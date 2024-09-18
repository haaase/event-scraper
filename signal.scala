import sttp.client3.*
import upickle.*
import upickle.default.*

// global objects
val httpBackend = HttpClientSyncBackend()

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

def sendSignalMessage(
    m: String
): Identity[Response[Either[String, String]]] =
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
  basicRequest
    .contentType("application/json")
    .body(jsonMsg)
    .post(uri"http://localhost:8094/api/v1/rpc")
    .send(httpBackend)
