package spatutorial.client.services

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, ScalaJSDefined}
import scala.util.{Failure, Success, Try}
import spatutorial.client.services.HandyStuff.{CrunchResultAndDeskRecs, QueueUserDeskRecs}
import autowire._
import boopickle.Default._
import diode._
import diode.data._
import diode.react.ReactConnector
import diode.util._
import spatutorial.client.components.DeskRecsChart
import spatutorial.client.logger._
import spatutorial.shared._
import spatutorial.shared.FlightsApi._

@JSExport
@ScalaJSDefined
class DeskRecTimeslot(val id: String, val deskRec: Int) extends js.Object {
  override def toString = s"DeskRecTimeSlot(${id}, ${deskRec})"
}

object DeskRecTimeslot {
  def apply(id: String, deskRec: Int) = new DeskRecTimeslot(id, deskRec)
}

// Actions
case object RefreshTodos extends Action

case class UpdateQueueUserDeskRecs(queueName: QueueName, todos: Seq[DeskRecTimeslot]) extends Action

case class UpdateDeskRecsTime(queueName: QueueName, item: DeskRecTimeslot) extends Action

case class DeleteTodo(item: DeskRecTimeslot) extends Action

case class UpdateMotd(potResult: Pot[String] = Empty) extends PotAction[String, UpdateMotd] {
  override def next(value: Pot[String]) = UpdateMotd(value)
}

case class UpdateCrunchResult(queueName: QueueName, crunchResult: CrunchResult) extends Action

case class UpdateSimulationResult(queueName: QueueName, simulationResult: SimulationResult) extends Action

case class UpdateWorkloads(workloads: Map[TerminalName, Map[QueueName, QueueWorkloads]]) extends Action

case class Crunch(terminal: TerminalName, queue: QueueName, workload: List[Double]) extends Action

case class GetWorkloads(begin: String, end: String, port: String) extends Action

case class RunSimulation(queueName: QueueName, workloads: List[Double], desks: List[Int]) extends Action

case class ChangeDeskUsage(queueName: QueueName, value: String, index: Int) extends Action

case class ProcessWork(desks: Seq[Double], workload: Seq[Double]) extends Action

trait WorkloadsUtil {
  def labelsFromAllQueues(workloads: Map[String, QueueWorkloads]) = {
    val timesMin = workloads.values.flatMap(_._1.map(_.time)).min
    val oneMinute: Long = 60000
    val allMins = timesMin until (timesMin + 60000 * 60 * 24) by oneMinute
    allMins.map(new js.Date(_).toISOString())
  }

  def timeStampsFromAllQueues(workloads: Map[String, QueueWorkloads]) = {
    val timesMin = workloads.values.flatMap(_._1.map(_.time)).min
    val oneMinute: Long = 60000
    val allMins = timesMin until (timesMin + 60000 * 60 * 24) by oneMinute
    allMins
  }
}

// The base model of our application
case class Workloads(workloads: Map[TerminalName, Map[QueueName, QueueWorkloads]]) extends WorkloadsUtil {
  def labels = labelsFromAllQueues(t1workload)

  def timeStamps = timeStampsFromAllQueues(t1workload)

  private def t1workload = { workloads("T1") }
}

case class RootModel(
  //todos: Pot[UserDeskRecs],
  motd: Pot[String] = Empty,
  workload: Pot[Workloads] = Empty,
  queueCrunchResults: Map[QueueName, Pot[CrunchResultAndDeskRecs]] = Map(),
  userDeskRec: QueueUserDeskRecs = Map(),
  simulationResult: Map[QueueName, Pot[SimulationResult]] = Map(),
  flights: Pot[Flights] = Empty,
  airportInfos: Map[String, Pot[AirportInfo]] = Map()
)

case class UserDeskRecs(items: Seq[DeskRecTimeslot]) {
  def updated(newItem: DeskRecTimeslot) = {
    log.info(s"will update ${newItem} into ${items.take(5)}...")
    items.indexWhere(_.id == newItem.id) match {
      case -1 =>
        // add new
        log.info("add new")
        UserDeskRecs(items :+ newItem)
      case idx =>
        log.info("add old")
        // replace old
        UserDeskRecs(items.updated(idx, newItem))
    }
  }
}

/**
 * Handles actions related to todos
 *
 * @param modelRW Reader/Writer to access the model
 */
