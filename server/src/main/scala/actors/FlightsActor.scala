package actors



import akka.actor._
import akka.util.Timeout
import controllers.FlightState
import org.joda.time.{DateTime, LocalDate}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import spatutorial.shared.FlightsApi.Flights

import scala.concurrent.duration._
import scala.language.postfixOps
import akka.persistence._
import spatutorial.shared.ApiFlight

case class GetFlights(since: String, duration: Duration)

class FlightsActor(crunchActor: ActorRef) extends PersistentActor with ActorLogging  with FlightState {
  implicit val timeout = Timeout(5 seconds)

  override def persistenceId = "flights-store"

  val receiveRecover: Receive = {
    case Flights(recoveredFlights)  =>
      log.info(s"Recovering ${recoveredFlights.length} new flights")
      onFlightUpdates(recoveredFlights, dateToRetainFlightsFrom)
    case SnapshotOffer(_, snapshot: Map[Int, ApiFlight]) =>
      log.info(s"Restoring from snapshot")
      flights = snapshot
    case message => log.info(s"unhandled message - $message")
  }

  val receiveCommand: Receive = {
    case GetFlights(since, duration) =>
      log.info(s"Being asked for flights and I know about ${flights.size}")
      val until = DateTime.parse(since).plus(duration.toMillis).toString("yyyy-MM-dd")
      sender ! Flights(
        filterOutFlightsAfterThreshold(filterOutFlightsBeforeThreshold(flights, since), until).values.toList
      )
    case Flights(newFlights) =>
      log.info(s"Adding ${newFlights.length} new flights")
      onFlightUpdates(newFlights, dateToRetainFlightsFrom)
      persist(Flights(newFlights)) { (event: Flights) =>
        log.info(s"Storing ${event.flights.length} flights")
        context.system.eventStream.publish(event)
      }
      crunchActor ! PerformCrunchOnFlights(newFlights)
    case message => log.error("Actor saw unexpected message: " + message.toString)
  }
}
