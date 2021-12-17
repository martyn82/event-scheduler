package com.github.martyn82.eventscheduler.client

import cats.data._
import cats.data.Validated._
import cats.implicits._
import com.github.martyn82.eventscheduler.client.ScheduleEventRequestValidator.{AtMustNotBeEmpty, EventMustNotBeEmpty}
import com.github.martyn82.eventscheduler.grpc.{Event, ScheduleEventRequest}
import com.google.protobuf.timestamp.Timestamp

sealed trait ScheduleEventRequestValidator {
  type ValidationResult[A] = ValidatedNec[Error, A]

  def validate(request: ScheduleEventRequest): ValidationResult[ScheduleEventRequest] = (
    validateEvent(request.event),
    validateAt(request.at)
  ).mapN(ScheduleEventRequest.of)

  private def validateEvent(event: Option[Event]): ValidationResult[Option[Event]] =
    if (event.isEmpty) EventMustNotBeEmpty.invalidNec else event.validNec

  private def validateAt(at: Option[Timestamp]): ValidationResult[Option[Timestamp]] =
    if (at.isEmpty) AtMustNotBeEmpty.invalidNec else at.validNec
}

object ScheduleEventRequestValidator extends ScheduleEventRequestValidator {
  case object EventMustNotBeEmpty extends Error("Event must not be empty")
  case object AtMustNotBeEmpty extends Error("At must not be empty")
}
