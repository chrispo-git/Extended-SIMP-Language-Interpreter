package simp

import org.scalatest.funsuite.AnyFunSuite

class ParserTest extends AnyFunSuite:

  def parse(source: String): Program = Parser(Lexer(source).tokenise()).parseProgram()

  // Commands
  test("parse skip") {
    assert(parse("skip") == Program.PCmd(Cmd.Skip))
  }

  test("parse assignment") {
    assert(parse("x := 5") == Program.PCmd(Cmd.Assign("x", Expr.Num(5))))
  }

  test("parse assignment with dereference") {
    assert(parse("x := !y") == Program.PCmd(Cmd.Assign("x", Expr.Deref("y"))))
  }

  test("parse sequence") {
    assert(parse("skip ; skip") == Program.PCmd(Cmd.Seq(Cmd.Skip, Cmd.Skip)))
  }

  test("parse if then else") {
    assert(parse("if true then skip else skip") == Program.PCmd(
      Cmd.If(BoolExpr.Literal(true), Cmd.Skip, Cmd.Skip)
    ))
  }

  test("parse while") {
    assert(parse("while true do skip") == Program.PCmd(
      Cmd.While(BoolExpr.Literal(true), Cmd.Skip)
    ))
  }

  test("parse nested sequence") {
    assert(parse("skip ; skip ; skip") == Program.PCmd(
      Cmd.Seq(Cmd.Skip, Cmd.Seq(Cmd.Skip, Cmd.Skip))
    ))
  }

  // Expressions
  test("parse number") {
    assert(parse("5") == Program.PExpr(Expr.Num(5)))
  }

  test("parse addition") {
    assert(parse("2 + 3") == Program.PExpr(
      Expr.BinaryOp(Expr.Num(2), Op.Add, Expr.Num(3))
    ))
  }

  test("respect precedence of * over +") {
    assert(parse("2 + 3 * 4") == Program.PExpr(
      Expr.BinaryOp(Expr.Num(2), Op.Add, Expr.BinaryOp(Expr.Num(3), Op.Mul, Expr.Num(4)))
    ))
  }

  test("parse parenthesised expression") {
    assert(parse("(2 + 3) * 4") == Program.PExpr(
      Expr.BinaryOp(Expr.BinaryOp(Expr.Num(2), Op.Add, Expr.Num(3)), Op.Mul, Expr.Num(4))
    ))
  }

  test("parse dereference in expression") {
    assert(parse("!x + 1") == Program.PExpr(
      Expr.BinaryOp(Expr.Deref("x"), Op.Add, Expr.Num(1))
    ))
  }

  // Booleans
  test("parse true") {
    assert(parse("true") == Program.PBool(BoolExpr.Literal(true)))
  }

  test("parse false") {
    assert(parse("false") == Program.PBool(BoolExpr.Literal(false)))
  }

  test("parse not") {
    assert(parse("¬true") == Program.PBool(BoolExpr.Not(BoolExpr.Literal(true))))
  }

  test("parse comparison") {
    assert(parse("!x > 0") == Program.PBool(
      BoolExpr.Compare(Expr.Deref("x"), Bop.Gt, Expr.Num(0))
    ))
  }

  test("parse and") {
    assert(parse("true && false") == Program.PBool(
      BoolExpr.And(BoolExpr.Literal(true), BoolExpr.Literal(false))
    ))
  }

  test("parse or") {
    assert(parse("true || false") == Program.PBool(
      BoolExpr.Or(BoolExpr.Literal(true), BoolExpr.Literal(false))
    ))
  }

  // Integration
  test("parse realistic while program") {
    assert(parse("x := 5 ; while !x > 0 do x := !x - 1") == Program.PCmd(
      Cmd.Seq(
        Cmd.Assign("x", Expr.Num(5)),
        Cmd.While(
          BoolExpr.Compare(Expr.Deref("x"), Bop.Gt, Expr.Num(0)),
          Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("x"), Op.Sub, Expr.Num(1)))
        )
      )
    ))
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
