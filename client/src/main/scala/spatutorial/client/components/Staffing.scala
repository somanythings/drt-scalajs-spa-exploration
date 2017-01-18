package spatutorial.client.components

import diode.data.{Empty, Pot, Ready}
import diode.react.ModelProxy
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import spatutorial.client.logger._
import spatutorial.client.services.JSDateConversions._
import spatutorial.client.services._
import spatutorial.shared.{SDate, WorkloadsHelpers}

import scala.collection.immutable.{NumericRange, Seq}
import scala.scalajs.js.Date
import scala.util.{Success, Try}

object Staffing {

  case class Props()

  class Backend($: BackendScope[Props, Unit]) {
    def render(props: Props) = {
      val shiftsRawRCP = SPACircuit.connect(_.shiftsRaw)
      shiftsRawRCP((shiftsMP: ModelProxy[Pot[String]]) => {
        val rawShifts = shiftsMP() match {
          case Ready(shifts) => shifts
          case Empty => ""
        }

        val shifts: List[Try[Shift]] = ShiftParser(rawShifts).parsedShifts.toList
        val didParseFail = shifts exists (s => s.isFailure)


        val today: SDate = SDate.today
        val todayString = today.ddMMyyString

        val shiftExamples = Seq(
          s"Midnight shift,${todayString},00:00,00:59,14",
          s"Night shift,${todayString},01:00,06:59,6",
          s"Morning shift,${todayString},07:00,13:59,25",
          s"Afternoon shift,${todayString},14:00,16:59,13",
          s"Evening shift,${todayString},17:00,23:59,20"
        )
        <.div(
          <.h1("Staffing"),
          <.h2("Shifts"),
          <.div("One shift per line with values separated by commas, e.g.:"),
          <.div(shiftExamples.map(<.p(_))),
          <.textarea(^.value := rawShifts,
            ^.className := "staffing-editor",
            ^.onChange ==> ((e: ReactEventI) => shiftsMP.dispatch(UpdateShifts(e.target.value)))),
          <.h2("Staff over the day"), if (didParseFail) {
            <.div(^.className := "error", "Error in shifts")
          }
          else {
            val successfulShifts: List[Shift] = shifts.collect { case Success(s) => s }
            val ss = ShiftService(successfulShifts)

            staffingTableHourPerColumn(daysWorthOf15Minutes(today), ss)
          }
        )
      })
    }
  }

  def daysWorthOf15Minutes(startOfDay: SDate): NumericRange[Long] = {
    val timeMinPlusOneDay = startOfDay.addDays(1)
    val daysWorthOf15Minutes = startOfDay.millisSinceEpoch until timeMinPlusOneDay.millisSinceEpoch by (WorkloadsHelpers.oneMinute * 15)
    daysWorthOf15Minutes
  }

  def staffingTableHourPerColumn(daysWorthOf15Minutes: NumericRange[Long], ss: ShiftService) = {
    <.table(
      ^.className := "table table-striped table-xcondensed table-sm",
      <.tbody(
        daysWorthOf15Minutes.grouped(16).flatMap {
          hoursWorthOf15Minutes =>
            Seq(
              <.tr(^.key := s"hr-${hoursWorthOf15Minutes.head}", {
                hoursWorthOf15Minutes.map((t: Long) => {
                  val d = new Date(t)
                  val display = f"${d.getHours}%02d:${d.getMinutes}%02d"
                  <.th(^.key := t, display)
                })
              }),
              <.tr(^.key := s"vr-${hoursWorthOf15Minutes.head}",
                hoursWorthOf15Minutes.map(t => <.td(^.key := t, s"${StaffMovements.staffAt(ss)(Nil)(t)}"))
              ))
        }
      )
    )
  }


  private def simpleStaffingTable(daysWorthOf15Minutes: NumericRange[Long], ss: ShiftService) = {
    <.table(
      <.tr({
        daysWorthOf15Minutes.map((t: Long) => {
          val d = new Date(t)
          val display = f"${d.getHours}%02d:${d.getMinutes}"
          <.td(^.key := t, display)
        })
      }),
      <.tr(
        daysWorthOf15Minutes.map(t => <.td(^.key := t, s"${StaffMovements.staffAt(ss)(Nil)(t)}"))
      )
    )
  }

  def apply(): ReactElement =
    component(Props())

  private val component = ReactComponentB[Props]("Staffing")
    .renderBackend[Backend]
    .build
}
