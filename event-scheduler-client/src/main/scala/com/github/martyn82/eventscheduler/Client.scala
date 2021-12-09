package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.github.martyn82.eventscheduler.client.EventSchedulerClient

import java.time.Instant
import scala.concurrent.ExecutionContext

object Client extends App {
  implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "EventSchedulerClient")
  implicit val ec: ExecutionContext = system.executionContext

  val client = new EventSchedulerClient("localhost", 50051, false)

  for {
    token <- client.scheduleEvent("foo", Instant.now().getEpochSecond + 5)
    _ <- client.cancelEvent(token)
  } yield {
    println(token)
  }
}
