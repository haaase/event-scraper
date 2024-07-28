//> using scala 3.3.1
//> using dep "net.ruippeixotog::scala-scraper::3.1.1"
//> using dep com.lihaoyi::upickle::3.1.0
//> using dep com.lihaoyi::os-lib::0.10.3
//> using dep org.tpolecat::doobie-core::1.0.0-RC4
//> using dep org.xerial:sqlite-jdbc:3.46.0.0
//> using file model.scala
import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.model.Element
import upickle.default._
import upickle._
import doobie._
import doobie.implicits._
import cats.effect.IO
import scala.concurrent.ExecutionContext
import cats.effect.unsafe.implicits.global

val xa = Transactor.fromDriverManager[IO](
  driver = "org.sqlite.JDBC",
  url = "jdbc:sqlite:events.db",
  logHandler = None
)


val createTable: ConnectionIO[Int] =
  sql"""CREATE TABLE "events" (
    "id"	INTEGER,
    "title"	TEXT NOT NULL,
    "date"	INTEGER NOT NULL,
    PRIMARY KEY("id" AUTOINCREMENT)
  )"""
  .update.run


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
  def getEvents: List[Event] =
    val doc = browser.get("https://806qm.de")
    val events = doc >> elementList(".type-tribe_events")

    def parseEvent(event: Element): Event =
      val title = event >?> allText(".tribe-events-list-event-title")
      val date = event >?> allText(".tribe-event-date-start")
      Event(title=title.getOrElse(""), start = date.getOrElse(""), end = date.getOrElse(""))

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

@main
def testSignalCli() =
  createTable.transact(xa).unsafeRunSync()
  val scrapers: List[EventScraper] = List(Scraper806qm)
// sendSignalMessage(s"${getEvents}")
//  var output = signalCli.stdout.readLine()
//  while (output != null) {
//    println(output)
//    output = signalCli.stdout.readLine()
//  }
  Thread.sleep(10000) // wait 10s
//  signalCli.destroy()
