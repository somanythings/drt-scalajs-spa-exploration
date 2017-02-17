package services

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import akka.util.ByteString
import boopickle.Default.Pickle
import controllers.{Application, AutowireStuff}
import org.specs2.mutable.{Specification, SpecificationLike}
import play.api.libs.streams.Accumulator
import play.api.mvc.{Action, RawBuffer, Request, Result}
import play.api.test.FakeRequest
import spatutorial.shared.{AirportConfig, AirportConfigs, ApiFlight}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import play.api.mvc._
import play.api.test._

import scala.concurrent.Future
import scala.concurrent.duration._

class AutowireRoutesSpec extends TestKit(ActorSystem("testActorSystem")) with Results with SpecificationLike {

  import boopickle.Default._

  import boopickle.PickleState._


//  "We should be able to call spatutorial/shared/Api/flights" in {
//
//    val flights: AutowireStuff.FlightsProvider = (i, j) => Future {
//      Nil
//    }
//    val apiService = AutowireStuff.createApiService(AirportConfigs.edi,
//      system, flights, Nil,
//      testActor,
//      testActor)
//
//    val autowiredApi: (String) => Action[RawBuffer] = AutowireStuff.autowireApi(apiService)
//
//    def autowireApi(path: String) = autowiredApi(path)
//
//    autowireApi("spatutorial/shared/Api/flights") === "awesome"
//
//  }
  "We expect this to fail" in {
    implicit val mat = ActorMaterializer()
    val flights: AutowireStuff.FlightsProvider = (i, j) => Future {
      Nil
    }
    val apiService = AutowireStuff.createApiService(AirportConfigs.edi,
      system, flights, Nil,
      testActor,
      testActor)

    val autowiredApi: (String) => Action[RawBuffer] = AutowireStuff.autowireApi(apiService)

//    val bb =  ByteBuffer.wrap("".getBytes)

    val bb = ByteString(2, 14, 115, 116, 97, 114, 116, 84, 105, 109, 101, 69, 112, 111, 99, 104, 2, 0, 12, 101, 110, 100, 84, 105, 109, 101, 69, 112, 111, 99, 104, 2, 0).toByteBuffer
//    val emptyBody = Pickle.intoBytes(Map[String, ByteBuffer]("abc" -> bb))

    def autowireApi(path: String) = autowiredApi(path)(FakeRequest("POST", path,
      headers = FakeHeaders(),
      body = bb ))

    val result: Accumulator[ByteString, Result] = autowireApi("spatutorial/shared/Api/flights")
    result map {
      r =>
        println("jalala")
        println(r.header)
        println(r)
    }

    result.recover{
      case t =>
        println("insanity")
        println(t)
    }

    val res = Await.result(result.run(), FiniteDuration(40, SECONDS))
    assert(res === "123")
    false
    //    === "hello"

  }

//  "We expect this to fail" in {
//    implicit val mat = ActorMaterializer()
//    val flights: AutowireStuff.FlightsProvider = (i, j) => Future {
//      Nil
//    }
//    val apiService = AutowireStuff.createApiService(AirportConfigs.edi,
//      system, flights, Nil,
//      testActor,
//      testActor)
//
//    val autowiredApi: (String) => Action[RawBuffer] = AutowireStuff.autowireApi(apiService)
//    val emptyBody = Pickle.intoBytes(Map())
//
//    def autowireApi(path: String) = autowiredApi(path)(FakeRequest("POST", path,
//      headers = FakeHeaders(),
//      body = emptyBody ))
//
//    println("hahahaha")
//    val result: Accumulator[ByteString, Result] = autowireApi("spatutorial/shared/Api/flights")
//    result map {
//      r =>
//        println("monono")
//        println(r.header)
//        println(r)
//    }
//    result.recover{
//      case t =>
//        println("insanity")
//        println(t)
//    }
//
//    Await.result(result.run(), FiniteDuration(40, SECONDS))
//    false
//    //    === "hello"
//
//  }

  //  "Through the controller" in {
  //
  //    val flights: AutowireStuff.FlightsProvider = (i, j) => Future {
  //      Nil
  //    }
  //    val apiService = AutowireStuff.createApiService(AirportConfigs.edi,
  //      system, flights, Nil,
  //      testActor,
  //      testActor)
  //
  //    val autowiredApi: (String) => Action[RawBuffer] = AutowireStuff.autowireApi(apiService)
  //
  //    def autowireApi(path: String) = autowiredApi(path)(FakeRequest("GET", path))
  //
  //
  //    val application = new Application()
  //
  //    val path = "/do/not/exist"
  //    val r1: Action[RawBuffer] = application.autowiredApi(path)
  //    val res: Accumulator[ByteString, Result] = r1(FakeRequest("GET", path))
  //    res map { r => println(r)}
  //    false
  ////    contentAsString(res) === "hello"
  //    //    val result:result Accumulator[ByteString, Result] = autowireApi("do/not/exist")
  //    //    === "hello"
  //
  //  }
}
