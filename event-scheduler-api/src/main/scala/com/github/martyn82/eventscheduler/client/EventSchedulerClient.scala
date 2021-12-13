package com.github.martyn82.eventscheduler.client

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.Source
import com.github.martyn82.eventscheduler.client.EventSchedulerClient.{ScheduleToken, Timestamp}
import com.github.martyn82.eventscheduler.grpc.{CancelEventRequest, DefaultEventSchedulerClient, Event, MetaDataValue, RescheduleEventRequest, ScheduleEventRequest, SubscribeRequest, ScheduleToken => GrpcScheduleToken}
import com.google.protobuf.timestamp.{Timestamp => GrpcTimestamp}

import scala.concurrent.{ExecutionContext, Future}

object EventSchedulerClient {
  type Timestamp = Long
  type ScheduleToken = String
}

class EventSchedulerClient(host: String, port: Int, useTls: Boolean)(implicit system: ActorSystem[_]) {
  private implicit val ec: ExecutionContext = system.executionContext

  private val settings = GrpcClientSettings
    .connectToServiceAt(host, port)
    .withTls(useTls)

  private val client = DefaultEventSchedulerClient(settings)

  def scheduleEvent(event: Event, at: Timestamp): Future[ScheduleToken] =
    client.scheduleEvent(
      ScheduleEventRequest.of(
        Some(event),
        Some(GrpcTimestamp.of(at, 0))
      )
    ).map(_.token.get.token)

  def rescheduleEvent(token: ScheduleToken, event: String, at: Timestamp): Future[ScheduleToken] =
    client.rescheduleEvent(
      RescheduleEventRequest.of(
        Some(GrpcScheduleToken.of(token)),
        Some(GrpcTimestamp.of(at, 0))
      )
    ).map(_.token.get.token)

  def cancelEvent(token: ScheduleToken): Future[Unit] =
    client.cancelEvent(
      CancelEventRequest.of(
        Some(GrpcScheduleToken.of(token))
      )
    ).map(_ => ())

  def subscribe: Source[Event, NotUsed] =
    client.subscribe(SubscribeRequest.of())
}
