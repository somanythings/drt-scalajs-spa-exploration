package services.workloadcalculator

import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory
import services.workloadcalculator.PassengerQueueTypes.{PaxType, PaxTypeAndQueueCount, VoyagePaxSplits}
import services.workloadcalculator.PaxLoadAt.PaxTypeAndQueue
import spatutorial.shared.FlightsApi.{QueueName, QueueWorkloads}
import spatutorial.shared.{ApiFlight, Pax, WL}

import scala.collection.immutable.{IndexedSeq, Seq}


object PaxLoadAt {

  case class PaxTypeAndQueue(passengerType: PaxType, queueType: String)

}

case class PaxLoadAt(time: DateTime, paxType: PaxTypeAndQueueCount)

case class SplitRatio(paxType: PaxTypeAndQueue, ratio: Double)

object PaxLoadCalculator {
  val log = LoggerFactory.getLogger(getClass)
  val paxOffFlowRate = 20

  def workload(paxLoad: PaxLoadAt): WL = {
    WL(paxLoad.time.getMillis, paxLoad.paxType.paxCount)
  }

  def queueWorkloadCalculator(splitsRatioProvider: ApiFlight => List[SplitRatio])(flights: List[ApiFlight]): Map[QueueName, QueueWorkloads] = {
    val paxLoadsByDesk: List[Map[String, IndexedSeq[PaxLoadAt]]] = paxLoadsByQueue(splitsRatioProvider, flights)
    val queueWorkloads: List[Map[String, QueueWorkloads]] = paxLoadsByDesk
      .map(m => {
        paxloadsToQueueWorkloads(m.map(e => e._1 -> e._2))
      })
    queueWorkloads.reduceLeft((agg, right) => {
      val overridden: Map[String, QueueWorkloads] = right.map { case (rightQueueName, rightQueueLoads) =>
        val currVal = agg.getOrElse(rightQueueName, (Nil, Nil))
        val combinedQueueLoads: QueueWorkloads = (combineWorkloads(currVal._1, rightQueueLoads._1), combinePaxLoads(currVal._2, rightQueueLoads._2))
        (rightQueueName -> combinedQueueLoads)
      }
      agg ++ overridden
    })
  }

  def paxLoadsByQueue(splitsRatioProvider: (ApiFlight) => List[SplitRatio], flights: List[ApiFlight]): List[Map[String, IndexedSeq[PaxLoadAt]]] = {
    val voyagePaxSplits: List[VoyagePaxSplits] = flights.map(
      voyagePaxSplitsFromApiFlight(splitsRatioProvider)
    )
    val paxLoadsByDesk: List[Map[String, IndexedSeq[PaxLoadAt]]] = voyagePaxSplits.map(vps => paxTypeAndQueueToPaxLoadAtTime(vps.scheduledArrivalDateTime, vps.paxSplits))
    paxLoadsByDesk
  }

  def voyagePaxSplitsFromApiFlight(splitsRatioProvider: (ApiFlight) => List[SplitRatio]): (ApiFlight) => VoyagePaxSplits = {
    flight => {
      val splitsOverTime: List[PaxTypeAndQueueCount] = paxDeparturesPerMinutes(flight.ActPax, paxOffFlowRate).flatMap { paxInMinute =>
        val splits = splitsRatioProvider(flight)
        splits.map(split => PaxTypeAndQueueCount(split.paxType, split.ratio * paxInMinute))
      }

      VoyagePaxSplits(flight.AirportID, flight.IATA, org.joda.time.DateTime.parse(flight.SchDT), splitsOverTime)
    }
  }

  def paxloadsToQueueWorkloads(queuePaxloads: Map[String, Seq[PaxLoadAt]]): Map[String, (Seq[WL], Seq[Pax])] = {
    queuePaxloads.map((queuePaxload: (String, Seq[PaxLoadAt])) =>

      queuePaxload._1 -> (
        queuePaxload._2.map((paxLoad: PaxLoadAt) => workload(paxLoad)),
        queuePaxload._2.map((paxLoad: PaxLoadAt) => Pax(paxLoad.time.getMillis, paxLoad.paxType.paxCount))
        )

    ).toMap
  }

  def flightPaxSplits(flight: ApiFlight, splitRatios: List[SplitRatio]): List[PaxTypeAndQueueCount] = {
    splitRatios.map(splitRatio => PaxTypeAndQueueCount(splitRatio.paxType, splitRatio.ratio * flight.ActPax))
  }

