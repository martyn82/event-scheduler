package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{AtLeastOnceProjection, SourceProvider}

object PublishingProjection {
  val Name = "PublishingProjection"

  def init(system: ActorSystem[_], repo: SchedulingRepository): Unit = {
    ShardedDaemonProcess(system).init(
      name = Name,
      Scheduler.Tags.size,
      index => ProjectionBehavior(createProjectionFor(system, repo, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop)
    )
  }

  private def createProjectionFor(system: ActorSystem[_], repo: SchedulingRepository, index: Int): AtLeastOnceProjection[Offset, EventEnvelope[Scheduler.Event]] = {
    val tag = Scheduler.Tags(index)
    val sourceProvider: SourceProvider[Offset, EventEnvelope[Scheduler.Event]] =
      EventSourcedProvider.eventsByTag[Scheduler.Event](
        system              = system,
        readJournalPluginId = JdbcReadJournal.Identifier,
        tag                 = tag
      )

    JdbcProjection.atLeastOnceAsync(
      projectionId = ProjectionId(Name, tag),
      sourceProvider,
      handler = () => new PublishingProjectionHandler(repo),
      sessionFactory = () => new ScalikeJdbcSession
    )(system)
  }
}

trait PublishingProjection {
}
