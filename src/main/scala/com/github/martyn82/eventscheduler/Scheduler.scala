package com.github.martyn82.eventscheduler

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}

import java.util.UUID

object Scheduler {
  type Identifier = String
  type Token = String

  val EntityType = "Scheduler"

  sealed trait Command extends Serializable

  final case class Schedule(event: String, replyTo: ActorRef[StatusReply[Token]]) extends Command
  final case class Reschedule(token: Token, replyTo: ActorRef[StatusReply[Token]]) extends Command
  final case class Cancel(token: Token, replyTo: ActorRef[StatusReply[Done]]) extends Command
  final case class Expire(token: Token, replyTo: ActorRef[StatusReply[Done]]) extends Command

  sealed trait Event extends Serializable

  final case class Scheduled(token: Token, event: Any) extends Event
  final case class Canceled(token: Token) extends Event
  final case class Rescheduled(token: Token, event: Any) extends Event
  final case class Expired(token: Token) extends Event

  sealed trait State extends Serializable

  object State {
    final case class Unscheduled() extends State
    final case class Scheduled(token: Token, event: Any) extends State
    final case class Canceled(token: Token) extends State
    final case class Expired(token: Token) extends State
  }

  def apply(id: Identifier): Behavior[Command] = {
    EventSourcedBehavior(
      persistenceId   = PersistenceId(EntityType, id),
      emptyState      = State.Unscheduled(),
      commandHandler  = applyCommand,
      eventHandler    = applyEvent
    )
  }

  protected val applyCommand: (State, Command) => Effect[Event, State] = { (state, command) =>
    state match {
      case state: State.Unscheduled =>
        command match {
          case command: Schedule  =>
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
        command match {
          case _ =>
            Effect
              .unhandled
              .thenNoReply()
        }

      case _: State.Expired =>
        command match {
          case _ =>
            Effect
              .unhandled
              .thenNoReply()
        }
    }
  }

  protected val applyEvent: (State, Event) => State = { (_, event) =>
    event match {
      case Scheduled(token, event)    => State.Scheduled(token, event)
      case Rescheduled(token, event)  => State.Scheduled(token, event)
      case Canceled(token)            => State.Canceled(token)
      case Expired(token)             => State.Expired(token)
    }
  }

  private def generateToken(): Token =
    UUID.randomUUID().toString

  private def schedule(state: State.Unscheduled, command: Schedule): ReplyEffect[Event, State] = {
    val token = generateToken()

    Effect
      .persist(Scheduled(token, command.event))
      .thenReply(command.replyTo)(_ => StatusReply.Success(token))
  }

  private def cancel(state: State.Scheduled, command: Cancel): ReplyEffect[Event, State] =
    Effect
      .persist(Canceled(command.token))
      .thenReply(command.replyTo)(_ => StatusReply.Ack)

  private def reschedule(state: State.Scheduled, command: Reschedule): ReplyEffect[Event, State] =
    Effect
      .persist(Rescheduled(command.token, state.event))
      .thenReply(command.replyTo)(_ => StatusReply.Success(command.token))

  private def expire(state: State.Scheduled, command: Expire): ReplyEffect[Event, State] =
    Effect
      .persist(Expired(command.token))
      .thenReply(command.replyTo)(_ => StatusReply.Ack)
}
