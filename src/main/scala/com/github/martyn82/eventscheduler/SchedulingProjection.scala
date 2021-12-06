package com.github.martyn82.eventscheduler

import akka.actor.typed.{ActorSystem, Scheduler}
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.query.Offset
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.AtLeastOnceProjection

object SchedulingProjection {
  def init(system: ActorSystem[_], scheduler: Scheduler): Unit = {
    ShardedDaemonProcess(system).init(
      name = "SchedulingProjection",
      Scheduler.Tags.size,
      index => ProjectionBehavior(createProjectionFor(system, scheduler, index))
    )
  }

  private def createProjectionFor(system: ActorSystem[_], scheduler: Scheduler, index: Int): AtLeastOnceProjection[Offset, EventEnvelope[Scheduler.Event]] = {
    val tag = Scheduler.Tags(index)

    val sourceProvider = EventSourcedProvider.eventsByTag[Scheduler.Event](
      system = system,
      readJournalPluginId = LeveldbReadJournal.Identifier,
      tag = tag
    )

    val handler = new SchedulingProjectionHandler(scheduler)

    CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("SchedulingProjection", tag),
      sourceProvider,
      handler = () => handler
    )
  }
}
