package controllers


import akka.event.LoggingAdapter
import org.joda.time.{DateTime, LocalDate}
import org.joda.time.format.DateTimeFormat
import spatutorial.shared.ApiFlight

import scala.language.postfixOps
import scala.collection.mutable
import scala.concurrent.duration.Duration

//todo think about where we really want this flight state, one source of truth?
trait FlightState {
  def log: LoggingAdapter

  var flights = Map[Int, ApiFlight]()

  val dateToRetainFlightsFrom = "2017-01-01"

  def onFlightUpdates(fs: List[ApiFlight], since: String) = {
    val currentFlights = flights

    val loggedFlights = logNewFlightInfo(flights, fs)
    val withNewFlights = addNewFlights(loggedFlights, fs)
    val withoutOldFlights = filterOutFlightsBeforeThreshold(withNewFlights, since)
    flights = withoutOldFlights
  }

  def addNewFlights(flights: Map[Int, ApiFlight], fs: List[ApiFlight]) = {
    val newFlights: List[(Int, ApiFlight)] = fs.map(f => (f.FlightID, f))
    flights ++ newFlights
  }

  def filterOutFlightsBeforeThreshold(flights: Map[Int, ApiFlight], since: String): Map[Int, ApiFlight] = {
    log.info(s"The Flights before droppping old $flights")
    val totalFlightsBeforeFilter = flights.size
    val flightsWithOldDropped = flights.filter {
      case (key, flight) => {
        flight.EstDT >= since || flight.SchDT >= since
      }
    }
    val totalFlightsAfterFilter = flightsWithOldDropped.size
    log.info(s"Dropped ${totalFlightsBeforeFilter - totalFlightsAfterFilter} flights before $since")
    log.info(s"The Flights we have left after droppping old $flightsWithOldDropped")
    flightsWithOldDropped
  }

  def filterOutFlightsAfterThreshold(flights: Map[Int, ApiFlight], until: String) = {
    log.info(s"The Flights before droppping new $flights")
    val totalFlightsBeforeFilter = flights.size
    val flightsWithNewerDropped = flights.filter {
      case (key, flight) if flight.EstDT != "" => {
        log.info(s"We have an EstDT = ${flight.EstDT}")
        log.info(s"Comparing: ${flight.EstDT} || ${flight.SchDT} <= $until")

        flight.EstDT <= until || flight.SchDT <= until
      }
      case (key, flight) => {
        log.info(s"Comparing: ${flight.EstDT} || ${flight.SchDT} <= $until")
        log.info(s"We don't have an EstDT = ${flight.EstDT}")
        flight.SchDT <= until
      }
    }
    val totalFlightsAfterFilter = flightsWithNewerDropped.size
    log.info(s"Dropped ${totalFlightsBeforeFilter - totalFlightsAfterFilter} flights after $until")
    log.info(s"The Flights we have left $flightsWithNewerDropped")
    flightsWithNewerDropped
  }

  def logNewFlightInfo(flights: Map[Int, ApiFlight], fs: List[ApiFlight]) = {
    val inboundFlightIds: Set[Int] = fs.map(_.FlightID).toSet
    val existingFlightIds: Set[Int] = flights.keys.toSet

    val updatingFlightIds = existingFlightIds intersect inboundFlightIds
    val newFlightIds = existingFlightIds diff inboundFlightIds

    log.info(s"New flights ${fs.filter(newFlightIds contains _.FlightID)}")
    log.info(s"Old      fl ${flights.filterKeys(updatingFlightIds).values}")
    log.info(s"Updating fl ${fs.filter(updatingFlightIds contains _.FlightID)}")
    flights
  }
}
