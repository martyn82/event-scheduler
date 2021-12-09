package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import java.time.Instant
import java.util.UUID
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object Main extends App {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val sys: ActorSystem[_] = ActorSystem[Nothing](Behaviors.empty, "EventScheduler", ConfigFactory.load())
  val dbConfig: DatabaseConfig[PostgresProfile] = DatabaseConfig.forConfig("akka.projection.slick", sys.settings.config)

  SchemaUtils.createIfNotExists()(sys)

  AkkaManagement(sys).start()
  ClusterBootstrap(sys).start()

  val sharding = Scheduler.init(sys)
  SchedulingProjection.init(sys, sharding, sys.scheduler, dbConfig)(sys.executionContext)

  val token1 = UUID.randomUUID().toString
  val reply1 = sharding.entityRefFor(Scheduler.EntityKey, token1)
    .askWithStatus(Scheduler.Schedule("foo", Instant.now().getEpochSecond + 50, _))(Timeout(10 seconds))

  reply1.onComplete {
    case Success(token) =>
      logger.info(s"Scheduled event: $token")

    case Failure(e) =>
      logger.error(s"Failed to schedule event: ${e.getMessage}", e)
  }(sys.executionContext)

  val token2 = UUID.randomUUID().toString
  val reply2 = sharding.entityRefFor(Scheduler.EntityKey, token2)
    .askWithStatus(Scheduler.Schedule("foo", Instant.now().getEpochSecond + 5, _))(Timeout(10 seconds))

  reply2.onComplete {
    case Success(token) =>
      logger.info(s"Scheduled event: $token")

    case Failure(e) =>
      logger.error(s"Failed to schedule event: ${e.getMessage}", e)
  }(sys.executionContext)

  val reply3 = sharding.entityRefFor(Scheduler.EntityKey, token1)
    .askWithStatus(Scheduler.Cancel(token1, _))(Timeout(10 seconds))

  reply3.onComplete {
    case Success(token) =>
      logger.info(s"Canceled event: $token")

    case Failure(e) =>
      logger.error(s"Failed to schedule event: ${e.getMessage}", e)
  }(sys.executionContext)
}
