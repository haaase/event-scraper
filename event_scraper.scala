//> using scala 3.4.2
//> using dep "net.ruippeixotog::scala-scraper::3.1.1"
//> using dep com.lihaoyi::upickle::3.1.0
//> using dep com.lihaoyi::os-lib::0.10.3
//> using dep org.tpolecat::doobie-core::1.0.0-RC4
//> using dep org.xerial:sqlite-jdbc:3.46.0.1
//> using dep org.slf4j:slf4j-simple:2.0.13
//> using file model.scala
//> using file scrapers.scala
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import upickle.default.*
import upickle.*
import doobie.*
import doobie.implicits.*
import cats.effect.{IO, IOApp}
import cats.implicits.*

import scala.concurrent.ExecutionContext
import cats.effect.unsafe.implicits.global
import doobie.util.log.LogEvent

import java.time.{Instant, ZonedDateTime, ZoneId}

val xa = Transactor.fromDriverManager[IO](
  driver = "org.sqlite.JDBC",
  url = "jdbc:sqlite:events.db",
  logHandler = None
)

val printSqlLogHandler: LogHandler[IO] = new LogHandler[IO] {
  def run(logEvent: LogEvent): IO[Unit] =
    IO {
      println(logEvent.sql)
    }
}

val createTable: ConnectionIO[Int] =
  sql"""CREATE TABLE IF NOT EXISTS "events" (
    "id"	INTEGER,
    "title"	TEXT NOT NULL,
    "subtitle" TEXT,
    "location" TEXT NOT NULL,
    "start"	NUMERIC NOT NULL,
    "end"	NUMERIC,
    "announced"	NUMERIC,
    PRIMARY KEY("id" AUTOINCREMENT),
    UNIQUE("title", "location", "start")
  )""".update.run

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


//def writeFile =
//  Files.write(
//    Paths.get("events.txt"),
//    getEventList().getBytes(StandardCharsets.UTF_8)
//  )

//def sendSignalMessage(m: String): Unit =
//  val jsonMsg = write(
//    SignalCliMessage(
//      jsonrpc = "2.0",
//      method = "send",
//      params = SignalCliParams(
//        message = m,
//        groupId = sys.env("EVENTSCRAPER_GROUPID")
//      ),
//      id = java.util.UUID.randomUUID.toString
//    )
//  )
//  println(jsonMsg)
//  signalCli.stdin.writeLine(jsonMsg)
//  signalCli.stdin.flush()

given Put[ZonedDateTime] =
  Put[Long].tcontramap((x: ZonedDateTime) => x.toEpochSecond)
given Get[ZonedDateTime] =
  Get[Long].tmap((x: Long) => ZonedDateTime.ofInstant(Instant.ofEpochSecond(x), ZoneId.of("Europe/Berlin")))

def saveEvents(events: List[Event]): ConnectionIO[Int] =
  val sql =
    "insert or ignore into events (title, subtitle, location, start, end) values (?,?,?,?,?)"
  Update[(String, Option[String], String, ZonedDateTime, Option[ZonedDateTime])](
    sql
  )
    .updateMany(
      events.map(e => (e.title, e.subtitle, e.location, e.start, e.end))
    )

def getEvent(id: Int): ConnectionIO[Event] =
  sql"select * from events where id = $id".query[Event].stream.take(1).compile.onlyOrError

object main extends IOApp.Simple:
  val scrapers: List[EventScraper] = List(`806qm`, OetingerVilla)
  def run =
    for
      // create db
      _ <- createTable.transact(xa)
      // scrape websites
      scrapeResults <- scrapers.map(s => IO(s.getEvents).attempt).parSequence
      // write results
      events = scrapeResults.collect { case Right(e) => e }.flatten
      _ <- saveEvents(events).transact(xa)
      _ <- IO.println(events.mkString("\n"))
      // print errors
      errors = scrapeResults.collect { case Left(t) => t.getMessage }
      _ <- IO.println(errors.mkString("\n"))
      event <- getEvent(1).transact(xa)
      _ <- IO.println(event)
    yield ()

def testSignalCli() =
  createTable.transact(xa).unsafeRunSync()
// sendSignalMessage(s"${getEvents}")
//  var output = signalCli.stdout.readLine()
//  while (output != null) {
//    println(output)
//    output = signalCli.stdout.readLine()
//  }
  Thread.sleep(10000) // wait 10s
//  signalCli.destroy()
