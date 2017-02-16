package services

import akka.actor.ActorSystem
import akka.testkit.TestKit
import controllers.AutowireStuff
import org.specs2.mutable.{Specification, SpecificationLike}
import play.api.mvc.{Action, RawBuffer}
import spatutorial.shared.{AirportConfig, AirportConfigs, ApiFlight}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AutowireRoutesSpec extends TestKit(ActorSystem("testActorSystem")) with SpecificationLike {

  "We should be able to call spatutorial/shared/Api/flights" in {

    val flights: AutowireStuff.FlightsProvider = (i, j) => Future {
      Nil
    }
    val apiService = AutowireStuff.createApiService(AirportConfigs.edi,
      system, flights, Nil,
      testActor,
      testActor)

    val autowiredApi: (String) => Action[RawBuffer] = AutowireStuff.autowireApi(apiService)

    def autowireApi(path: String) = autowiredApi(path)

    autowireApi("spatutorial/shared/Api/flights")
    false
  }
}
