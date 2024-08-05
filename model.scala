//// type definitions
type EpochSecond = Long
case class Event(
    title: String,
    location: String,
    start: EpochSecond,
    end: Option[EpochSecond] = None,
    announced: Option[EpochSecond] = None
)

trait EventScraper:
  def getEvents: List[Event]
