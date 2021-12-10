package com.github.martyn82.eventscheduler

import com.github.martyn82.eventscheduler.Scheduler.Token
import com.github.martyn82.eventscheduler.SchedulingRepository.Status.Status
import com.github.martyn82.eventscheduler.SchedulingRepository.{Schedule, Status}
import scalikejdbc.{DBSession, scalikejdbcSQLInterpolationImplicitDef}

class JdbcSchedulingRepository(implicit session: DBSession) extends SchedulingRepository {
  def init(): Unit = {
    sql"""
         | CREATE TABLE IF NOT EXISTS public.schedule (
         |   token VARCHAR(255) NOT NULL,
         |   timestamp INT NOT NULL,
         |   status VARCHAR(64) NOT NULL,
         |   event text NOT NULL,
         |   PRIMARY KEY (token));
         |""".stripMargin
      .execute()
      .apply()
  }

  override def store(schedule: Schedule): Unit = {
    sql"""
       | INSERT INTO schedule (token, timestamp, status, event)
       |   VALUES (${schedule.token}, ${schedule.at}, ${schedule.status.toString}, ${schedule.event})
       |   ON CONFLICT (token) DO UPDATE SET status = ${schedule.status.toString}
       |""".stripMargin
      .executeUpdate()
      .apply()
  }

  override def update(token: Token, status: Status): Unit = {
    sql"""UPDATE schedule SET status = ${status.toString} WHERE token = $token """
      .executeUpdate()
      .apply()
  }

  override def delete(token: Token): Unit = {
    sql"""DELETE FROM schedule WHERE token = $token"""
      .executeUpdate()
      .apply()
  }

  override def get(token: Token): Option[Schedule] = {
    sql"""SELECT timestamp, status, event FROM schedule WHERE token = $token"""
      .map { result =>
        Schedule(
          token,
          result.long("timestamp"),
          Status.withName(result.string("status")),
          result.string("event")
        )
      }
      .toOption()
      .apply()
  }

  override def getScheduled: Seq[Schedule] = {
    sql"""SELECT token, timestamp, status, event FROM schedule WHERE status = ${Status.Scheduled.toString}"""
      .map { result =>
        Schedule(
          result.string("token"),
          result.long("timestamp"),
          Status.withName(result.string("status")),
          result.string("event")
        )
      }
      .toList()
      .apply()
  }
}
