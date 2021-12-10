package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.github.martyn82.eventscheduler.client.EventSchedulerClient
import com.github.martyn82.eventscheduler.client.EventSchedulerClient.EventEnvelope

import java.time.Instant
import scala.concurrent.ExecutionContext

object Client extends App {
  implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "EventSchedulerClient")
  implicit val ec: ExecutionContext = system.executionContext

  val client = new EventSchedulerClient("localhost", 50051, false)
  val event = EventEnvelope(
    "foobar",
    "barfoor",
    "bartype",
    0L,
    Instant.now(),
    "1.0",
    "blabla",
    Map.empty[String, Any]
  )

  for {
    token <- client.scheduleEvent(event, Instant.now().getEpochSecond + 5)
//    _ <- client.cancelEvent(token)
  } yield {
    println(token)
  }
}
