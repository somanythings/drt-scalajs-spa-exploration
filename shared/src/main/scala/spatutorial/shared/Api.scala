package spatutorial.shared

case class CrunchResult(recommendedDesks: Seq[Double])

trait Api {
  def welcomeMsg(name: String): String

  def getAllTodos(): Seq[TodoItem]

  def updateTodo(item: TodoItem): Seq[TodoItem]

  def deleteTodo(itemId: String): Seq[TodoItem]

  def crunch(workloads: Seq[Double]): CrunchResult
}
