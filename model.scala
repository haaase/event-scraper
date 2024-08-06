import java.time.ZonedDateTime
//// type definitions
type EpochSecond = Long
case class Event(
    id: Option[Int] = None,
    title: String,
    subtitle: Option[String] = None,
    location: String,
    start: ZonedDateTime,
    end: Option[ZonedDateTime] = None,
    announced: Option[ZonedDateTime] = None
):
  override def toString =
    s"""$title ${subtitle.map("(" + _ + ")").getOrElse("")}
       |$location / ${start.toLocalDate.toString}
       |""".stripMargin

