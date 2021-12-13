package com.github.martyn82.eventscheduler

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, ReplyEffect}

object Scheduler {
  type Identifier = String
  type Token = String
  type Timestamp = Long

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Scheduler")
  val Tags: Vector[String] = Vector.tabulate(1) { i => s"scheduler-$i" }

  final case class EventData(eventType: String, eventData: Array[Byte])

  sealed trait Command extends Serializable

  final case class Schedule(event: EventData, at: Timestamp, replyTo: ActorRef[StatusReply[Token]]) extends Command
  final case class Reschedule(token: Token, at: Timestamp, replyTo: ActorRef[StatusReply[Token]]) extends Command
  final case class Cancel(token: Token, replyTo: ActorRef[StatusReply[Done]]) extends Command
  final case class Expire(token: Token, replyTo: ActorRef[StatusReply[Done]]) extends Command

  sealed trait Event extends Serializable {
    val token: Token
  }

  final case class Scheduled(token: Token, event: EventData, at: Timestamp) extends Event
  final case class Canceled(token: Token) extends Event
  final case class Rescheduled(token: Token, event: EventData, at: Timestamp) extends Event
  final case class Expired(token: Token) extends Event

  sealed trait State extends Serializable

  object State {
    final case class Unscheduled(token: Token) extends State
    final case class Scheduled(token: Token, event: EventData, at: Timestamp) extends State
    final case class Canceled(token: Token) extends State
    final case class Expired(token: Token) extends State
  }

  def init(system: ActorSystem[_]): ClusterSharding = {
    val sharding = ClusterSharding(system)
    sharding.init(Entity(EntityKey) { entityContext =>
      Scheduler(entityContext.entityId, Tags(0))
    })
    sharding
  }

  def apply(id: Identifier, projectionTag: String): Behavior[Command] = {
    EventSourcedBehavior(
      persistenceId   = PersistenceId(EntityKey.name, id),
      emptyState      = State.Unscheduled(id),
      commandHandler  = applyCommand,
      eventHandler    = applyEvent
    )
      .withTagger(_ => Set(projectionTag))
      .withRecovery(Recovery.default)
  }

  protected val applyCommand: (State, Command) => Effect[Event, State] = { (state, command) =>
    state match {
      case state: State.Unscheduled =>
        command match {
          case command: Schedule =>
            schedule(state, command)

          case command: Reschedule =>
            Effect
              .unhandled
              .thenReply(command.replyTo)(_ => StatusReply.Error("Unscheduled events cannot be rescheduled"))

          case command: Cancel =>
            Effect
              .unhandled
              .thenReply(command.replyTo)(_ => StatusReply.Error("Unscheduled events cannot be canceled"))

          case command: Expire =>
            Effect
              .unhandled
              .thenReply(command.replyTo)(_ => StatusReply.Error("Unscheduled events cannot expire"))

          case _ =>
            Effect
              .unhandled
              .thenNoReply()
        }

      case state: State.Scheduled =>
        command match {
          case command: Cancel =>
            cancel(state, command)

          case command: Reschedule =>
            reschedule(state, command)

          case command: Expire =>
            expire(state, command)

          case command: Schedule =>
            Effect
              .unhandled
              .thenReply(command.replyTo)(_ => StatusReply.Error("Event already scheduled"))

          case _ =>
            Effect
              .unhandled
              .thenNoReply()
        }

      case _: State.Canceled =>
        Effect
          .unhandled
          .thenNoReply()

      case _: State.Expired =>
        Effect
          .unhandled
          .thenNoReply()
    }
  }

  protected val applyEvent: (State, Event) => State = { (state, event) =>
    event match {
      case Scheduled(token, event, at)    => State.Scheduled(token, event, at)
      case Rescheduled(token, event, at)  => State.Scheduled(token, event, at)
      case Canceled(token)                => State.Canceled(token)
      case Expired(token)                 => State.Expired(token)
    }
  }

  private def schedule(state: State.Unscheduled, command: Schedule): ReplyEffect[Event, State] = {
    val token = state.token

    Effect
      .persist(Scheduled(token, command.event, command.at))
      .thenReply(command.replyTo)(_ => StatusReply.Success(token))
  }

  private def cancel(state: State.Scheduled, command: Cancel): ReplyEffect[Event, State] =
    Effect
      .persist(Canceled(command.token))
      .thenReply(command.replyTo)(_ => StatusReply.Ack)

  private def reschedule(state: State.Scheduled, command: Reschedule): ReplyEffect[Event, State] =
    Effect
      .persist(Rescheduled(command.token, state.event, command.at))
      .thenReply(command.replyTo)(_ => StatusReply.Success(command.token))

  private def expire(state: State.Scheduled, command: Expire): ReplyEffect[Event, State] =
    Effect
      .persist(Expired(command.token))
      .thenReply(command.replyTo)(_ => StatusReply.Ack)
}
