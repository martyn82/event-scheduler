package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.util.Timeout
import com.github.martyn82.eventscheduler.grpc.{CancelEventRequest, CancelEventResponse, EventScheduler, EventSchedulerHandler, RescheduleEventRequest, RescheduleEventResponse, ScheduleEventRequest, ScheduleEventResponse, ScheduleToken}
import io.grpc.Status
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class EventSchedulerServer(interface: String, port: Int, sharding: ClusterSharding)(implicit system: ActorSystem[_]) {
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
            in.event,
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
