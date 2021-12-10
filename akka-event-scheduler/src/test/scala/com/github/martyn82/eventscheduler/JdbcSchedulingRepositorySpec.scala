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
      val event = "foo"
      val at = 12345

      repo.store(Schedule(token, event, at, Status.Scheduled))

      repo.get(token) should equal(Some(Schedule(token, event, at, Status.Scheduled)))
    }

    "update an already scheduled event with store" in { implicit session =>
      val token = "token"
      val event = "foo"
      val at = 12345

      repo.store(Schedule(token, event, at, Status.Scheduled))
      repo.store(Schedule(token, event, at, Status.Canceled))

      repo.get(token) should equal(Some(Schedule(token, event, at, Status.Canceled)))
    }

    "update an already scheduled event" in { implicit session =>
      val token = "token"
      val event = "foo"
      val at = 12345

      repo.store(Schedule(token, event, at, Status.Scheduled))
      repo.update(token, Status.Expired)

      repo.get(token) should equal(Some(Schedule(token, event, at, Status.Expired)))
    }

    "remove a scheduled event" in { implicit session =>
      val token = "token"
      val event = "foo"
      val at = 12345

      repo.store(Schedule(token, event, at, Status.Scheduled))
      repo.delete(token)

      repo.get(token) should equal(None)
    }
  }
}
