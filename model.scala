import doobie.{Get, Put}
import java.time.{Instant, ZoneId, ZonedDateTime}

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
       |$location / ${start.toLocalDate.toString}""".stripMargin

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
