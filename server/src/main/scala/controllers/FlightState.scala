package controllers


import akka.event.LoggingAdapter
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import spatutorial.shared._

import scala.language.postfixOps
import scala.collection.mutable




//todo think about where we really want this flight state, one source of truth?
trait FlightState {
  def log: LoggingAdapter

  var flights = Map[Int, ApiFlight]()
  def nowProvider(): Long

  def onFlightUpdates(fs: List[ApiFlight], since: String) = {
    val currentFlights = flights

    val changedFlights = FlightChanges.diffFlightChanges(flights, fs, nowProvider)
    val withNewFlights = addNewFlights(currentFlights, fs)
    val withoutOldFlights = filterOutFlightsBeforeThreshold(withNewFlights, since)
    flights = withoutOldFlights
    changedFlights
  }

  def addNewFlights(flights: Map[Int, ApiFlight], fs: List[ApiFlight]) = {
    val newFlights: List[(Int, ApiFlight)] = fs.map(f => (f.FlightID, f))
    flights ++ newFlights
  }

  def filterOutFlightsBeforeThreshold(flights: Map[Int, ApiFlight], since: String): Map[Int, ApiFlight] = {
    val totalFlightsBeforeFilter = flights.size
    val flightsWithOldDropped = flights.filter { case (key, flight) => flight.EstDT >= since || flight.SchDT >= since }
    val totalFlightsAfterFilter = flights.size
    log.info(s"Dropped ${totalFlightsBeforeFilter - totalFlightsAfterFilter} flights before $since")
    flightsWithOldDropped
  }

}
