package com.github.martyn82.eventscheduler

import akka.Done
import akka.actor.Cancellable
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.Scheduler
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import akka.projection.testkit.scaladsl.{ProjectionTestKit, TestProjection, TestSourceProvider}
import akka.stream.scaladsl.Source
import com.github.martyn82.eventscheduler.SchedulingProjectionHandlerSpec.{FakeCancellable, FakeProjectionHandler}
import com.typesafe.config.{Config, ConfigFactory}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import slick.dbio.{FailureAction, SuccessAction}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object SchedulingProjectionHandlerSpec {
  val config: Config = ConfigFactory.parseString(
    s"""
     | akka.actor.provider = cluster
     |""".stripMargin
  )

  class FakeCancellable extends Cancellable {
    override def cancel(): Boolean = true
    override def isCancelled: Boolean = false
  }

  class FakeProjectionHandler(inner: SchedulingProjectionHandler)(implicit ec: ExecutionContext) extends Handler[EventEnvelope[Scheduler.Event]] {
    override def process(envelope: EventEnvelope[Scheduler.Event]): Future[Done] =
      inner.process(envelope) match {
        case SuccessAction(v) => Future.successful(v)
        case FailureAction(e) => Future.failed(e)
      }
  }
}

class SchedulingProjectionHandlerSpec extends ScalaTestWithActorTestKit(SchedulingProjectionHandlerSpec.config)
  with AnyWordSpecLike
  with BeforeAndAfterEach {

  private implicit val ec: ExecutionContext = system.executionContext

  private val projectionTestKit = ProjectionTestKit(system)

  private val sharding = ClusterSharding(system)
  private val scheduler = mock(system.scheduler.getClass)
  private val handler = new FakeProjectionHandler(new SchedulingProjectionHandler(scheduler, sharding))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(scheduler)
  }

  "SchedulingProjectionHandler" should {
    "schedule expiry on event scheduled" in {
      val event = Scheduler.Scheduled("token", "foo", Instant.now().getEpochSecond)

      val sourceProvider = TestSourceProvider(
        sourceEvents = Source(
          EventEnvelope[Scheduler.Event](Offset.sequence(0), "1", 1, event, Instant.now().getEpochSecond) ::
            Nil
        ),
        extractOffset = (envelope: EventEnvelope[_]) => envelope.offset
      )

      val projection = TestProjection(
        projectionId    = ProjectionId("test", "00"),
        sourceProvider  = sourceProvider,
        handler         = () => handler
      )

      projectionTestKit.run(projection) {
        val in = FiniteDuration(Instant.now().getEpochSecond - event.at, SECONDS)
        verify[Scheduler](scheduler, atMostOnce()).scheduleOnce(ArgumentMatchers.eq(in), any[Runnable])(any[ExecutionContext])
      }
    }

    "cancel scheduled event on event canceled" in {
      val scheduled = Scheduler.Scheduled("token", "foo", Instant.now().getEpochSecond + 5000)
      val canceled = Scheduler.Canceled("token")

      val sourceProvider = TestSourceProvider(
        sourceEvents = Source(
          EventEnvelope[Scheduler.Event](Offset.sequence(0), "1", 1, scheduled, Instant.now().getEpochSecond) ::
            EventEnvelope[Scheduler.Event](Offset.sequence(1), "1", 2, canceled, Instant.now().getEpochSecond)  ::
            Nil
        ),
        extractOffset = (envelope: EventEnvelope[_]) => envelope.offset
      )

      val projection = TestProjection(
        projectionId = ProjectionId("test", "00"),
        sourceProvider = sourceProvider,
        handler = () => handler
      )

      val cancellable: Cancellable = spy(new FakeCancellable)
      when(scheduler.scheduleOnce(any[FiniteDuration], any[Runnable])(any[ExecutionContext]))
        .thenReturn(cancellable)

      projectionTestKit.run(projection) {
        verify(cancellable, atMostOnce()).cancel()
      }
    }

    "cancel scheduled event on expiry" in {
      val scheduled = Scheduler.Scheduled("token", "foo", Instant.now().getEpochSecond + 1)

      val sourceProvider = TestSourceProvider(
        sourceEvents = Source(
          EventEnvelope[Scheduler.Event](Offset.sequence(0), "1", 1, scheduled, Instant.now().getEpochSecond) ::
            Nil
        ),
        extractOffset = (envelope: EventEnvelope[_]) => envelope.offset
      )

      val projection = TestProjection(
        projectionId = ProjectionId("test", "00"),
        sourceProvider = sourceProvider,
        handler = () => handler
      )

      val cancellable: Cancellable = spy(new FakeCancellable)
      when(scheduler.scheduleOnce(any[FiniteDuration], any[Runnable])(any[ExecutionContext]))
        .thenReturn(cancellable)

      projectionTestKit.run(projection) {
        verify(cancellable, atMostOnce()).cancel()
      }
    }
  }
}
