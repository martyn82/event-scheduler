package com.github.martyn82.eventscheduler

import com.github.martyn82.eventscheduler.Scheduler.{Token, Timestamp}

object SchedulingRepository {
  import SchedulingRepository.Status.Status

  final case class Schedule(token: Token, at: Timestamp, status: Status, eventType: String, eventData: Array[Byte])

  object Status extends Enumeration {
    type Status = Value

    val Scheduled: Value = Value(0, "Scheduled")
    val Canceled: Value = Value(1, "Canceled")
    val Expired: Value = Value(2, "Expired")
  }
}

trait SchedulingRepository {
  import SchedulingRepository.Schedule
  import SchedulingRepository.Status.Status

  def store(schedule: Schedule): Unit
  def update(token: Token, status: Status): Unit
  def delete(token: Token): Unit
  def get(token: Token): Option[Schedule]
  def getScheduled: Seq[Schedule]
}
