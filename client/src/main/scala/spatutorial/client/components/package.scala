package drt.client

import diode.data.Pot
import drt.client.services.JSDateConversions.SDate

import scala.util.{Success, Try}

package object components {
  import diode.react.{ModelProxy, ReactConnectProxy, ReactPot}
  import ReactPot._
  // expose jQuery under a more familiar name
  val jQuery = JQueryStatic
  import scala.language.implicitConversions

  implicit def potReactForwarder[A](a: Pot[A]): potWithReact[A] = ReactPot.potWithReact(a)
  def makeDTReadable(dt: String): String = {
    if(dt != "") SDate.parse(dt).toLocalDateTimeString() else ""
  }
}
