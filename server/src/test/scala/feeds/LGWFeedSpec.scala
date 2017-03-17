package feeds


import java.io.{ByteArrayInputStream, FileInputStream}
import java.nio.file.FileSystems
import java.security.KeyFactory
import java.security.cert.{CertificateFactory, X509Certificate}
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.IOUtils
import org.joda.time.DateTime
import org.opensaml.saml2.core.Assertion
import org.opensaml.saml2.core.impl._
import org.opensaml.xml.Configuration
import org.opensaml.xml.io.{Marshaller, MarshallerFactory}
import org.opensaml.xml.security.SecurityHelper
import org.opensaml.xml.signature.Signer
import org.opensaml.xml.signature.impl.SignatureBuilder
import org.opensaml.xml.util.XMLHelper
import org.opensaml.xml.security.x509.BasicX509Credential
import org.specs2.mutable.SpecificationLike
import org.w3c.dom.Element

class LGWFeedSpec extends TestKit(ActorSystem("testActorSystem", ConfigFactory.empty())) with SpecificationLike {
  //  val tokenScope = s"http://${xxx}.servicebus.windows.net/partners/${yyy}/to"
  //  val httpPostUri = s"https://${xxx}-sb.accesscontrol.windows.net/v2/OAuth2-13"
//  val acsTokenServiceGrant = "urn:oasis:names:tc:SAML:2.0:assertion"

  //http://stackoverflow.com/questions/11952274/how-can-i-create-keystore-from-an-existing-certificate-abc-crt-and-abc-key-fil


  object CredentialsFactory {

    /**
      * Builds a BasicX509Credential using PRIVATE_KEY and CERTIFICATE
      *
      * @return a BasicX509Credential
      */
    def getSigningCredential(privateKey: Array[Byte], certificate: Array[Byte]): BasicX509Credential = {
      val credential = new BasicX509Credential

      credential.setEntityCertificate(getCertificate(certificate))
      credential.setPrivateKey(getPrivateKey(privateKey))

      credential
    }

    /**
      * Loads in a private key from file
      *
      * @return a RSAPrivateKey representing the private key bytes
      */
    def getPrivateKey(privateKey: Array[Byte]): RSAPrivateKey = {
      val keyFactory = KeyFactory.getInstance("RSA")
      val ks = new PKCS8EncodedKeySpec(privateKey)
      keyFactory.generatePrivate(ks).asInstanceOf[RSAPrivateKey]
    }

    /**
      * Loads in a certificate from file
      *
      * @return the X509 certificate from this file
      */
    def getCertificate(certificate: Array[Byte]): X509Certificate = {
      val bis = new ByteArrayInputStream(certificate)

      try {
        CertificateFactory.getInstance("X.509").generateCertificate(bis).asInstanceOf[X509Certificate]
      } finally {
        IOUtils.closeQuietly(bis)
      }
    }
  }

  def createAzureSamlAssertionAsString(privateKey: Array[Byte], certificate: Array[Byte]): String = {
    val assertion = createAzureSamlAssertion(privateKey, certificate)

    //    val factory: MarshallerFactory = Configuration.getMarshallerFactory
    //    val marshaller1: Marshaller = factory.getMarshaller(assertion)
    //    val marshall: Element = marshaller1.marshall(assertion)
    //    Signer.signObject(assertion.getSignature)

    val marshaller = new ResponseMarshaller
    val plain = marshaller.marshall(assertion)

    XMLHelper.nodeToString(plain)
  }

  def createAzureSamlAssertion(privateKey: Array[Byte], certificate: Array[Byte]): Assertion = {
    val builder: AssertionBuilder = new AssertionBuilder()
    val assertion = builder.buildObject()
    assertion.setID("_" + UUID.randomUUID().toString)
    assertion.setIssueInstant(new DateTime())

    val nameId = new NameIDBuilder().buildObject
    nameId.setValue(ConfigFactory.load.getString("feeds.gatwick.live.azure.name.id"))

    val subject = new SubjectBuilder().buildObject
    subject.setNameID(nameId)
    assertion.setSubject(subject)

    val subjectConfirmation = new SubjectConfirmationBuilder().buildObject
    subjectConfirmation.setMethod("urn:oasis:names:tc:SAML:2.0:cm:bearer")
    subject.getSubjectConfirmations.add(subjectConfirmation)

    val audience = new AudienceBuilder().buildObject
    audience.setAudienceURI("https://" + ConfigFactory.load.getString("feeds.gatwick.live.azure.namespace") + "-sb.accesscontrol.windows.net")

    val audienceRestriction = new AudienceRestrictionBuilder().buildObject
    audienceRestriction.getAudiences.add(audience)

    val conditions = new ConditionsBuilder().buildObject
    conditions.getConditions.add(audienceRestriction)
    assertion.setConditions(conditions)

    val issuer = new IssuerBuilder().buildObject
    issuer.setValue(ConfigFactory.load.getString("feeds.gatwick.live.azure.issuer"))
    assertion.setIssuer(issuer)

    signAssertion(assertion, privateKey, certificate)

    assertion
  }

  def signAssertion(assertion: Assertion, privateKey: Array[Byte], certificate: Array[Byte]) {
    val signature = new SignatureBuilder().buildObject
    val signingCredential = CredentialsFactory.getSigningCredential(privateKey, certificate)
    signature.setSigningCredential(signingCredential)
    val secConfig = Configuration.getGlobalSecurityConfiguration
    SecurityHelper.prepareSignatureParams(signature, signingCredential, secConfig, null)
    assertion.setSignature(signature)
  }

  "something" should {
    "do something" in {
      val certificateURI = FileSystems.getDefault.getPath("/tmp/drt-lgw.cert")

      if (!certificateURI.toFile.canRead) {
        throw new Exception(s"Could not read Gatwick certificate file from /tmp/drt-lgw.cert")
      }

      val privateKeyURI = FileSystems.getDefault.getPath("/tmp/drt-lgw.pem")

      if (!privateKeyURI.toFile.canRead) {
        throw new Exception(s"Could not read Gatwick private key file from /tmp/drt-lgw.pem")
      }

      val pkInputStream = new FileInputStream(privateKeyURI.toFile)
      val certInputStream = new FileInputStream(certificateURI.toFile)

      val privateKey = IOUtils.toByteArray(pkInputStream)
      val certificate = IOUtils.toByteArray(certInputStream)

      println(s"privateKey: $privateKey")
      println(s"certificate: $certificate")

      val assertion = createAzureSamlAssertionAsString(privateKey, certificate)

      assertion === "yeah"
    }
  }
}