  def voyagePaxLoadByDesk(voyagePaxSplits: VoyagePaxSplits): Map[String, IndexedSeq[PaxLoadAt]] = {
    val firstMinute: DateTime = voyagePaxSplits.scheduledArrivalDateTime
    val splits: Seq[PaxTypeAndQueueCount] = voyagePaxSplits.paxSplits
    paxTypeAndQueueToPaxLoadAtTime(firstMinute, splits)
  }

  def paxTypeAndQueueToPaxLoadAtTime(firstMinute: DateTime, splits: Seq[PaxTypeAndQueueCount]): Map[String, IndexedSeq[PaxLoadAt]] = {
    val groupedByDesk: Map[String, Seq[PaxTypeAndQueueCount]] = splits.groupBy(_.paxAndQueueType.queueType)
    groupedByDesk.mapValues(
      (paxTypeAndCount: Seq[PaxTypeAndQueueCount]) => {
        //        val totalPax = paxTypeAndCount.map(_.paxCount).sum
        //        val headPaxType = paxTypeAndCount.head
        val times = firstMinute.getMillis to firstMinute.plusDays(1).getMillis by 60000L
        times.zip(paxTypeAndCount).map { case (time, paxTypeCount) => {
          val time1: DateTime = new DateTime(time, DateTimeZone.UTC)
          //          log.info(s"PaxLoad from $firstMinute for ${time1} ${voyagePaxSplits.flightCode}")
          PaxLoadAt(time1, paxTypeCount)
        }
        }
      }
    ).toMap
  }

  def paxDeparturesPerMinutes(remainingPax: Int, departRate: Int): List[Int] = {
    if (remainingPax % departRate != 0)
      List.fill(remainingPax / departRate)(departRate) ::: remainingPax % departRate :: Nil
    else
      List.fill(remainingPax / departRate)(departRate)
  }

  def combineWorkloads(l1: Seq[WL], l2: Seq[WL]): Seq[WL] = {
    def foldInto(agg: Map[Long, Double], list: List[WL]): Map[Long, Double] = list.foldLeft(agg)(
      (agg, wl) => {
        val cv = agg.getOrElse(wl.time, 0d)
        agg + (wl.time -> (cv + wl.workload))
      }
    )
    val res1 = foldInto(Map[Long, Double](), l1.toList)
    val res2 = foldInto(res1, l2.toList).map(timeWorkload => WL(timeWorkload._1, timeWorkload._2)).toList

    res2.toList
  }

  def combinePaxLoads(l1: Seq[Pax], l2: Seq[Pax]): Seq[Pax] = {
    def foldInto(agg: Map[Long, Double], list: List[Pax]): Map[Long, Double] = list.foldLeft(agg)(
      (agg, pax) => {
        val cv = agg.getOrElse(pax.time, 0d)
        agg + (pax.time -> (cv + pax.pax))
      }
    )
    val res1 = foldInto(Map[Long, Double](), l1.toList)
    val res2 = foldInto(res1, l2.toList).map(timeWorkload => Pax(timeWorkload._1, timeWorkload._2)).toList

    res2
  }

  //  def combineWorkloadsWithinAQueue(l1: List[QueueWorkloads], l2: List[QueueWorkloads]) = {
  //    def foldInto(agg: Map[String, QueueWorkloads], list: List[QueueWorkloads]) = list.foldLeft(agg)(
  //      (agg, qw) => {
  //        val cv = agg.getOrElse(qw.queueName, (Nil, Nil))
  //        agg + (
  //          qw.queueName ->
  //            (   combineWorkloads(cv.workloadsByMinute, qw.workloadsByMinute),
  //              combinePaxLoads(cv.paxByMinute, qw.paxByMinute).toList
  //            )
  //          )
  //      }
  //    )
  //    val res1 = foldInto(Map[String, QueueWorkloads](), l1)
  //    val res2 = foldInto(res1, l2).map(qw => qw._2)
  //    res2
  //  }
}

object PassengerQueueTypes {

  sealed trait PaxType {
    def name = getClass.getName
  }

  object Queues {
    val eeaDesk = "eeaDesk"
    val eGate = "eGate"
    val nonEeaDesk = "nonEeaDesk"
  }

  object PaxTypes {

    case object eeaNonMachineReadable extends PaxType

    case object visaNational extends PaxType

    case object eeaMachineReadable extends PaxType

    case object nonVisaNational extends PaxType

  }

  val eGatePercentage = 0.6

  type FlightCode = String

  case class VoyagePaxSplits(destinationPort: String, flightCode: FlightCode, scheduledArrivalDateTime: DateTime, paxSplits: Seq[PaxTypeAndQueueCount])

  case class VoyagesPaxSplits(voyageSplits: List[VoyagePaxSplits])

  case class PaxTypeAndQueueCount(paxAndQueueType: PaxTypeAndQueue, paxCount: Double)

}

