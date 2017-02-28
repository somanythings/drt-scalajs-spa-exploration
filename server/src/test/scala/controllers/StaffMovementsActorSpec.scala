package controllers

import java.util.UUID

import actors.{SerializableStaffMovement1, SerializableStaffMovements, StaffMovements}
import akka.util.Timeout
import org.specs2.mutable.Specification
import spatutorial.shared.{MilliDate, StaffMovement}

import scala.collection.immutable.Seq
import scala.concurrent.Await
import scala.concurrent.duration._

class StaffMovementsActorSpec extends Specification {
  sequential

  private val uuid: UUID = UUID.randomUUID()

  "StaffMovementsPersistenceApi" should {
    "allow setting and getting of staff movements" in new AkkaTestkitSpecs2Support("target/test") {


      val staffMovementsApi = new StaffMovementsPersistence {
        override implicit val timeout: Timeout = Timeout(5 seconds)

        val actorSystem = system
      }
      staffMovementsApi.saveStaffMovements(Seq(
        StaffMovement("is81", MilliDate(0L), -1, uuid)
      ))

      awaitAssert({
        val resultFuture = staffMovementsApi.getStaffMovements()
        val result = Await.result(resultFuture, 1 seconds)
        println(s"result: $result")
        assert(Seq(StaffMovement("is81", MilliDate(0L), -1, uuid)) == result)
      }, 2 seconds)
    }
  }

//  "Serialisation of StaffMovement" >> {
//    "Given a list of StaffMovement we should get back a list of SerializableStaffMovementV2" >> {
//
//      val staffMovements = Seq(StaffMovement("T1", "is81", MilliDate(0L), -1, uuid))
//
//      val result = staffMovements.map((sm: StaffMovement) => SerializableStaffMovement2(sm))
//
//      result === Seq(SerializableStaffMovement2("T1", "is81", MilliDate(0L), -1, uuid))
//    }
//  }

  "Serialisation of StaffMovement" >> {
    "Given a StaffMovements we should get back a SerializableStaffMovements" >> {

      val staffMovements = StaffMovements(Seq(StaffMovement("is81", MilliDate(0L), -1, uuid)))

      val result = SerializableStaffMovements(staffMovements)

      result === SerializableStaffMovements(Seq(SerializableStaffMovement1("is81", MilliDate(0L), -1, uuid)))
    }

    "Given a SerializableStaffMovements we should get back a StaffMovements" >> {

      val serializableStaffMovements = SerializableStaffMovements(Seq(SerializableStaffMovement1("is81", MilliDate(0L), -1, uuid)))

      val result = serializableStaffMovements.toStaffMovements

      result === StaffMovements(Seq(StaffMovement("is81", MilliDate(0L), -1, uuid)))
    }
  }
}
