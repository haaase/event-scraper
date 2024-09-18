import doobie.{Get, Put}
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

//// type definitions
type EpochSecond = Long
// datetime format
// Wed, 18. Sep 2024, 18:00
val formatter = DateTimeFormatter.ofPattern("ccc, dd. LLL, yyyy")
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
       |$location / ${start.format(formatter)}""".stripMargin

// doobie translations
given Put[ZonedDateTime] =
  Put[Long].tcontramap((x: ZonedDateTime) => x.toEpochSecond)
given Get[ZonedDateTime] =
  Get[Long].tmap((x: Long) =>
    ZonedDateTime.ofInstant(
      Instant.ofEpochSecond(x),
      ZoneId.of("Europe/Berlin")
    )
  )
