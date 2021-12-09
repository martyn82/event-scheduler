package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main extends App {
  val sys: ActorSystem[_] = ActorSystem[Nothing](Behaviors.empty, "EventScheduler", ConfigFactory.load())
  val dbConfig: DatabaseConfig[PostgresProfile] = DatabaseConfig.forConfig("akka.projection.slick", sys.settings.config)

  SchemaUtils.createIfNotExists()(sys)

  AkkaManagement(sys).start()
  ClusterBootstrap(sys).start()

  val sharding = Scheduler.init(sys)
  SchedulingProjection.init(sys, sharding, sys.scheduler, dbConfig)(sys.executionContext)

  val token1 = UUID.randomUUID().toString
  val reply1 = sharding.entityRefFor(Scheduler.EntityKey, token1)
    .askWithStatus(Scheduler.Schedule("foo", Instant.now().getEpochSecond + 50000, _))(Timeout(10 seconds))

  reply1.onComplete {
    case Success(token) =>
      println(s"Scheduled event: $token")

    case Failure(e) =>
      println(s"Failed to schedule event: ${e.getMessage}")
  }(sys.executionContext)

  val token2 = UUID.randomUUID().toString
  val reply2 = sharding.entityRefFor(Scheduler.EntityKey, token2)
    .askWithStatus(Scheduler.Schedule("foo", Instant.now().getEpochSecond + 5000, _))(Timeout(10 seconds))

  reply2.onComplete {
    case Success(token) =>
      println(s"Scheduled event: $token")

    case Failure(e) =>
      println(s"Failed to schedule event: ${e.getMessage}")
  }(sys.executionContext)

  val reply3 = sharding.entityRefFor(Scheduler.EntityKey, token1)
    .askWithStatus(Scheduler.Cancel(token1, _))(Timeout(10 seconds))

  reply3.onComplete {
    case Success(token) =>
      println(s"Canceled event: $token")

    case Failure(e) =>
      println(s"Failed to schedule event: ${e.getMessage}")
  }(sys.executionContext)
}
