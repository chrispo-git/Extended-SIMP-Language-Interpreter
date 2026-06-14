package simp

import org.scalatest.funsuite.AnyFunSuite

class ParserTest extends AnyFunSuite:

  def parse(source: String): List[Program] = {
    val sourceLines = source.split('\n').toList
    val tokens = Lexer(source, sourceLines).tokenise()
    Parser(tokens._1, StructEnv(), tokens._2, sourceLines).parseProgram()
  }

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
    assert(parse("x := 1; if !x == 1 then {skip} else {skip}") == List(Program.PCmd(
      Cmd.Seq(Cmd.Assign("x", Expr.Num(1)), Cmd.If(BoolExpr.Compare(Expr.Deref("x"), Bop.Eq, Expr.Num(1)), Cmd.Skip, Cmd.Skip))
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
    assert(parse("x := 5 ; while !x > 0 do {x := !x - 1}") == List(
        Program.PCmd(
          Cmd.Seq(
            Cmd.Assign("x", Expr.Num(5)), 
            Cmd.While(BoolExpr.Compare(Expr.Deref("x"), Bop.Gt, Expr.Num(0)), 
              Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("x"), Op.Sub, Expr.Num(1)))
            )
          )
        )
    ))
  }


  // Arrays
  test("parse empty array") {
      assert(parse("arr := []") == List(Program.PCmd(
          Cmd.Assign("arr", Expr.ArrLiteral(List()))
      )))
  }

  test("parse array literal") {
      assert(parse("arr := [1, 2, 3]") == List(Program.PCmd(
          Cmd.Assign("arr", Expr.ArrLiteral(List(Expr.Num(1), Expr.Num(2), Expr.Num(3))))
      )))
  }

  test("parse array index read") {
      assert(parse("x := arr[0]") == List(Program.PCmd(
          Cmd.Assign("x", Expr.ArrIndex(Expr.Ref("arr"), Expr.Num(0)))
      )))
  }

  test("parse array index assignment") {
      assert(parse("arr[0] := 5") == List(Program.PCmd(
          Cmd.ArrAssign("arr", Expr.Num(0), Expr.Num(5))
      )))
  }

  test("parse array index with expression") {
      assert(parse("arr[!i + 1] := 5") == List(Program.PCmd(
          Cmd.ArrAssign("arr", Expr.BinaryOp(Expr.Deref("i"), Op.Add, Expr.Num(1)), Expr.Num(5))
      )))
  }

  test("parse nested array index") {
      assert(parse("x := arr[arr[0]]") == List(Program.PCmd(
          Cmd.Assign("x", Expr.ArrIndex(Expr.Ref("arr"), Expr.ArrIndex(Expr.Ref("arr"), Expr.Num(0))))
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

  // Structs
  test("parse struct declaration") {
      assert(parse("struct Point { x: Int, y: Int }") == List(Program.PDecl(
          Decl.StructDecl("Point", List(("x", SimpType.TypeInt, None), ("y", SimpType.TypeInt, None)))
      )))
  }

  test("parse struct literal") {
      assert(parse("struct Point { x: Int, y: Int }; p := Point { x: 1, y: 2 }") == List(Program.PDecl(
          Decl.StructDecl("Point", List(("x", SimpType.TypeInt, None), ("y", SimpType.TypeInt, None)))
      ),Program.PCmd(
          Cmd.Assign("p", Expr.StructLiteral("Point", List(("x", Expr.Num(1)), ("y", Expr.Num(2)))))
      )))
  }

  test("parse field access") {
      assert(parse("x := p.y") == List(Program.PCmd(
          Cmd.Assign("x", Expr.FieldAccess(Expr.Ref("p"), "y"))
      )))
  }

  test("parse field assignment") {
      assert(parse("p.x := 5") == List(Program.PCmd(
          Cmd.FieldAssign("p", "x", Expr.Num(5))
      )))
  }

  test("parse nested field access") {
      assert(parse("x := line.start.x") == List(Program.PCmd(
          Cmd.Assign("x", Expr.FieldAccess(Expr.FieldAccess(Expr.Ref("line"), "start"), "x"))
      )))
  }
