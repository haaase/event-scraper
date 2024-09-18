//> using scala 3.4.2
//> using dep net.ruippeixotog::scala-scraper::3.1.1
//> using dep com.softwaremill.sttp.client3::core:3.9.8
//> using dep com.lihaoyi::upickle::3.1.0
//> using dep com.lihaoyi::os-lib::0.10.3
//> using dep org.tpolecat::doobie-core::1.0.0-RC4
//> using dep org.xerial:sqlite-jdbc:3.46.0.1
//> using dep org.slf4j:slf4j-simple:2.0.15
//> using file model.scala
//> using file scrapers.scala
//> using file signal.scala
import cats.effect.{IO, IOApp}
import cats.implicits.*
import doobie.*
import doobie.implicits.*

import java.time.*

// global objects: database connection
val xa = Transactor.fromDriverManager[IO](
  driver = "org.sqlite.JDBC",
  url = "jdbc:sqlite:events.db",
  logHandler = None
)

// database transactions
val createTable: ConnectionIO[Int] =
  sql"""CREATE TABLE IF NOT EXISTS "events" (
    "id"	INTEGER,
    "title"	TEXT NOT NULL,
    "subtitle" TEXT,
    "location" TEXT NOT NULL,
    "start_epoch"	NUMERIC NOT NULL,
    "end_epoch"	NUMERIC,
    "announced_epoch" NUMERIC,
    PRIMARY KEY("id" AUTOINCREMENT),
    UNIQUE("title", "location", "start_epoch")
  )""".update.run

def markAsAnnounced(events: List[Event]) =
  val timestamp = Instant.now().getEpochSecond
  val sql = s"update events SET announced_epoch = $timestamp where id IS ?"
  Update[Int](sql).updateMany(events.map(_.id.get))

def saveEvents(events: List[Event]): ConnectionIO[Int] =
  val sql =
    "insert or ignore into events (title, subtitle, location, start_epoch, end_epoch) values (?,?,?,?,?)"
  Update[
    (String, Option[String], String, ZonedDateTime, Option[ZonedDateTime])
  ](sql)
    .updateMany(
      events.map(e => (e.title, e.subtitle, e.location, e.start, e.end))
    )

def getEvent(id: Int): ConnectionIO[Event] =
  sql"select * from events where id = $id"
    .query[Event]
    .unique

def getEventsNextWeek: ConnectionIO[List[Event]] =
  val endOfNextWeek = LocalDateTime
    .of(nextSunday, LocalTime.MAX)
    .atZone(ZoneId.of("Europe/Berlin"))
  val startOfNextWeek = LocalDateTime
    .of(nextMonday, LocalTime.MIN)
    .atZone(ZoneId.of("Europe/Berlin"))

  sql"select * from events where start_epoch >= ${startOfNextWeek.toEpochSecond} AND start_epoch <= ${endOfNextWeek.toEpochSecond} ORDER BY start_epoch"
    .query[Event]
    .to[List]
// end database transactions

def nextSunday: LocalDate =
  val currDay = LocalDate.now()
  val daysTillSunday = currDay.getDayOfWeek.getValue
  if daysTillSunday == 0 then currDay.plusDays(7)
  else currDay.plusDays(daysTillSunday)

def nextMonday: LocalDate =
  nextSunday.minusDays(6)

def announceNextWeeksEvents: IO[Unit] =
  for
    events <- getEventsNextWeek.transact(xa)
    message = s"""Events Next Week ($nextMonday - $nextSunday)
         |================
         |${events.mkString { "\n\n" }}
         |""".stripMargin
    _ <- IO(sendSignalMessage(message))
    _ <- markAsAnnounced(events).transact(xa)
  yield ()

// https://stackoverflow.com/questions/71212284/doobie-lifting-arbitrary-effect-into-connectionio-ce3
def announceNewEvents: IO[Unit] =
  WeakAsync.liftK[IO, ConnectionIO].use { fk =>
    val selectNew =
      sql"select * from events where announced_epoch IS NULL ORDER BY start_epoch"
    val updateAnnounced =
      sql"update events SET announced_epoch = ${Instant.now().getEpochSecond} where announced_epoch IS NULL"

    val transaction = for {
      newEvents <- selectNew.query[Event].to[List]
      _ <- fk(
        IO(
          if newEvents.nonEmpty then
            IO(sendSignalMessage(newEvents.mkString("\n")))
        )
      )
      _ <- updateAnnounced.update.run
    } yield ()

    transaction.transact(xa)
  }

object main extends IOApp.Simple:
  val scrapers: List[EventScraper] = List(`806qm`, OetingerVilla, Schlosskeller)
  def run =
    for
      // create db
      _ <- createTable.transact(xa)
      // scrape websites
      scrapeResults <- scrapers.map(s => IO(s.getEvents).attempt).parSequence
      // write results
      events = scrapeResults.collect { case Right(e) => e }.flatten
      _ <- saveEvents(events).transact(xa)
      // announce new events via signal
      _ <- announceNewEvents
      // announce new events if its Saturday
//      _ <-
//        if LocalDate.now().getDayOfWeek.getValue == 6 then
//          announceNextWeeksEvents
//        else IO.unit
      _ <- announceNextWeeksEvents
      // print errors
      errors = scrapeResults.collect { case Left(t) => s"ERROR: $t" }
      _ <- IO.println(errors.mkString("\n"))
//      event <- getEvent(1).transact(xa)
//      _ <- IO.println(event)
    yield ()
