package com.github.martyn82.eventscheduler

import akka.actor.typed.ActorSystem
import akka.japi.function
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import akka.projection.jdbc.JdbcSession
import scalikejdbc._

import java.sql.Connection

object ScalikeJdbcSession {
  def createSchema()(implicit session: DBSession, sys: ActorSystem[_]): Unit = {
    SchemaUtils.createIfNotExists()

    sql"""
       |CREATE TABLE IF NOT EXISTS akka_projection_offset_store (
       |                                                            projection_name VARCHAR(255) NOT NULL,
       |                                                            projection_key VARCHAR(255) NOT NULL,
       |                                                            current_offset VARCHAR(255) NOT NULL,
       |                                                            manifest VARCHAR(4) NOT NULL,
       |                                                            mergeable BOOLEAN NOT NULL,
       |                                                            last_updated BIGINT NOT NULL,
       |                                                            PRIMARY KEY(projection_name, projection_key)
       |);
       |
       |CREATE INDEX IF NOT EXISTS projection_name_index ON akka_projection_offset_store (projection_name);
       |
       |CREATE TABLE IF NOT EXISTS akka_projection_management (
       |                                                          projection_name VARCHAR(255) NOT NULL,
       |                                                          projection_key VARCHAR(255) NOT NULL,
       |                                                          paused BOOLEAN NOT NULL,
       |                                                          last_updated BIGINT NOT NULL,
       |                                                          PRIMARY KEY(projection_name, projection_key)
       |);
    """.stripMargin
      .execute()
      .apply()
  }

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
