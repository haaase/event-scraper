//> using scala 3.3.1
//> using dep "net.ruippeixotog::scala-scraper::3.1.1"
//> using dep com.lihaoyi::upickle::3.1.0
//> using dep com.lihaoyi::os-lib::0.9.3
import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.model.Element
import upickle.default._
import upickle._

// global objects
val browser = JsoupBrowser()
val signalCli = os
  .proc(
    "signal-cli",
    "--config",
    "/var/lib/signal-cli",
    "-a",
    sys.env.get("EVENTSCRAPER_PHONENUM").get,
    "jsonRpc"
  )
  .spawn(stderr = os.Inherit, stdout = os.Inherit)

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

def getEventList(): String =
  val doc = browser.get("https://806qm.de")
  val events = doc >> elementList(".type-tribe_events")

  def parseEvent(event: Element): String =
    val title = event >?> allText(".tribe-events-list-event-title")
    val date = event >?> allText(".tribe-event-date-start")
    s"${title.getOrElse("")} (${date.getOrElse("")})"

  events.map(parseEvent).mkString("\n")

def writeFile =
  Files.write(
    Paths.get("events.txt"),
    getEventList().getBytes(StandardCharsets.UTF_8)
  )

def sendSignalMessage(m: String) =
  val jsonMsg = write(
    SignalCliMessage(
      jsonrpc = "2.0",
      method = "send",
      params = SignalCliParams(
        message = m,
        groupId = sys.env.get("EVENTSCRAPER_GROUPID").get
      ),
      id = java.util.UUID.randomUUID.toString
    )
  )
  println(jsonMsg)
  signalCli.stdin.writeLine(jsonMsg)
  signalCli.stdin.flush()

@main
def testSignalCli =
  sendSignalMessage(s"${getEventList()}")
//  var output = signalCli.stdout.readLine()
//  while (output != null) {
//    println(output)
//    output = signalCli.stdout.readLine()
//  }
  Thread.sleep(10000)
  signalCli.destroy()
