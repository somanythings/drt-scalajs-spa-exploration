package spatutorial.client.components

import diode.data.Pot
import diode.react.{ReactConnectProxy, ModelProxy}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.{Resolution, RouterCtl}
import japgolly.scalajs.react.vdom.prefix_<^._
import spatutorial.client.SPAMain._
import spatutorial.client.services.SPACircuit
import spatutorial.shared.{ApiFlight, FlightChange, AirportConfig}
import spatutorial.client.logger._
import scala.scalajs.js

object Layout {

  case class Props(ctl: RouterCtl[Loc], currentLoc: Resolution[Loc])

  val component = ReactComponentB[Props]("Layout")
    .renderP((_, props: Props) => {
      val airportConfigRCP = SPACircuit.connect(m => m.airportConfig)
      val flightChanges: ReactConnectProxy[Vector[FlightChange]] = SPACircuit.connect(m => m.flightChanges)
      airportConfigRCP((airportConfigPotMP: ModelProxy[Pot[AirportConfig]]) => {
        <.div(
          airportConfigPotMP().renderReady(airportConfig =>
            <.div(
              // here we use plain Bootstrap class names as these are specific to the top level layout defined here
              <.nav(^.className := "navbar navbar-inverse navbar-fixed-top",
                <.div(^.className := "container",
                  <.div(^.className := "navbar-header", <.span(^.className := "navbar-brand", s"DRT ${airportConfigPotMP.value.get.portCode} Live Spike")),
                  <.div(^.className := "collapse navbar-collapse", MainMenu(props.ctl, props.currentLoc.page)))),
              // currently active module is shown in this container
              <.div(^.className := "container", props.currentLoc.render()))),
          airportConfigPotMP().renderPending(_ => "Waiting for Airport Config"),
          airportConfigPotMP().renderEmpty(" Didn't get Airport Config"),
          flightChangeDiv(flightChanges)
        )
      })
    })
    .build

  def flightChangeDiv(flightChanges: ReactConnectProxy[Vector[FlightChange]]) = {
    <.div(^.className := "flight-changes",
      flightChanges(flightChangesMp =>
        <.div(
          <.h2("Flight Changes"),
          flightChangesMp.value.map((f: FlightChange) => {
            <.div(^.key := f.hashCode(), f match {
              case FlightChange(changeType, changeTime, oldf, Some(newf)) =>
                val time = new js.Date(changeTime)
                <.div("Change: ",
                  <.p(s"At: ${time.getFullYear()}/${time.getMonth()}/${time.getDate()}T${time.getHours()}:${time.getMinutes()}"),
                  <.p("Was: " + oldf.toString()), <.p("Now: " + newf.toString()))
              case default =>
                default.toString()
            })
          }).toList)))
  }

  lazy val notificationWrapper = ReactToastrWrapper.C
  def flightChangeToastr(flightChanges: ReactConnectProxy[Vector[FlightChange]]) = {
    <.div(notificationWrapper())
  }

  def apply(ctl: RouterCtl[Loc], currentLoc: Resolution[Loc]): ReactElement = component(Props(ctl, currentLoc))
}

trait ReactToastrM extends js.Object {
  def success(title: String, body: String) = js.native
}

case class ReactToastrWrapper(ref: String) {
  val reacttoastr: js.Dynamic = js.Dynamic.global.Bundle.reacttoastr
  val ToastMessageFactory = React.asInstanceOf[js.Dynamic].createFactory(reacttoastr.ToastMessage.animation)

  def toJs = {
    val p = js.Dynamic.literal()
    p.updateDynamic("ref")(ref) // it looks like there's a safer way to do this.
    p.updateDynamic("toastMessageFactory")(ToastMessageFactory)
    p.updateDynamic("className")("toast-top-right")
    p
  }

  val jsWrapper = toJs


  def apply(children: ReactNode*) = {
    val f = React.asInstanceOf[js.Dynamic].createFactory(reacttoastr)
    f(jsWrapper).asInstanceOf[ReactComponentU_]
  }

  def addAlert() = {
    log.info(s"Trying to add alert")
    log.info(s"jsWrapper ${jsWrapper}")
    log.info(s"refs container: ${jsWrapper.refs.container}")
    jsWrapper.refs.container.success(
      "Hello there!!!!!",
      "Body of changes!", js.Dynamic.literal(
        timeOut = 30000,
        extendedTimeOut = 10000
      ))
  }
}

object ReactToastrWrapper {
  var ref: js.UndefOr[ReactToastrM] = null
  class RB(t: BackendScope[_,_]) {
    def test = {
      val prref = Ref.toJS[ReactToastrM]("toastrwrapper")(t)
      ref = prref
      if (ref.isDefined) ref.get.success("Hello", "fromRB")
    }
  }
  val C = ReactComponentB[Unit]("RefsToToast")
    .stateless
    .backend(new RB(_))
    .render((P) => {
      <.div(
       ReactToastrWrapper(ref = "toastrwrapper")()
      )
    })
    .build
}