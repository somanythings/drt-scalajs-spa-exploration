package feeds

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike

class LGWFeedSpec extends TestKit(ActorSystem("testActorSystem", ConfigFactory.empty())) with SpecificationLike {
  val xxx = ""
  val yyy = ""
  val tokenScope = s"http://${xxx}.servicebus.windows.net/partners/${yyy}/to"
  val httpPostUri = s"https://${xxx}-sb.accesscontrol.windows.net/v2/OAuth2-13"
  val acsTokenServiceGrant = "urn:oasis:names:tc:SAML:2.0:assertion"

  "something" should {
    "do something" in {
    }
  }
}
