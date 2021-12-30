package com.github.martyn82.eventscheduler

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.SourceProvider
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.github.martyn82.eventscheduler.Scheduler.EventData
import com.github.martyn82.eventscheduler.v1.{CancelEventRequest, CancelEventRequestValidator, CancelEventResponse, Event, EventSchedulerService, EventSchedulerServiceHandler, RescheduleEventRequest, RescheduleEventRequestValidator, RescheduleEventResponse, ScheduleEventRequest, ScheduleEventRequestValidator, ScheduleEventResponse, ScheduleToken, SubscribeRequest, SubscribeRequestValidator, SubscribeResponse}
import com.google.protobuf.ByteString
import com.google.protobuf.any.{Any => GrpcAny}
import io.grpc.Status
import org.slf4j.{Logger, LoggerFactory}
import scalapb.validate

import java.util.UUID
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class EventSchedulerServer(interface: String, port: Int, sharding: ClusterSharding, repo: SchedulingRepository)(implicit system: ActorSystem[_]) {
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val timeout: Timeout = Timeout(30 seconds)

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private def tokenGenerator: TokenGenerator = () =>
    UUID.randomUUID().toString

  def start(): Future[Http.ServerBinding] = {
    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(
        EventSchedulerServiceHandler.partial(new EventScheduler(sharding, tokenGenerator))
      )

    val bound: Future[Http.ServerBinding] = Http(system)
      .newServerAt(interface, port)
      .bind(service)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10 seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        logger.info("gRPC server running at {}:{}", address.getHostName, address.getPort)

      case Failure(e) =>
        logger.error("Failed to bind gRPC endpoint, terminating", e)
        system.terminate()
    }
    bound
  }

  class EventScheduler(sharding: ClusterSharding, generateToken: TokenGenerator) extends EventSchedulerService {
    override def scheduleEvent(in: ScheduleEventRequest): Future[ScheduleEventResponse] = {
      val reply = ScheduleEventRequestValidator.validate(in) match {
        case validate.Success =>
          sharding.entityRefFor(Scheduler.EntityKey, generateToken())
            .askWithStatus(
              Scheduler.Schedule(
                in.event.map(GrpcAny.pack(_)).map(a => EventData(a.typeUrl, a.value.toByteArray)).get,
                in.at.get.seconds,
                _
              )
            )
        case validate.Failure(errors) =>
          Future.failed(
            new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(errors.head.toString()))
          )
      }

      convertError(
        reply.map { token =>
          ScheduleEventResponse.of(
            Some(ScheduleToken.of(token))
          )
        }
      )
    }

    override def rescheduleEvent(in: RescheduleEventRequest): Future[RescheduleEventResponse] = {
      val reply = RescheduleEventRequestValidator.validate(in) match {
        case validate.Success =>
          sharding.entityRefFor(Scheduler.EntityKey, in.token.get.token)
            .askWithStatus(
              Scheduler.Reschedule(
                in.token.get.token,
                in.at.get.seconds,
                _
              )
            )
        case validate.Failure(errors) =>
          Future.failed(
            new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(errors.head.toString()))
          )
      }

      convertError(
        reply.map { token =>
          RescheduleEventResponse.of(
            Some(ScheduleToken.of(token))
          )
        }
      )
    }

    override def cancelEvent(in: CancelEventRequest): Future[CancelEventResponse] = {
      val reply: Future[_] = CancelEventRequestValidator.validate(in) match {
        case validate.Success =>
          sharding.entityRefFor(Scheduler.EntityKey, in.token.get.token)
            .askWithStatus(
              Scheduler.Cancel(
                in.token.get.token,
                _
              )
            )
        case validate.Failure(errors) =>
          Future.failed(
            new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(errors.head.toString()))
          )
      }

      convertError(
        reply.map(_ => CancelEventResponse.of())
      )
    }

    override def subscribe(in: SubscribeRequest): Source[SubscribeResponse, NotUsed] = {
      SubscribeRequestValidator.validate(in) match {
        case validate.Success =>
          val sourceProvider: SourceProvider[Offset, EventEnvelope[Scheduler.Event]] =
            EventSourcedProvider.eventsByTag[Scheduler.Event](
              system = system,
              readJournalPluginId = JdbcReadJournal.Identifier,
              tag = Scheduler.Tags(0)
            )

          Await.result(
            sourceProvider.source { () =>
              Future.successful(
                Some(Offset.sequence(in.offset.sequenceNumber.getOrElse(0)))
              )
            },
            1 second
          ).filter { envelope =>
            in.offset.timestamp.map(_.seconds).getOrElse(0L) < (envelope.timestamp / 1000).longValue
          }.map { envelope =>
            repo.get(envelope.event.token)
              .map { schedule =>
                GrpcAny.of(schedule.eventType, ByteString.copyFrom(schedule.eventData))
              }
              .map(_.unpack(Event))
          }.filter(_.nonEmpty)
            .map(SubscribeResponse.of)

        case validate.Failure(errors) =>
          Source.failed(
            new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(errors.head.toString()))
          )
      }
    }

    private def convertError[T](response: Future[T]): Future[T] =
      response.recoverWith {
        case _: TimeoutException =>
          Future.failed(
            new GrpcServiceException(
              Status.UNAVAILABLE
                .withDescription("Operation timed out")
            )
          )

        case ex =>
          Future.failed(
            new GrpcServiceException(
              Status.INVALID_ARGUMENT
                .withDescription(ex.getMessage)
            )
          )
      }
  }
}
