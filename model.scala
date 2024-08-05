//// type definitions
type EpochSecond = Long
case class Event(
    id: Option[Int] = None,
    title: String,
    start: EpochSecond,
    end: Option[EpochSecond] = None,
    announced: Option[EpochSecond] = None
)

trait EventScraper:
  def getEvents: List[Event]
