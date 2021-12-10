package com.github.martyn82.eventscheduler

import akka.Done
import akka.actor.Cancellable
import akka.actor.typed.Scheduler
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import akka.util.Timeout
import com.github.martyn82.eventscheduler.SchedulingProjection.ScalikeJdbcSession
import com.github.martyn82.eventscheduler.SchedulingRepository.{Schedule, Status}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection._
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class SchedulingProjectionHandler(scheduler: Scheduler, sharding: ClusterSharding, repo: SchedulingRepository)
                                 (implicit ec: ExecutionContext) extends JdbcHandler[EventEnvelope[Scheduler.Event], ScalikeJdbcSession] {
  private implicit val timeout: Timeout = Timeout(30 seconds)

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private val planned: mutable.Map[Scheduler.Token, Cancellable] = mutable.Map.empty

  override def process(session: ScalikeJdbcSession, envelope: EventEnvelope[Scheduler.Event]): Unit = envelope.event match {
    case event: Scheduler.Scheduled   => onScheduled(event)
    case event: Scheduler.Rescheduled => onRescheduled(event)
    case event: Scheduler.Canceled    => onCanceled(event)
    case event: Scheduler.Expired     => onExpired(event)
  }

  private def onScheduled(event: Scheduler.Scheduled): Future[Done] = {
    val in = Duration(Instant.now().getEpochSecond - event.at, SECONDS)

    val cancellable = scheduler.scheduleOnce(in, () => {
      sharding.entityRefFor(Scheduler.EntityKey, event.token)
        .askWithStatus(Scheduler.Expire(event.token, _))
    })

    repo.store(Schedule(event.token, event.event, event.at, Status.Scheduled))
    planned.put(event.token, cancellable)

    logger.info(s"Scheduled: ${event.token}")
    Future.successful(Done)
  }

  private def onRescheduled(event: Scheduler.Rescheduled): Future[Done] =
    onScheduled(Scheduler.Scheduled(event.token, event.event, event.at))

  private def onCanceled(event: Scheduler.Canceled): Future[Done] = {
    repo.update(event.token, Status.Canceled)
    planned.remove(event.token).map(_.cancel())

    logger.info(s"Canceled: ${event.token}")
    Future.successful(Done)
  }

  private def onExpired(event: Scheduler.Expired): Future[Done] = {
    repo.update(event.token, Status.Expired)
    planned.remove(event.token).map(_.cancel())

    logger.info(s"Expired: ${event.token}")
    Future.successful(Done)
  }
}
