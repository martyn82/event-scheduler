package com.github.martyn82.eventscheduler.client

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import com.github.martyn82.eventscheduler.client.EventSchedulerClient.{EventEnvelope, ScheduleToken, Timestamp}
import com.github.martyn82.eventscheduler.grpc.{CancelEventRequest, DefaultEventSchedulerClient, Event, MetaDataValue, RescheduleEventRequest, ScheduleEventRequest, ScheduleToken => GrpcScheduleToken}
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.{Timestamp => GrpcTimestamp}
import com.google.protobuf.any.{Any => GrpcAny}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

object EventSchedulerClient {
  type Timestamp = Long
  type ScheduleToken = String

  final case class EventEnvelope(
    messageIdentifier: String,
    aggregateIdentifier: String,
    aggregateType: String,
    aggregateSequenceNumber: Long,
    timestamp: Instant,
    payloadRevision: String,
    payloadTypeUrl: String,
    payloadBytes: Array[Byte],
    metaData: Map[String, Any]
  )
}

class EventSchedulerClient(host: String, port: Int, useTls: Boolean)(implicit system: ActorSystem[_]) {
  private implicit val ec: ExecutionContext = system.executionContext

  private val settings = GrpcClientSettings
    .connectToServiceAt(host, port)
    .withTls(useTls)

  private val client = DefaultEventSchedulerClient(settings)

  def scheduleEvent(event: EventEnvelope, at: Timestamp): Future[ScheduleToken] =
    client.scheduleEvent(
      ScheduleEventRequest.of(
        Some(
          Event.of(
            event.messageIdentifier,
            event.aggregateIdentifier,
            event.aggregateSequenceNumber,
            event.aggregateType,
            Some(
              GrpcTimestamp.of(event.timestamp.getEpochSecond, 0)
            ),
            event.payloadRevision,
            Some(
              GrpcAny.of(
                event.payloadTypeUrl,
                ByteString.copyFrom(event.payloadBytes)
              )
            ),
            Map.empty[String, MetaDataValue],
            snapshot = false
          )
        ),
        Some(
          GrpcTimestamp.of(at, 0)
        )
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
}
