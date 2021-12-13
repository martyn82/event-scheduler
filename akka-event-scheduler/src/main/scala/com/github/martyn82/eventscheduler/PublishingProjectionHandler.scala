package com.github.martyn82.eventscheduler

import akka.Done
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

class PublishingProjectionHandler(repo: SchedulingRepository) extends Handler[EventEnvelope[Scheduler.Event]] {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def process(envelope: EventEnvelope[Scheduler.Event]): Future[Done] = envelope.event match {
    case event: Scheduler.Expired => publish(event)
    case _ => Future.successful(Done) // ignored
  }

  private def publish(event: Scheduler.Expired): Future[Done] = {
    logger.info("PUBLISH EVENT ON EXPIRY TIME")
    repo.get(event.token).foreach { e =>
      logger.info(e.eventType + ": " + e.eventData)
    }
    Future.successful(Done)
  }
}