class DeskTimesHandler[M](modelRW: ModelRW[M, QueueUserDeskRecs]) extends ActionHandler(modelRW) {
  override def handle = {
    case RefreshTodos =>
      log.info("RefreshTodos")
      //      effectOnly(Effect(AjaxClient[Api].geAllTodos().call().map(UpdateAllTodos)))
      noChange
    case UpdateQueueUserDeskRecs(queueName, deskRecs) =>
      // got new deskRecs, update model
      log.info(s"got new user desk recs update model for $queueName")
      updated(value + (queueName -> Ready(UserDeskRecs(deskRecs))))
    case UpdateDeskRecsTime(queueName, item) =>
      log.info(s"Update Desk Recs time ${item} into ${value}")
      // make a local update and inform server
      val newDesksPot: Pot[UserDeskRecs] = value(queueName).map(_.updated(item))
      updated(value + (queueName -> newDesksPot), Effect(Future(RunSimulation(queueName, Nil, newDesksPot.get.items.map(_.deskRec).toList)))) //, Effect(AjaxClient[Api].updateDeskRecsTime(item).call().map(UpdateAllTodos)))
  }
}

abstract class LoggingActionHandler[M, T](modelRW: ModelRW[M, T]) extends ActionHandler(modelRW) {
  override def handleAction(model: M, action: Any): Option[ActionResult[M]] = {
    val triedHandler = Try(super.handleAction(model, action))
    triedHandler match {
      case Failure(f) =>
        log.error("Exception from ${getClass}" + f.toString())
      case _ =>
    }
    triedHandler match {
      case Success(s) => s
    }
  }
}
/**
 * Handles actions related to the Motd
 *
 * @param modelRW Reader/Writer to access the model
 */
class MotdHandler[M](modelRW: ModelRW[M, Pot[String]]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle = {
    case action: UpdateMotd =>
      val updateF = action.effect(AjaxClient[Api].welcomeMsg("User X").call())(identity _)
      action.handleWith(this, updateF)(PotAction.handler())
  }
}

class WorkloadHandler[M](modelRW: ModelRW[M, Pot[Workloads]]) extends LoggingActionHandler(modelRW) {
  protected def handle = {
    case action: GetWorkloads =>
      log.info("requesting workloadsWrapper from server")
      updated(Pending(), Effect(AjaxClient[Api].getWorkloads().call().map(UpdateWorkloads)))

    case UpdateWorkloads(terminalQueueWorkloads) =>
      log.info(s"received workloads ${terminalQueueWorkloads} from server")
      val trytqes = terminalQueueWorkloads.flatMap {
        case (terminalName, queueWorkloads) =>
            log.info(s" $terminalName, $queueWorkloads flatmapping")
            val workloadsByQueue = WorkloadsHelpers.workloadsByQueue(queueWorkloads)
                log.info(s"workloadsByQueue ${workloadsByQueue}")
                val effects = workloadsByQueue.map {
                  case (queueName, queueWorkload) =>
                    val effect = Effect(AjaxClient[Api].crunch(terminalName, queueName, queueWorkload).call().map(resp => {
                      log.info(s"will request crunch for ${queueName}")
                      UpdateCrunchResult(queueName, resp)
                    }))
                    effect
                }
                effects
      }

          log.info(s"have grouped stuff ${trytqes}")
          val effects = trytqes.toList
          val effectsAsEffectSeq = new EffectSet(effects.head, effects.tail.toSet, queue)
          updated(Ready(Workloads(terminalQueueWorkloads)), effectsAsEffectSeq)
  }
}

object HandyStuff {
  type CrunchResultAndDeskRecs = (Pot[CrunchResult], Pot[UserDeskRecs])
  type QueueUserDeskRecs = Map[String, Pot[UserDeskRecs]]
}

class SimulationHandler[M](modelR: ModelR[M, Pot[Workloads]], modelRW: ModelRW[M, QueueUserDeskRecs])
  extends ActionHandler(modelRW) {
  protected def handle = {
    case RunSimulation(queueName, workloads, desks) =>
      log.info(s"Requesting simulation for ${queueName}")
      //todo add terminal to RunSimulation
      val workloads1: List[Double] = WorkloadsHelpers.workloadsByQueue(modelR.value.get.workloads("T1"))(queueName)
      //      queueWorkloadsToFullyPopulatedDoublesList(modelR.value.get.workloads)
      log.info(s"Got workloads from model for ${queueName} desks: ${desks.take(15)}... workloads: ${workloads1.take(15)}...")
      effectOnly(
        Effect(AjaxClient[Api].processWork(workloads1, desks).call().map(resp => UpdateSimulationResult(queueName, resp)))
      )
    case ChangeDeskUsage(queueName, v, k) =>
      log.info(s"Handler: ChangeDesk($queueName, $v, $k)")
      val simModel: ModelRW[M, QueueUserDeskRecs] = modelRW
      val model: Pot[UserDeskRecs] = simModel.value(queueName)
      val newUserRecs: UserDeskRecs = model.get.updated(DeskRecTimeslot(k.toString, v.toInt))
      updated(value + (queueName -> Ready(newUserRecs)))
  }
}

class SimulationResultHandler[M](modelRW: ModelRW[M, Map[QueueName, Pot[SimulationResult]]]) extends ActionHandler(modelRW) {
  protected def handle = {
    case UpdateSimulationResult(queueName, simResult) =>
      log.info(s"Got simulation result $queueName ${simResult.waitTimes}")
      updated(value + (queueName -> Ready(simResult)))
  }
}

