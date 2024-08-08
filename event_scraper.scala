//> using scala 3.4.2
//> using dep net.ruippeixotog::scala-scraper::3.1.1
//> using dep com.softwaremill.sttp.client3::core:3.9.7
//> using dep com.lihaoyi::upickle::3.1.0
//> using dep com.lihaoyi::os-lib::0.10.3
//> using dep org.tpolecat::doobie-core::1.0.0-RC4
//> using dep org.xerial:sqlite-jdbc:3.46.0.1
//> using dep org.slf4j:slf4j-simple:2.0.15
//> using file model.scala
//> using file scrapers.scala
import cats.effect.{IO, IOApp}
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import sttp.client3.*
import upickle.*
import upickle.default.*

import java.time.{Instant, ZoneId, ZonedDateTime}

// global objects
val httpBackend = HttpClientSyncBackend()
val xa = Transactor.fromDriverManager[IO](
  driver = "org.sqlite.JDBC",
  url = "jdbc:sqlite:events.db",
  logHandler = None
)

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

//val createAnnounced: ConnectionIO[Int] =
//  sql"""CREATE TABLE IF NOT EXISTS "events_announced"(
//    "event_id" INTEGER NOT NULL,
//    "announced_epoch" NUMERIC,
//    PRIMARY KEY ("event_id"),
//    FOREIGN KEY ("event_id") REFERENCES "events"("id") ON UPDATE CASCADE ON DELETE CASCADE
//  )""".update.run

//val signalCli = os
//  .proc(
//    "signal-cli",
//    "--config",
//    "/var/lib/signal-cli",
//    "-a",
//    sys.env("EVENTSCRAPER_PHONENUM"),
//    "jsonRpc"
//  )
//  .spawn(stderr = os.Inherit, stdout = os.Inherit)

// JSON RPC encoding
case class SignalCliMessage(
    jsonrpc: String,
    method: String,
    id: String,
    params: SignalCliParams
) derives ReadWriter
case class SignalCliParams(
    message: String = "",
    groupId: String = "",
    username: String = ""
) derives ReadWriter

def sendSignalMessage(m: String): Unit =
  val jsonMsg = write(
    SignalCliMessage(
      jsonrpc = "2.0",
      method = "send",
      params = SignalCliParams(
        message = m,
        groupId = sys.env("EVENTSCRAPER_GROUPID")
      ),
      id = java.util.UUID.randomUUID.toString
    )
  )
  println(jsonMsg)
  val response = basicRequest
    .contentType("application/json")
    .body(jsonMsg)
    .post(uri"http://localhost:8094/api/v1/rpc")
    .send(httpBackend)
  println(response)
//  signalCli.stdin.writeLine(jsonMsg)
//  signalCli.stdin.flush()

given Put[ZonedDateTime] =
  Put[Long].tcontramap((x: ZonedDateTime) => x.toEpochSecond)
given Get[ZonedDateTime] =
  Get[Long].tmap((x: Long) =>
    ZonedDateTime.ofInstant(
      Instant.ofEpochSecond(x),
      ZoneId.of("Europe/Berlin")
    )
  )

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
            sendSignalMessage(newEvents.mkString("\n"))
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
      // announce events via signal
      _ <- announceNewEvents
      // print errors
      errors = scrapeResults.collect { case Left(t) => t.getMessage }
      _ <- IO.println(errors.mkString("\n"))
//      event <- getEvent(1).transact(xa)
//      _ <- IO.println(event)
    yield ()
