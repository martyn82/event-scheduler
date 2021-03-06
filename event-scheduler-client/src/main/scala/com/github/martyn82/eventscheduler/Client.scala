package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import com.github.martyn82.eventscheduler.v1.{CancelEventRequest, DefaultEventPublisherServiceClient, DefaultEventSchedulerServiceClient, Event, MetaDataValue, NewAccountEmailVerificationDeadlinePassed, ScheduleEventRequest, SubscribeRequest}
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

  val publisher = new DefaultEventPublisherServiceClient(
    GrpcClientSettings
      .connectToServiceAt("localhost", 50051)
      .withTls(false)
      .withConnectionAttempts(10)
  )
  val scheduler = new DefaultEventSchedulerServiceClient(
    GrpcClientSettings
      .connectToServiceAt("localhost", 50051)
      .withTls(false)
      .withConnectionAttempts(10)
  )

  publisher.subscribe().addHeader("token", "XYZ").invoke(
    SubscribeRequest.of(
      SubscribeRequest.Offset.Timestamp(
        Timestamp.of(
          Instant.now().getEpochSecond,
          0
        )
      )
    ))
    .map { event =>
      event.getEvent.getPayload match {
        case _ if event.getEvent.getPayload.is(NewAccountEmailVerificationDeadlinePassed) => {
          println("RECEIVED NewAccountEmailVerificationDeadlinePassed")

          val deadline = event.getEvent.getPayload.unpack(NewAccountEmailVerificationDeadlinePassed)
          println(deadline)

          println(event)
        }

        case _ =>
          println("UNKNOWN PAYLOAD")
      }
    }
    .run()

  scheduler.scheduleEvent().addHeader("token", "XYZ").invoke(
    ScheduleEventRequest.of(Some(event), Some(Timestamp.of(Instant.now().getEpochSecond, 0)))
  ).onComplete {
    case Success(token) =>
      scheduler.cancelEvent(
        CancelEventRequest.of(
          token.token
        )
      ).onComplete {
        case Success(response) => ()
        case Failure(exception) => println(exception.getMessage)
      }
    case Failure(exception) => println(exception.getMessage)
  }
}
