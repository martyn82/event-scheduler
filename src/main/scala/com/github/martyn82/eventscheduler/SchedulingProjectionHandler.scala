package com.github.martyn82.eventscheduler

import akka.Done
import akka.actor.Cancellable
import akka.actor.typed.Scheduler
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import akka.util.Timeout

import slick.dbio.DBIO

import scala.collection._
import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class SchedulingProjectionHandler(scheduler: Scheduler, sharding: ClusterSharding)
                                 (implicit ec: ExecutionContext) extends SlickHandler[EventEnvelope[Scheduler.Event]] {
  private implicit val timeout: Timeout = Timeout(30 seconds)

  private val planned: mutable.Map[Scheduler.Token, Cancellable] = mutable.Map.empty

  override def process(envelope: EventEnvelope[Scheduler.Event]): DBIO[Done] = envelope.event match {
    case event: Scheduler.Scheduled   => onScheduled(event)
    case event: Scheduler.Rescheduled => onRescheduled(event)
    case event: Scheduler.Canceled    => onCanceled(event)
    case event: Scheduler.Expired     => onExpired(event)
  }

  private def onScheduled(event: Scheduler.Scheduled): DBIO[Done] = {
    val in = Duration((Instant.now().getEpochSecond - event.at), SECONDS)

    val cancellable = scheduler.scheduleOnce(in, () => {
      sharding.entityRefFor(Scheduler.EntityKey, event.token)
        .askWithStatus(Scheduler.Expire(event.token, _))
    })

    planned.put(event.token, cancellable)
    DBIO.successful(Done)
  }

  private def onRescheduled(event: Scheduler.Rescheduled): DBIO[Done] =
    onScheduled(Scheduler.Scheduled(event.token, event.event, event.at))

  private def onCanceled(event: Scheduler.Canceled): DBIO[Done] = {
    planned.remove(event.token).map(_.cancel())
    DBIO.successful(Done)
  }

  private def onExpired(event: Scheduler.Expired): DBIO[Done] = {
    planned.remove(event.token).map(_.cancel())
    DBIO.successful(Done)
  }
}
