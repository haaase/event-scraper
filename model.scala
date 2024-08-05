//// type definitions
type Date = String
case class Event(id: Option[Int] = None, title: String, start: Date, end: Date)

trait EventScraper:
  def getEvents: List[Event]