//> using scala 3.3.1
//> using dep "net.ruippeixotog::scala-scraper::3.1.1"
//> using dep com.lihaoyi::upickle::3.1.0
//> using dep com.lihaoyi::os-lib::0.10.3
//> using dep org.tpolecat::doobie-core::1.0.0-RC4
//> using dep org.xerial:sqlite-jdbc:3.46.0.0
//> using dep org.slf4j:slf4j-simple:2.0.13
//> using file model.scala
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import net.ruippeixotog.scalascraper.browser.{HtmlUnitBrowser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL.*
import net.ruippeixotog.scalascraper.dsl.DSL.Extract.*
import net.ruippeixotog.scalascraper.dsl.DSL.Parse.*
import net.ruippeixotog.scalascraper.model.Element
import upickle.default.*
import upickle.*
import doobie.*
import doobie.implicits.*
import cats.effect.IO
import cats.effect.{IO, IOApp}
import cats.implicits.*

import scala.concurrent.ExecutionContext
import cats.effect.unsafe.implicits.global
import doobie.util.log.LogEvent

import java.time.{LocalDate, ZoneId, ZoneOffset}
import java.time.format.DateTimeFormatter

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
    "location" TEXT NOT NULL,
    "start"	NUMERIC NOT NULL,
    "end"	NUMERIC,
    "announced"	NUMERIC,
    PRIMARY KEY("id" AUTOINCREMENT),
    UNIQUE("title", "location", "start")
  )""".update.run

// global objects
val browser = JsoupBrowser()
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

object Scraper806qm extends EventScraper:
  val formatter = DateTimeFormatter.ofPattern("dd–MM–yyyy")
//  val dt = formatter.parseDateTime(string)
  def getEvents: List[Event] =
    val doc = browser.get("https://806qm.de")
    val events = doc >> elementList(".type-tribe_events")

    def parseEvent(event: Element): Event =
      val title = event >> allText(".tribe-events-list-event-title")
      val dateString = (event >> allText(".tribe-event-date-start b"))
      val date =
        LocalDate
          .parse(dateString, formatter)
          .atStartOfDay()
          .atZone(ZoneId.of("Europe/Berlin"))
          .toEpochSecond
      Event(title = title, location = "806qm", start = date)

    events.map(parseEvent)

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

def saveEvents(events: List[Event]): ConnectionIO[Int] =
  val sql = "insert or ignore into events (title, location, start, end) values (?,?,?,?)"
  Update[(String, String, EpochSecond, Option[EpochSecond])](sql)
    .updateMany(events.map(e => (e.title, e.location, e.start, e.end)))

object main extends IOApp.Simple:
  val scrapers: List[EventScraper] = List(Scraper806qm)
  def run =
    for
      _ <- createTable.transact(xa) // create db
      // scrape websites
      scrapeResults <- scrapers.map(s => IO(s.getEvents).attempt).parSequence
      // write results
      events = scrapeResults.collect { case Right(e) => e }.flatten
      _ <- saveEvents(events).transact(xa)
      _ <- IO.println(events.mkString("\n"))
      // print errors
      errors = scrapeResults.collect { case Left(t) => t.getMessage }
      _ <- IO.println(errors.mkString("\n"))
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
