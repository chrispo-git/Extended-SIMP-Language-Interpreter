package simp

object Main extends App {
  val source = "x := 5 ; while !x > 0 do x := !x - 1"
  val tokens = Lexer(source).tokenise()
  val program = Parser(tokens).parseProgram()
  val store = Store()
  Evaluator(store).evalProgram(program)
}