case class RequestFlights(from: Long, to: Long) extends Action

case class UpdateFlights(flights: Flights) extends Action

class FlightsHandler[M](modelRW: ModelRW[M, Pot[Flights]]) extends ActionHandler(modelRW) {
  protected def handle = {
    case RequestFlights(from, to) =>
      log.info(s"requeusting flights $from $to")
      effectOnly(Effect(AjaxClient[Api].flights(from, to).call().map(UpdateFlights)))
    case UpdateFlights(flights) =>
      log.info(s"Client got flights! ${flights.flights.length}")
      val airportSubs: List[EffectSingle[GetAirportInfo]] = flights.flights.map(f => Effect(Future(GetAirportInfo(f.Origin))))
      updated(Ready(flights), new EffectSeq(airportSubs.head, airportSubs.tail, queue))
  }
}

class CrunchHandler[M](modelRW: ModelRW[M, (QueueUserDeskRecs, Map[QueueName, Pot[CrunchResultAndDeskRecs]])])
  extends LoggingActionHandler(modelRW) {

  override def handle = {
    case Crunch(terminalName, queueName, workload) =>
      log.info(s"Requesting Crunch $terminalName, $queueName,  with ${workload}")
      updated(
        value.copy(_2 = value._2 + (queueName -> Pending())),
        Effect(AjaxClient[Api].crunch(terminalName, queueName, workload).call().map(serverResult => UpdateCrunchResult(queueName, serverResult))))
    case UpdateCrunchResult(queueName, crunchResult) =>
      log.info(s"UpdateCrunchResult $queueName")
      //todo zip with labels?, or, probably better, get these prepoluated from the server response?
      val newDeskRec: UserDeskRecs = UserDeskRecs(DeskRecsChart
        .takeEvery15th(crunchResult.recommendedDesks)
        .zipWithIndex.map(t => DeskRecTimeslot(t._2.toString, t._1)))

      updated(value.copy(
        _1 = value._1 + (queueName -> Ready(newDeskRec)),
        _2 = value._2 + (queueName -> Ready((Ready(crunchResult), Ready(newDeskRec))))
      ))
    //        Effect(AjaxClient[Api].setDeskRecsTime(newDeskRec.items.toList).call().map(res => UpdateQueueUserDeskRecs(queueName, res))))
  }

}

class AirportCountryHandler[M](modelRW: ModelRW[M, Map[String, Pot[AirportInfo]]]) extends ActionHandler(modelRW) {
  override def handle = {
    case GetAirportInfo(code) =>
      value.get(code) match {
        case None =>
          val stringToObject = value + (code -> Empty)
          log.info(s"sending request for info for ${code}")
          updated(stringToObject, Effect(AjaxClient[Api].airportInfoByAirportCode(code).call().map(res => UpdateAirportInfo(code, res))))
        case Some(v) =>
          noChange
      }
    case UpdateAirportInfo(code, Some(airportInfo)) =>
      val newValue = value + ((code -> Ready(airportInfo)))
      log.info(s"got a new value for ${code} ${airportInfo}")
      updated(newValue)
  }
}

// Application circuit
object SPACircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  val blockWidth = 15

  // initial application model
  override protected def initialModel = RootModel()

  // combine all handlers into one
  override val actionHandler = {
    println("composing handlers")
    composeHandlers(
      new DeskTimesHandler(zoomRW(_.userDeskRec)((m, v) => m.copy(userDeskRec = v))),
      new MotdHandler(zoomRW(_.motd)((m, v) => m.copy(motd = v))),
      new WorkloadHandler(zoomRW(_.workload)((m, v) => {
        log.info(s"Updateing workloads to $v")
        m.copy(workload = v)
      })),
      new CrunchHandler(zoomRW(m => (m.userDeskRec, m.queueCrunchResults))((m, v) => {
        log.info(s"setting crunch result and userdesk recs desks in model ${v}")
        m.copy(
          userDeskRec = v._1,
          queueCrunchResults = v._2
        )
      })),
      new SimulationHandler(zoom(_.workload), zoomRW(m => m.userDeskRec)((m, v) => {
        log.info("setting simulation result in model")
        m.copy(userDeskRec = v)
      })),
      new SimulationResultHandler(zoomRW(_.simulationResult)((m, v) => m.copy(simulationResult = v))),
      new FlightsHandler(zoomRW(_.flights)((m, v) => m.copy(flights = v))),
      new AirportCountryHandler(zoomRW(_.airportInfos)((m, v) => m.copy(airportInfos = v)))
    )
  }

}

case class GetAirportInfo(code: String) extends Action

case class UpdateAirportInfo(code: String, info: Option[AirportInfo]) extends Action
