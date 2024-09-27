import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.*
import net.ruippeixotog.scalascraper.dsl.DSL.Extract.*
import net.ruippeixotog.scalascraper.model.Element

import java.time.{LocalDate, Year, ZoneId}
import java.time.format.DateTimeFormatter

// global objects
val browser = JsoupBrowser()

trait EventScraper:
  def getEvents: List[Event]

object OetingerVilla extends EventScraper:
  def getEvents: List[Event] =
    val events =
      browser.get("https://oetingervilla.de") >> elementList(".card-event")

    def parseEvent(event: Element): Event =
      val title = event >> allText(".event__name")
      val subtitle = event >> extractor(".top__heading") >> allText("h3")
      val venue = "Oetinger Villa"
      val date = (event >> extractor("h3.date", texts) match {
        case (day :: month :: year :: Nil) =>
          LocalDate.parse(
            s"$day/$month/$year",
            DateTimeFormatter.ofPattern("dd/MM/yy")
          )
      }).atStartOfDay().atZone(ZoneId.of("Europe/Berlin"))
      Event(
        title = title,
        subtitle = Some(subtitle),
        location = venue,
        start = date
      )

    events.map(parseEvent)

object `806qm` extends EventScraper:
  def getEvents: List[Event] =
    val doc = browser.get("https://806qm.de")
    val events = doc >> elementList(".type-tribe_events")

    def parseEvent(event: Element): Event =
      val title = event >> allText(".tribe-events-list-event-title")
      val room = event >> allText(".venue")
      val venue = if room.isBlank then "806qm" else s"806qm ($room)"
      val dateString = (event >> allText(".tribe-event-date-start b"))
      val formatter = DateTimeFormatter.ofPattern("dd–MM–yyyy")
      val date =
        LocalDate
          .parse(dateString, formatter)
          .atStartOfDay()
          .atZone(ZoneId.of("Europe/Berlin"))
      Event(title = title, location = venue, start = date)

    events.map(parseEvent)

object Schlosskeller extends EventScraper:
  def getEvents: List[Event] =
    val events =
      browser.get("https://www.schlosskeller-darmstadt.de") >> elementList(
        ".card.fxr"
      )

    def parseEvent(event: Element): Event =
      val title = event >> allText("h3.event-title")
      val subtitle = event >> allText(".event-description")
      val venue = "Schlosskeller"
      val date = (event >> extractor("span.date span", texts) match {
        case (weekday :: day :: month :: Nil) =>
          val monthEngl = month.capitalize match
            case "Mär" => "Mar"
            case "Mai" => "May"
            case "Okt" => "Oct"
            case "Dez" => "Dec"
            case m     => m
          LocalDate.parse(
            s"$day/$monthEngl/${Year.now().getValue}",
            DateTimeFormatter.ofPattern("dd/LLL/yyyy")
          )
      }).atStartOfDay().atZone(ZoneId.of("Europe/Berlin"))
      Event(
        title = title,
        subtitle = Some(subtitle),
        location = venue,
        start = date
      )

    events.map(parseEvent)
