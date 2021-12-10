package com.github.martyn82.eventscheduler

import com.github.martyn82.eventscheduler.SchedulingRepository.{Schedule, Status}
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

      repo.store(Schedule(token, at, Status.Scheduled, event))

      repo.get(token) should equal(Some(Schedule(token, at, Status.Scheduled, event)))
    }

    "update an already scheduled event with store" in { implicit session =>
      val token = "token"
      val event = "event"
      val at = 12345

      repo.store(Schedule(token, at, Status.Scheduled, event))
      repo.store(Schedule(token, at, Status.Canceled, event))

      repo.get(token) should equal(Some(Schedule(token, at, Status.Canceled, event)))
    }

    "update an already scheduled event" in { implicit session =>
      val token = "token"
      val event = "event"
      val at = 12345

      repo.store(Schedule(token, at, Status.Scheduled, event))
      repo.update(token, Status.Expired)

      repo.get(token) should equal(Some(Schedule(token, at, Status.Expired, event)))
    }

    "remove a scheduled event" in { implicit session =>
      val token = "token"
      val event = "event"
      val at = 12345

      repo.store(Schedule(token, at, Status.Scheduled, event))
      repo.delete(token)

      repo.get(token) should equal(None)
    }

    "retrieve all scheduled events" in { implicit session =>
      val token1 = "token1"
      val token2 = "token2"
      val event = "event"
      val at = 12345

      repo.store(Schedule(token1, at, Status.Scheduled, event))
      repo.store(Schedule(token2, at, Status.Canceled, event))

      repo.getScheduled should equal(Seq(Schedule(token1, at, Status.Scheduled, event)))
    }
  }
}
