package feeds

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike
import org.pac4j.saml.client._

class LGWFeedSpec extends TestKit(ActorSystem("testActorSystem", ConfigFactory.empty())) with SpecificationLike {
  val xxx = ""
  val yyy = ""
  val tokenScope = s"http://${xxx}.servicebus.windows.net/partners/${yyy}/to"
  val httpPostUri = s"https://${xxx}-sb.accesscontrol.windows.net/v2/OAuth2-13"
  val acsTokenServiceGrant = "urn:oasis:names:tc:SAML:2.0:assertion"

  //http://stackoverflow.com/questions/11952274/how-can-i-create-keystore-from-an-existing-certificate-abc-crt-and-abc-key-fil


  "something" should {
    "do something" in {
      // SAML
      val cfg = new SAML2ClientConfiguration("resource:/tmp/samlKeystore.jks", "pac4j-demo-passwd", "pac4j-demo-passwd", "resource:openidp-feide.xml")
      cfg.setMaximumAuthenticationLifetime(3600)
      cfg.setServiceProviderEntityId("urn:mace:saml:pac4j.org")
//      cfg.setServiceProviderMetadataPath(new File("target", "sp-metadata.xml").getAbsolutePath)

      val saml2Client = new SAML2Client(cfg)
    }
  }
}
