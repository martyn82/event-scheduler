package com.github.martyn82.eventscheduler

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

object SchedulerSpec {
  val config: Config = ConfigFactory.parseString(
    s"""
     | akka.actor.serializers.jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
     | akka.actor.serialization-bindings {
     |  "com.github.martyn82.eventscheduler.Serializable" = jackson-json
     | }
     |""".stripMargin)
}

class SchedulerSpec extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config.withFallback(SchedulerSpec.config))
  with AnyWordSpecLike
  with BeforeAndAfterEach {

  private val identifier = "1"

  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[Scheduler.Command, Scheduler.Event, Scheduler.State](
    system,
    Scheduler(identifier, Scheduler.Tags(0))
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "Scheduler" must {
    "schedule an event" in {
      val result = eventSourcedTestKit.runCommand(Scheduler.Schedule("foo", 1.hour.toMillis, _))
      result.event shouldBe an[Scheduler.Scheduled]
      result.stateOfType[Scheduler.State.Scheduled].token shouldNot be(empty)
    }

    "cancel a scheduled event" in {
      val token = eventSourcedTestKit.runCommand[StatusReply[Scheduler.Token]](Scheduler.Schedule("foo", 1.hour.toMillis, _))
        .reply.getValue

      val result = eventSourcedTestKit.runCommand(Scheduler.Cancel(token, _))
      result.event shouldBe an[Scheduler.Canceled]
      result.stateOfType[Scheduler.State.Canceled].token shouldBe token
    }

    "reschedule a scheduled event" in {
      val token = eventSourcedTestKit.runCommand[StatusReply[Scheduler.Token]](Scheduler.Schedule("foo", 1.hour.toMillis, _))
        .reply.getValue

      val result = eventSourcedTestKit.runCommand(Scheduler.Reschedule(token, 1.hour.toMillis, _))
      result.event shouldBe an[Scheduler.Rescheduled]
      result.stateOfType[Scheduler.State.Scheduled].token shouldBe token
    }

    "expire a scheduled event" in {
      val token = eventSourcedTestKit.runCommand[StatusReply[Scheduler.Token]](Scheduler.Schedule("foo", 1.hour.toMillis, _))
        .reply.getValue

      val result = eventSourcedTestKit.runCommand(Scheduler.Expire(token, _))
      result.event shouldBe an[Scheduler.Expired]
      result.stateOfType[Scheduler.State.Expired].token shouldBe token
    }

    "not be able to Schedule an already scheduled event" in {
      eventSourcedTestKit.runCommand(Scheduler.Schedule("foo", 1.hour.toMillis, _))

      val result = eventSourcedTestKit.runCommand(Scheduler.Schedule("foo", 1.hour.toMillis, _))
      result.reply.isError shouldBe true
    }

    "not be able to Reschedule an unscheduled event" in {
      val result = eventSourcedTestKit.runCommand(Scheduler.Reschedule("foo", 1.hour.toMillis, _))
      result.reply.isError shouldBe true
    }

    "not be able to Cancel an unscheduled event" in {
      val result = eventSourcedTestKit.runCommand(Scheduler.Cancel("token", _))
      result.reply.isError shouldBe true
    }

    "not be able to Expire an unscheduled event" in {
      val result = eventSourcedTestKit.runCommand(Scheduler.Expire("foo", _))
      result.reply.isError shouldBe true
    }
  }
}
