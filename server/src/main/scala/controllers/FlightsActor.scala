package controllers

import akka.actor._
import akka.util.Timeout
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import spatutorial.shared.{FlightChanges, FlightChange}
import spatutorial.shared.FlightsApi.Flights

import scala.language.postfixOps

import scala.concurrent.duration._

case object GetFlights
case class GetFlightChanges(since: Long, numberRequested: Int)

class FlightsActor(crunchActor: ActorRef) extends Actor with ActorLogging  with FlightState {
  implicit val timeout = Timeout(5 seconds)

  var latestChanges : List[FlightChange] = Nil

  def receive = {
    case GetFlights =>
      log.info(s"Being asked for flights and I know about ${flights.size}")
      sender ! Flights(flights.values.toList)
    case Flights(newFlights) =>
      log.info(s"Adding ${newFlights.length} new flights")
      val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
      val lastMidnight = LocalDate.now().toString(formatter)
      val changedFlightsThisTick = onFlightUpdates(newFlights, lastMidnight)

      log.info(s"Applying ${changedFlightsThisTick.flightChanges.length} flight changes")
      val currentFlightChanges = latestChanges
      latestChanges = (changedFlightsThisTick.flightChanges.toList ::: currentFlightChanges.toList).take(100)

      crunchActor ! CrunchFlightsChange(newFlights)
    case message => log.error("Actor saw unexpected message: " + message.toString)
    case GetFlightChanges(since, numberRequested) =>
      sender ! FlightChanges(latestChanges.take(numberRequested))
  }
}
