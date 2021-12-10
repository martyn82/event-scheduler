package com.github.martyn82.eventscheduler

import akka.japi.function
import akka.projection.jdbc.JdbcSession
import scalikejdbc.DB

import java.sql.Connection

object ScalikeJdbcSession {
  def withSession[R](f: ScalikeJdbcSession => R): R = {
    val session = new ScalikeJdbcSession
    try {
      f(session)
    } finally {
      session.close()
    }
  }
}

final class ScalikeJdbcSession extends JdbcSession {
  private lazy val db: DB = init

  private def init: DB = {
    val db = DB.connect()
    db.autoClose(false)
    db
  }

  override def withConnection[Result](func: function.Function[Connection, Result]): Result = {
    db.begin()
    db.withinTxWithConnection(func(_))
  }

  override def commit(): Unit = db.commit()

  override def rollback(): Unit = db.rollback()

  override def close(): Unit = db.close()
}
