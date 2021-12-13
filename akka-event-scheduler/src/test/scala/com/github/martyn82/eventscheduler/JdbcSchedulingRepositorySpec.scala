package com.github.martyn82.eventscheduler

import com.github.martyn82.eventscheduler.Scheduler.EventData
import com.github.martyn82.eventscheduler.SchedulingRepository.{Schedule, Status}
import org.mockito.ArgumentMatchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.FixtureAnyWordSpecLike
import scalikejdbc._
import scalikejdbc.config.DBs
import scalikejdbc.scalatest.AutoRollback

class JdbcSchedulingRepositorySpec extends FixtureAnyWordSpecLike with AutoRollback with BeforeAndAfterAll {
  private var repo: JdbcSchedulingRepository = _

  override protected def beforeAll(): Unit = {
    DBs.setupAll()
  }

  override def fixture(implicit session: DBSession): Unit = {
    repo = new JdbcSchedulingRepository()
    repo.init()
  }

  "JdbcSchedulingRepository" should {
    "store scheduled event" in { implicit session =>
      val token = "token"
      val event = "event"
      val at = 12345

      repo.store(Schedule(token, at, Status.Scheduled, event, "\u0000".getBytes))

      val retrieved = repo.get(token)
      retrieved should not be(None)
      retrieved.get.token should equal(token)
      retrieved.get.status should equal(Status.Scheduled)
      retrieved.get.eventType should equal(event)
      retrieved.get.at should equal(at)
    }

    "update an already scheduled event with store" in { implicit session =>
      val token = "token"
      val event = "event"
      val at = 12345

      repo.store(Schedule(token, at, Status.Scheduled, event, event.getBytes))
      repo.store(Schedule(token, at, Status.Canceled, event, event.getBytes))

      val retrieved = repo.get(token)
      retrieved should not be(None)
      retrieved.get.token should equal(token)
      retrieved.get.status should equal(Status.Canceled)
      retrieved.get.eventType should equal(event)
      retrieved.get.at should equal(at)
    }

    "update an already scheduled event" in { implicit session =>
      val token = "token"
      val event = "event"
      val at = 12345

      repo.store(Schedule(token, at, Status.Scheduled, event, event.getBytes))
      repo.update(token, Status.Expired)

      val retrieved = repo.get(token)
      retrieved should not be(None)
      retrieved.get.token should equal(token)
      retrieved.get.status should equal(Status.Expired)
      retrieved.get.eventType should equal(event)
      retrieved.get.at should equal(at)
    }

    "remove a scheduled event" in { implicit session =>
      val token = "token"
      val event = "event"
      val at = 12345

      repo.store(Schedule(token, at, Status.Scheduled, event, event.getBytes))
      repo.delete(token)

      repo.get(token) should equal(None)
    }

    "retrieve all scheduled events" in { implicit session =>
      val token1 = "token1"
      val token2 = "token2"
      val event = "event"
      val at = 12345

      repo.store(Schedule(token1, at, Status.Scheduled, event, event.getBytes))
      repo.store(Schedule(token2, at, Status.Canceled, event, event.getBytes))

      val retrieved = repo.getScheduled
      retrieved should not be(empty)
      retrieved.head.token should equal(token1)
      retrieved.head.status should equal(Status.Scheduled)
      retrieved.head.eventType should equal(event)
      retrieved.head.at should equal(at)
    }
  }
}
