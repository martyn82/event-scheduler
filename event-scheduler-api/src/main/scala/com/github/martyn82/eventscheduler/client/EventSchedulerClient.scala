package com.github.martyn82.eventscheduler.client

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.Source
import com.github.martyn82.eventscheduler.client.EventSchedulerClient.ScheduleToken
import com.github.martyn82.eventscheduler.grpc.{CancelEventRequest, DefaultEventSchedulerClient, Event, RescheduleEventRequest, ScheduleEventRequest, SubscribeRequest}

import scala.concurrent.{ExecutionContext, Future}

object EventSchedulerClient {
  type ScheduleToken = String
}

class EventSchedulerClient(host: String, port: Int)(implicit system: ActorSystem[_]) {
  private implicit val ec: ExecutionContext = system.executionContext

  private val settings = GrpcClientSettings
    .connectToServiceAt(host, port)
    .withTls(false)

  private val client = DefaultEventSchedulerClient(settings)

  def scheduleEvent(request: ScheduleEventRequest): Future[ScheduleToken] =
    client.scheduleEvent(request).map(_.token.get.token)

  def rescheduleEvent(request: RescheduleEventRequest): Future[ScheduleToken] =
    client.rescheduleEvent(request).map(_.token.get.token)

  def cancelEvent(request: CancelEventRequest): Future[Unit] =
    client.cancelEvent(request).map(_ => ())

  def subscribe(request: SubscribeRequest): Source[Event, NotUsed] =
    client.subscribe(request)
}
