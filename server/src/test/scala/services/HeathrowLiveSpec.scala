package services

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.specs2.mutable.SpecificationLike
import akka.actor._
import spray.http._
import spray.client.pipelining._
import spray.io.ClientSSLEngineProvider

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


class HeathrowLiveSpec extends SpecificationLike{

  "I should be able to log in to the heathrow live feed" >> {

    implicit val myEngineProvider = ClientSSLEngineProvider { engine =>
      engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_RC4_128_SHA"))
      engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
      engine
    }

    // Start an Akka Actor System
    // In a real-life webapp, you would use only one, share it everywhere,
    // and call actorSystem.shutdown() when you're done
    implicit val actorSystem = ActorSystem()
    import actorSystem.dispatcher

    val pipeline = sendReceive
    val response: Future[HttpResponse] = pipeline(
      // Building the request
      Get(
        Uri(
//          "https://ukbf-api.magairports.com:9010/chroma/token"
          "https://gateway.baa.com"
        )
      )
    )
//      .map { response =>
//        // Treating the response
//        if (response.status.isFailure) {
//          sys.error(s"Received unexpected status ${response.status} : ${response.entity.asString(HttpCharsets.`UTF-8`)}")
//        }
//        println(s"OK, received ${response.entity.asString(HttpCharsets.`UTF-8`)}")
//        println(s"The response header Content-Length was ${response.header[HttpHeaders.`Content-Length`]}")
//      }

    val res = Await.result(response, 5 second)
    println(s"blah blah blah $res")
    true
  }
}
