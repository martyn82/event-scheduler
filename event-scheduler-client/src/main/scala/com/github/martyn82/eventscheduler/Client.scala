package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.github.martyn82.eventscheduler.client.EventSchedulerClient
import com.github.martyn82.eventscheduler.grpc.{Event, MetaDataValue, NewAccountEmailVerificationDeadlinePassed, SubscribeRequest}
import com.google.protobuf.any.Any
import com.google.protobuf.timestamp.Timestamp

import java.time.Instant
import scala.concurrent.ExecutionContext

object Client extends App {
  implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "EventSchedulerClient")
  implicit val ec: ExecutionContext = system.executionContext

  val client = new EventSchedulerClient("localhost", 50051, false)
  val deadline = NewAccountEmailVerificationDeadlinePassed.of("account-1")
  val event = Event.of(
    messageIdentifier =  "msg-id-1",
    aggregateIdentifier =  "aggregate-id-1",
    aggregateSequenceNumber =  0L,
    aggregateType =  "aggregate-type",
    Some(Timestamp.of(Instant.now().getEpochSecond, 0)),
    payloadRevision =  "1.0",
    Some(Any.pack(deadline)),
    Map.empty[String, MetaDataValue],
    snapshot = false
  )

  client.subscribe
    .map { event =>
      event.payload.get match {
        case _ if event.payload.get.is(NewAccountEmailVerificationDeadlinePassed) => {
          println("RECEIVED NewAccountEmailVerificationDeadlinePassed")

          val deadline = event.payload.get.unpack(NewAccountEmailVerificationDeadlinePassed)
          println(deadline)

          println(event)
        }

        case _ =>
          println("UNKNOWN PAYLOAD")
      }
    }
    .run()

  for {
    token <- client.scheduleEvent(event, Instant.now().getEpochSecond + 5)
  } yield {
    println(token)
  }
}
