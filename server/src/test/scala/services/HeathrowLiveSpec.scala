package services

import javax.net.ssl.{SSLServerSocket, SSLServerSocketFactory}

import org.specs2.mutable.{Specification, SpecificationLike}
import spray.io.ClientSSLEngineProvider

import scala.concurrent.Await
import scala.concurrent.duration._
//import scala.concurrent.{Await, Future}

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.can.Http
import spray.can.client.{ClientConnectionSettings, HostConnectorSettings}
import spray.client.pipelining._
import spray.http.{HttpRequest, _}
import scala.concurrent.ExecutionContext.Implicits.global

class HeathrowLiveSpec extends SpecificationLike {

  "I should be able to log in to the heathrow live feed" >> {


    // Start an Akka Actor System
    // In a real-life webapp, you would use only one, share it everywhere,
    // and call actorSystem.shutdown() when you're done
    //    implicit val actorSystem = ActorSystem()
    //    import actorSystem.dispatcher
    implicit val myEngineProvider: ClientSSLEngineProvider = ClientSSLEngineProvider { engine =>
      engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
      engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_RC4_128_SHA"))
      engine
    }
    //

    object baa {


      val host = "gateway.baa.com"
      val port = 443
    }
    object chromastn {
      val host = "ukbf-api.magairports.com"
      val port = 9010
    }

    import baa._
    //
    //
    //    val pipeline = sendReceive
    //    val request: HttpRequest = Get(
    //      Uri(
    //        //          "https://ukbf-api.magairports.com:9010/chroma/token"
    //        "https://gateway.baa.com"
    //      )
    //    )

    //    val conn: HostConnectorSetup = Http.HostConnectorSetup(host, port, true)

    //    val response: Future[HttpResponse] = pipeline()
    //      .map { response =>
    //        // Treating the response
    //        if (response.status.isFailure) {
    //          sys.error(s"Received unexpected status ${response.status} : ${response.entity.asString(HttpCharsets.`UTF-8`)}")
    //        }
    //        println(s"OK, received ${response.entity.asString(HttpCharsets.`UTF-8`)}")
    //        println(s"The response header Content-Length was ${response.header[HttpHeaders.`Content-Length`]}")
    //      }

    implicit val actorSystem = ActorSystem("testSystem")
    //    val host = "some host name"
    //    val port = 9000
    val endpoint = "/"
    val simpleConfig =
      """
        spray.can {
          host-connector {
            max-connections = 500
            max-retries = 5
          }
          client {
            idle-timeout = 30 s
            connecting-timeout = 2 s
            request-timeout = 3 s
          }
        }"""
    val hostConfig = ConfigFactory.parseString(simpleConfig).withFallback(ConfigFactory.load())
    val clientSettings = ClientConnectionSettings(hostConfig)
    val hostSettings = HostConnectorSettings(hostConfig)
    val connectorSetup = Http.HostConnectorSetup(host, port, sslEncryption = true, settings = Option(hostSettings.copy(connectionSettings = clientSettings)))
    //(myEngineProvider)
    implicit val hostConnectionTimeout = Timeout(60 second)
    val maxRequestTime = (hostSettings.maxRetries + 1) * clientSettings.requestTimeout
    val maxConnectionTime = clientSettings.connectingTimeout

    val hostConnection =
      for (Http.HostConnectorInfo(connector, _) <- IO(Http) ? connectorSetup)
        yield {
          implicit val maxRequestTimeout = maxRequestTime + maxConnectionTime
          sendReceive(connector)
        }

    val numOfRequests = 2

    for (iteration <- 1 to 5) {
      println("starting iteration " + iteration)
      val requests = for (i <- 1 to numOfRequests) yield {
        val request = HttpRequest(
          method = HttpMethods.GET,
          uri = endpoint)
        hostConnection.flatMap {
          connection =>
            connection(request).map { resp =>
              println("res" + resp.toString())
            }
        }.recover {
          case e => println("error", e)
        }
      }

      // wait until requests complete
      for (req <- requests) yield Await.ready(req, maxRequestTime + maxConnectionTime)

      println("completed iteration" + iteration)
    }


    //    val res = Await.result(response, 5 second)
    //    println(s"blah blah blah $res")
    true
  }
}

class FindCiphers extends Specification {
  "find ciphers" >> {
    val ssl = SSLServerSocketFactory.getDefault().asInstanceOf[SSLServerSocketFactory]
    val sslServerSocket = ssl.createServerSocket().asInstanceOf[SSLServerSocket]

    // Get the list of all supported cipher suites.
    val cipherSuites = sslServerSocket.getSupportedCipherSuites();
    for (suite <- cipherSuites)
      println(suite)

    // Get the list of all supported protocols.
    val protocols = sslServerSocket.getSupportedProtocols();
    println(protocols.mkString("\n"))
    false
  }
}
