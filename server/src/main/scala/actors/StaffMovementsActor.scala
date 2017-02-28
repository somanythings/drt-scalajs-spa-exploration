package actors

import java.io.InvalidClassException
import java.util.UUID

import akka.actor.ActorLogging
import akka.persistence._
import spatutorial.shared.FlightsApi.{QueueName, TerminalName}
import spatutorial.shared.{MilliDate, StaffMovement}

import scala.collection.immutable.Seq

case class StaffMovements(staffMovements: Seq[StaffMovement])

case class SerializableStaffMovements(staffMovements: Seq[SerializableStaffMovement]) {
  def toStaffMovements() = StaffMovements(staffMovements.map(_.toStaffMovement))

}

object SerializableStaffMovements {
  def apply(staffMovements: StaffMovements): SerializableStaffMovements = {
    SerializableStaffMovements(staffMovements.staffMovements.map(sm => SerializableStaffMovement1(sm)))
  }
}

sealed trait SerializableStaffMovement {
  def toStaffMovement: StaffMovement
}
case class SerializableStaffMovement1(reason: String, time: MilliDate, delta: Int, uUID: UUID, queue: Option[QueueName] = None) extends SerializableStaffMovement {
  def toStaffMovement() = StaffMovement(reason, time, delta, uUID, queue)

}
//case class SerializableStaffMovement2(terminalName: TerminalName = "", reason: String, time: MilliDate, delta: Int, uUID: UUID, queue: Option[QueueName] = None) extends SerializableStaffMovement

//object SerializableStaffMovement2 {
//  def apply(staffMovement: StaffMovement): SerializableStaffMovement2 = {
//    SerializableStaffMovement2(staffMovement.terminalName, staffMovement.reason, staffMovement.time, staffMovement.delta, staffMovement.uUID, staffMovement.queue)
//  }
//}

object SerializableStaffMovement1 {
  def apply(staffMovement: StaffMovement): SerializableStaffMovement1 = {
    SerializableStaffMovement1(staffMovement.reason, staffMovement.time, staffMovement.delta, staffMovement.uUID, staffMovement.queue)
  }
}

case class StaffMovementsState(events: List[StaffMovements] = Nil) {
  def updated(data: StaffMovements): StaffMovementsState = copy(data :: events)

  def size: Int = events.length

  override def toString: String = events.reverse.toString
}

class StaffMovementsActor extends PersistentActor with ActorLogging {

  override def persistenceId = "staff-movements-store"

  var state = StaffMovementsState()

  def updateState(data: StaffMovements): Unit = {
    state = state.updated(data)
  }

  val receiveRecover: Receive = {
    case data: StaffMovements =>

      updateState(data)
    case SnapshotOffer(_, snapshot: StaffMovementsState) => state = snapshot
  }

  val receiveCommand: Receive = {
    case GetState =>
      sender() ! state.events.headOption.getOrElse("")
    case data: StaffMovements =>
      persist(data) { staffMovements =>
        updateState(staffMovements)
        context.system.eventStream.publish(SerializableStaffMovements(staffMovements))
      }
  }

  override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
    cause match {
      case e: InvalidClassException =>
        log.error(s"Failed to recover persisted StaffMovements, invalid serialization class: ${cause.getMessage}")
    }
    super.onRecoveryFailure(cause, event)
  }
}
