package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc.{AutoSession, DBSession, DBSessionWrapper}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

object Server extends App {
  val config = ConfigFactory.load()
  implicit val sys: ActorSystem[_] = ActorSystem[Nothing](Behaviors.empty, "EventScheduler", config)
  implicit val session: DBSession = AutoSession

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val dbConfig: DatabaseConfig[PostgresProfile] = DatabaseConfig.forConfig("akka.projection.slick", sys.settings.config)

  SchemaUtils.createIfNotExists()(sys)

  AkkaManagement(sys).start()
  ClusterBootstrap(sys).start()

  val sharding = Scheduler.init(sys)
  val repo = new JdbcSchedulingRepository()
  repo.init()

  SchedulingProjection.init(sys, sharding, sys.scheduler, repo)(sys.executionContext)

  val grpcConfig = config.getConfig("grpc.server")
  val server = new EventSchedulerServer(grpcConfig.getString("host"), grpcConfig.getInt("port"), sharding)
  server.start()
}
