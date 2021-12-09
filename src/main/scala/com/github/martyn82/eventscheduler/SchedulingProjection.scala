package com.github.martyn82.eventscheduler

import akka.actor.typed.{ActorSystem, Scheduler}
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.ExactlyOnceProjection
import akka.projection.slick.SlickProjection
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object SchedulingProjection {
  val Name = "SchedulingProjection"

  def init(system: ActorSystem[_], sharding: ClusterSharding, scheduler: Scheduler, dbConfig: DatabaseConfig[PostgresProfile])
          (implicit ec: ExecutionContext): Unit = {
    ShardedDaemonProcess(system).init(
      name = Name,
      Scheduler.Tags.size,
      index => ProjectionBehavior(createProjectionFor(system, dbConfig, sharding, scheduler, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop)
    )
  }

  private def createProjectionFor(system: ActorSystem[_], dbConfig: DatabaseConfig[PostgresProfile], sharding: ClusterSharding, scheduler: Scheduler, index: Int)
                                 (implicit ec: ExecutionContext): ExactlyOnceProjection[Offset, EventEnvelope[Scheduler.Event]] = {
    val tag = Scheduler.Tags(index)

    val sourceProvider = EventSourcedProvider.eventsByTag[Scheduler.Event](
      system              = system,
      readJournalPluginId = JdbcReadJournal.Identifier,
      tag                 = tag
    )

    val handler = new SchedulingProjectionHandler(scheduler, sharding)

    SlickProjection.exactlyOnce(
      projectionId    = ProjectionId(Name, tag),
      sourceProvider  = sourceProvider,
      handler         = () => handler,
      databaseConfig  = dbConfig
    )(ClassTag(PostgresProfile.getClass), system)
  }
}
