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
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.ExactlyOnceProjection

import scala.concurrent.ExecutionContext

object SchedulingProjection {
  val Name = "SchedulingProjection"

  def init(system: ActorSystem[_],
           sharding: ClusterSharding,
           scheduler: Scheduler,
           repo: SchedulingRepository)
          (implicit ec: ExecutionContext): Unit = {
    ShardedDaemonProcess(system).init(
      name = Name,
      Scheduler.Tags.size,
      index => ProjectionBehavior(createProjectionFor(system, sharding, scheduler, repo, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop)
    )
  }

  private def createProjectionFor(system: ActorSystem[_],
                                  sharding: ClusterSharding,
                                  scheduler: Scheduler,
                                  repo: SchedulingRepository,
                                  index: Int)
                                 (implicit ec: ExecutionContext): ExactlyOnceProjection[Offset, EventEnvelope[Scheduler.Event]] = {
    val tag = Scheduler.Tags(index)

    val sourceProvider = EventSourcedProvider.eventsByTag[Scheduler.Event](
      system              = system,
      readJournalPluginId = JdbcReadJournal.Identifier,
      tag                 = tag
    )

    val handler = new SchedulingProjectionHandler(scheduler, sharding, repo)
    handler.init()

    JdbcProjection.exactlyOnce(
      projectionId    = ProjectionId(Name, tag),
      sourceProvider  = sourceProvider,
      handler         = () => handler,
      sessionFactory = () => new ScalikeJdbcSession()
    )(system)
  }
}
