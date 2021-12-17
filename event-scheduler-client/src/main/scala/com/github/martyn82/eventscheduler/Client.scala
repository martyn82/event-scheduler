package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.github.martyn82.eventscheduler.client.EventSchedulerClient
import com.github.martyn82.eventscheduler.grpc.{Event, MetaDataValue, NewAccountEmailVerificationDeadlinePassed, ScheduleEventRequest, SubscribeRequest}
import com.google.protobuf.any.Any
import com.google.protobuf.timestamp.Timestamp

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object Client extends App {
  implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "EventSchedulerClient")
  implicit val ec: ExecutionContext = system.executionContext

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

  val client = new EventSchedulerClient("localhost", 50051)

  client.subscribe(
    SubscribeRequest.of(
      SubscribeRequest.Offset.Timestamp(
        Timestamp.of(
          Instant.now().getEpochSecond,
          0
        )
      )
    ))
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

  client.scheduleEvent(
    ScheduleEventRequest.of(Some(event), Some(Timestamp.of(Instant.now().getEpochSecond, 0)))
  ).onComplete {
    case Success(token) => println(token)
    case Failure(exception) => println(exception.getMessage)
  }
}
