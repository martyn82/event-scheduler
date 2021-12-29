package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc.config.DBs
import scalikejdbc.{AutoSession, DBSession}

object Server extends App {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val config = ConfigFactory.load()

  implicit val sys: ActorSystem[_] = ActorSystem[Nothing](Behaviors.empty, "EventScheduler", config)
  implicit val session: DBSession = AutoSession

  DBs.setupAll()
  ScalikeJdbcSession.createSchema()

  AkkaManagement(sys).start()
  ClusterBootstrap(sys).start()

  val sharding = Scheduler.init(sys)
  val repo = new JdbcSchedulingRepository()
  repo.init()

  SchedulingProjection.init(sys, sharding, sys.scheduler, repo)(sys.executionContext)
  PublishingProjection.init(sys, repo)

  val grpcConfig = config.getConfig("grpc.server")
  val server = new EventSchedulerServer(grpcConfig.getString("host"), grpcConfig.getInt("port"), sharding, repo)
  server.start()
}
