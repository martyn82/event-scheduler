package com.github.martyn82.eventscheduler

import akka.Done
import akka.actor.Cancellable
import akka.actor.typed.Scheduler
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import akka.util.Timeout

import scala.collection._

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class SchedulingProjectionHandler(scheduler: Scheduler, sharding: ClusterSharding)(implicit ec: ExecutionContext) extends Handler[EventEnvelope[Scheduler.Event]] {
  private implicit val timeout: Timeout = Timeout(30 seconds)

  private val planned: mutable.Map[Scheduler.Token, Cancellable] = mutable.Map.empty

  override def process(envelope: EventEnvelope[Scheduler.Event]): Future[Done] = {
    envelope.event match {
      case event: Scheduler.Scheduled   => onScheduled(event)
      case event: Scheduler.Rescheduled => onRescheduled(event)
      case event: Scheduler.Canceled    => onCanceled(event)
      case event: Scheduler.Expired     => onExpired(event)
    }
  }

  private def onScheduled(event: Scheduler.Scheduled): Future[Done] = {
    val in = Duration(Instant.now().toEpochMilli - event.at, MILLISECONDS)

    val cancellable = scheduler.scheduleOnce(in, () => {
      sharding.entityRefFor(Scheduler.EntityKey, event.token)
        .askWithStatus(Scheduler.Expire(event.token, _))
    })

    planned.put(event.token, cancellable)
    Future.successful(Done)
  }

  private def onRescheduled(event: Scheduler.Rescheduled): Future[Done] =
    onScheduled(Scheduler.Scheduled(event.token, event.event, event.at))

  private def onCanceled(event: Scheduler.Canceled): Future[Done] = {
    planned.remove(event.token).map(_.cancel())
    Future.successful(Done)
  }

  private def onExpired(event: Scheduler.Expired): Future[Done] = {
    planned.remove(event.token).map(_.cancel())
    Future.successful(Done)
  }
}
