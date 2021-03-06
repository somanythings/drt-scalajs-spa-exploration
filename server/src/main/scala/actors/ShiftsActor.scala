package actors

import akka.persistence._
import drt.shared.MilliDate
import org.joda.time.format.DateTimeFormat
import server.protobuf.messages.ShiftMessage.{ShiftMessage, ShiftsMessage}
import org.slf4j.LoggerFactory

import scala.util.Try

case class ShiftsState(events: List[String] = Nil) {
  def updated(data: String): ShiftsState = copy(data :: events)

  def size: Int = events.length

  override def toString: String = events.reverse.toString
}

case object GetState

object ShiftsMessageParser {

  def dateAndTimeToMillis(date: String, time: String): Option[Long] = {

    val formatter = DateTimeFormat.forPattern("dd/MM/yy HH:mm")
    Try {
      formatter.parseMillis(date + " " + time)
    }.toOption
  }

  def dateString(timestamp: Long) = {
    import services.SDate.implicits._

    MilliDate(timestamp).ddMMyyString
  }

  def timeString(timestamp: Long) = {
    import services.SDate.implicits._

    val date = MilliDate(timestamp)

    f"${date.getHours()}%02d:${date.getMinutes()}%02d"
  }

  def startAndEndTimestamps(startDate: String, startTime: String, endTime: String): (Option[Long], Option[Long]) = {
    val startMillis = dateAndTimeToMillis(startDate, startTime)
    val endMillis = dateAndTimeToMillis(startDate, endTime)

    val oneDay = 60 * 60 * 24 * 1000L

    (startMillis, endMillis) match {
      case (Some(start), Some(end)) =>
        if (start <= end)
          (Some(start), Some(end))
        else
          (Some(start), Some(end + oneDay))
      case _ => (None, None)
    }
  }

  val log = LoggerFactory.getLogger(getClass)

  def shiftStringToShiftMessage(shift: String): Option[ShiftMessage] = {
    shift.replaceAll("([^\\\\]),", "$1\",\"").split("\",\"").toList.map(_.trim) match {
      case List(description, terminalName, startDay, startTime, endTime, staffNumberDelta) =>
        val (startTimestamp, endTimestamp) = startAndEndTimestamps(startDay, startTime, endTime)
        Some(ShiftMessage(
          name = Some(description),
          terminalName = Some(terminalName),
          startTimestamp = startTimestamp,
          endTimestamp = endTimestamp,
          numberOfStaff = Some(staffNumberDelta)
        ))
      case _ =>
        log.warn(s"Couldn't parse shifts line: '$shift'")
        None
    }
  }

  def shiftsStringToShiftsMessage(shifts: String): ShiftsMessage = {
    val shiftMessages = shifts.split("\n").map(shiftStringToShiftMessage).collect { case Some(x) => x }
    ShiftsMessage(shiftMessages)
  }

  def shiftsMessageToShiftsString(shiftsMessage: ShiftsMessage) = {
    shiftsMessage.shifts.map {
        case ShiftMessage(Some(name), Some(terminalName), Some(startDay), Some(startTime), Some(endTime), Some(numberOfStaff), None, None) =>
          s"$name, $terminalName, $startDay, $startTime, $endTime, $numberOfStaff"
        case ShiftMessage(Some(name), Some(terminalName), None, None, None, Some(numberOfStaff), Some(startTimestamp), Some(endTimestamp)) =>
          s"$name, $terminalName, ${ShiftsMessageParser.dateString(startTimestamp)}, ${ShiftsMessageParser.timeString(startTimestamp)}, ${ShiftsMessageParser.timeString(endTimestamp)}, $numberOfStaff"
    }.mkString("\n")
  }
}

class ShiftsActor extends PersistentActor {

  override def persistenceId = "shifts-store"

  var state = ShiftsState()

  import ShiftsMessageParser._

  def updateState(data: String): Unit = {
    state = state.updated(data)
  }

  val receiveRecover: Receive = {
    case shiftsMessage: ShiftsMessage => updateState(shiftsMessageToShiftsString(shiftsMessage))
    case SnapshotOffer(_, snapshot: ShiftsState) => state = snapshot
  }

  val receiveCommand: Receive = {
    case GetState =>
      sender() ! state.events.headOption.getOrElse("")
    case data: String =>
      persist(shiftsStringToShiftsMessage(data)) { shiftsMessage =>
        updateState(data)
        context.system.eventStream.publish(shiftsMessage)
      }
  }
}
