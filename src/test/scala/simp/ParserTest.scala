package simp

import org.scalatest.funsuite.AnyFunSuite

class ParserTest extends AnyFunSuite:

  def parse(source: String): List[Program] = Parser(Lexer(source).tokenise()).parseProgram()

  // Commands
  test("parse skip") {
    assert(parse("skip") == List(Program.PCmd(Cmd.Skip)))
  }

  test("parse assignment") {
    assert(parse("x := 5") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(5)))))
  }

  test("parse assignment with dereference") {
    assert(parse("x := !y") == List(Program.PCmd(Cmd.Assign("x", Expr.Deref("y")))))
  }

  test("parse sequence") {
    assert(parse("skip ; skip") == List(Program.PCmd(Cmd.Seq(Cmd.Skip, Cmd.Skip))))
  }

  test("parse if then else") {
    assert(parse("if true then {skip} else {skip}") == List(Program.PCmd(
      Cmd.If(BoolExpr.Literal(true), Cmd.Skip, Cmd.Skip)
    )))
  }

  test("parse while") {
    assert(parse("while true do {skip}") == List(Program.PCmd(
      Cmd.While(BoolExpr.Literal(true), Cmd.Skip)
    )))
  }

  test("parse nested sequence") {
    assert(parse("skip ; skip ; skip") == List(Program.PCmd(
      Cmd.Seq(Cmd.Skip, Cmd.Seq(Cmd.Skip, Cmd.Skip))
    )))
  }

  // Integration
  test("parse realistic while program") {
    assert(parse("x := 5 ; while !x > 0 do {x := !x - 1}") == List(Program.PCmd(
      Cmd.Seq(
        Cmd.Assign("x", Expr.Num(5)),
        Cmd.While(
          BoolExpr.Compare(Expr.Deref("x"), Bop.Gt, Expr.Num(0)),
          Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("x"), Op.Sub, Expr.Num(1)))
        )
      )
    )))
  }

  // Errors
  test("throw on missing then") {
    assertThrows[RuntimeException](parse("if true skip else skip"))
  }

  test("throw on missing else") {
    assertThrows[RuntimeException](parse("if true then skip skip"))
  }

  test("throw on bare variable") {
    assertThrows[RuntimeException](parse("x"))
  }
