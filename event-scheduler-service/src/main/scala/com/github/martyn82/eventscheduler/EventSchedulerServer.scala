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
import com.github.martyn82.eventscheduler.grpc.{CancelEventRequest, CancelEventResponse, Event, EventScheduler, EventSchedulerHandler, RescheduleEventRequest, RescheduleEventResponse, ScheduleEventRequest, ScheduleEventResponse, ScheduleToken, SubscribeRequest}
import com.google.protobuf.ByteString
import com.google.protobuf.any.{Any => GrpcAny}
import io.grpc.Status
import org.slf4j.{Logger, LoggerFactory}

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
        EventSchedulerHandler.partial(new EventSchedulerService(sharding, tokenGenerator))
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

  class EventSchedulerService(sharding: ClusterSharding, generateToken: TokenGenerator) extends EventScheduler {
    override def scheduleEvent(in: ScheduleEventRequest): Future[ScheduleEventResponse] = {
      val reply = sharding.entityRefFor(Scheduler.EntityKey, generateToken())
        .askWithStatus(
          Scheduler.Schedule(
            in.event.map(GrpcAny.pack(_)).map(a => EventData(a.typeUrl, a.value.toByteArray)).get,
            in.at.get.seconds,
            _
          )
        )

      convertError(
        reply.map { token =>
          ScheduleEventResponse.of(
            Some(ScheduleToken.of(token))
          )
        }
      )
    }

    override def rescheduleEvent(in: RescheduleEventRequest): Future[RescheduleEventResponse] = {
      val reply = sharding.entityRefFor(Scheduler.EntityKey, in.token.get.token)
        .askWithStatus(
          Scheduler.Reschedule(
            in.token.get.token,
            in.at.get.seconds,
            _
          )
        )

      convertError(
        reply.map { token =>
          RescheduleEventResponse.of(
            Some(ScheduleToken.of(token))
          )
        }
      )
    }

    override def cancelEvent(in: CancelEventRequest): Future[CancelEventResponse] = {
      val reply = sharding.entityRefFor(Scheduler.EntityKey, in.token.get.token)
        .askWithStatus(
          Scheduler.Cancel(
            in.token.get.token,
            _
          )
        )

      convertError(
        reply.map(_ => CancelEventResponse.of())
      )
    }

    override def subscribe(in: SubscribeRequest): Source[Event, NotUsed] = {
      val sourceProvider: SourceProvider[Offset, EventEnvelope[Scheduler.Event]] =
        EventSourcedProvider.eventsByTag[Scheduler.Event](
          system = system,
          readJournalPluginId = JdbcReadJournal.Identifier,
          tag = Scheduler.Tags(0)
        )

      Await.result(
        sourceProvider.source { () =>
          Future.successful(
            Some(Offset.sequence(0))
          )
        },
        10 seconds
      )
    }.map { envelope =>
      repo.get(envelope.event.token)
        .map { schedule =>
          GrpcAny.of(schedule.eventType, ByteString.copyFrom(schedule.eventData))
        }
        .map(_.unpack(Event))
    }.filter(_.nonEmpty).map(_.get)

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
