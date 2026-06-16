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
    assert(parse("x := 5") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(5),1))))
  }

  test("parse assignment with dereference") {
    assert(parse("x := !y") == List(Program.PCmd(Cmd.Assign("x", Expr.Deref("y"),1))))
  }

  test("parse sequence") {
    assert(parse("skip ; skip") == List(Program.PCmd(Cmd.Seq(Cmd.Skip, Cmd.Skip))))
  }

  test("parse if then else") {
    assert(parse("x := 1; if !x == 1 then {skip} else {skip}") == List(Program.PCmd(
      Cmd.Seq(Cmd.Assign("x", Expr.Num(1),1), Cmd.If(BoolExpr.Compare(Expr.Deref("x"), Bop.Eq, Expr.Num(1)), Cmd.Skip, Cmd.Skip,1))
    )))
  }

  test("parse while") {
    assert(parse("while true do {skip}") == List(Program.PCmd(
      Cmd.While(BoolExpr.Literal(true), Cmd.Skip,1)
    )))
  }

  test("parse nested sequence") {
    assert(parse("skip ; skip ; skip") == List(Program.PCmd(
      Cmd.Seq(Cmd.Skip, Cmd.Seq(Cmd.Skip, Cmd.Skip))
    )))
  }
  // Anonymous Scope
  test("parse empty scope block") {
    assert(parse("{}") == List(Program.PCmd(Cmd.Scope(Cmd.Skip))))
  }

  test("parse scope block with body") {
    assert(parse("{x := 5}") == List(Program.PCmd(Cmd.Scope(Cmd.Assign("x", Expr.Num(5), 1)))))
  }
  // For Loop
  test("parse for loop") {
    assert(parse("for x in [1,2,3] {skip}") == List(Program.PCmd(
      Cmd.For("x", Expr.ArrLiteral(List(Expr.Num(1), Expr.Num(2), Expr.Num(3))), Cmd.Skip, 1)
    )))
  }
  // Pattern Match
  test("parse match expression") {
    assert(parse("x := match !n { case 1 => 2; case _ => 0; }") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Match(
        Expr.Deref("n"),
        List(
          MatchArm(Pattern.PLit(Expr.Num(1)), None, Expr.Num(2)),
          MatchArm(Pattern.PWild, None, Expr.Num(0))
        )
      ), 1)
    )))
  }

  // Pairs
  test("parse pair literal") {
    assert(parse("x := (1, 2)") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Pair(Expr.Num(1), Expr.Num(2)), 1)
    )))
  }

  test("parse pair field access fst") {
    assert(parse("x := p.fst") == List(Program.PCmd(
      Cmd.Assign("x", Expr.FieldAccess(Expr.Ref("p"), "fst"), 1)
    )))
  }

  // Const
  test("parse const declaration") {
    assert(parse("const x := 5") == List(Program.PCmd(Cmd.ConstAssign("x", Expr.Num(5), 1))))
  }

  test("parse const with expression") {
    assert(parse("const x := 2 + 3") == List(Program.PCmd(Cmd.ConstAssign("x", Expr.Num(5), 1))))
  }


  // Test Optimisations
  test("fold integer addition") {
    assert(parse("x := 2 + 3") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(5), 1))))
  }

  test("fold integer multiplication") {
    assert(parse("x := 2 * 3") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(6), 1))))
  }

  test("fold chained arithmetic") {
    assert(parse("x := 2 + 3 * 4") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(14), 1))))
  }

  test("fold string concatenation") {
    assert(parse("""x := "hello" + " world"""") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Str("hello world"), 1)
    )))
  }

  test("fold float arithmetic") {
    assert(parse("x := 1.5 + 2.5") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(4.0), 1))))
  }

  test("fold mixed int float") {
    assert(parse("x := 2 * 3.0") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(6.0), 1))))
  }

  test("fold bitwise and") {
    assert(parse("x := 5 & 3") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(1), 1))))
  }

  test("fold left shift") {
    assert(parse("x := 1 << 4") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(16), 1))))
  }

  test("fold bit complement") {
    assert(parse("x := ~0") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(-1), 1))))
  }

  test("division by zero not folded") {
    assert(parse("x := 5 / 0") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Num(5), Op.Div, Expr.Num(0)), 1)
    )))
  }

  test("modulo by zero not folded") {
    assert(parse("x := 5 % 0") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Num(5), Op.Mod, Expr.Num(0)), 1)
    )))
  }

  test("variable arithmetic not folded") {
    assert(parse("x := !y + 2") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("y"), Op.Add, Expr.Num(2)), 1)
    )))
  }
  test("fold int equality to true") {
    assert(parse("if 5 == 5 then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("fold int equality to false") {
    assert(parse("if 5 == 6 then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("fold string equality") {
    assert(parse("""if "a" == "a" then {skip} else {skip}""") == List(Program.PCmd(Cmd.Skip)))
  }

  test("fold bool equality") {
    assert(parse("if true == true then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("fold float comparison") {
    assert(parse("if 1.5 < 2.5 then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("fold arithmetic then compare") {
    assert(parse("if 2 + 3 == 5 then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("comparison with variable not folded") {
    assert(parse("if !x == 5 then {skip} else {skip}") == List(Program.PCmd(
      Cmd.If(BoolExpr.Compare(Expr.Deref("x"), Bop.Eq, Expr.Num(5)), Cmd.Skip, Cmd.Skip, 1)
    )))
  }

  test("eliminate false if branch") {
    assert(parse("if false then {x := 1} else {x := 2}") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Num(2), 1)
    )))
  }

  test("eliminate true if else branch") {
    assert(parse("if true then {x := 1} else {x := 2}") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Num(1), 1)
    )))
  }

  test("eliminate while false") {
    assert(parse("while false do {x := 1}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("while true not eliminated") {
    assert(parse("while true do {skip}") == List(Program.PCmd(
      Cmd.While(BoolExpr.Literal(true), Cmd.Skip, 1)
    )))
  }

  test("eliminate chained fold: arithmetic comparison dead branch") {
    assert(parse("if 2 + 3 == 5 then {x := 1} else {x := 2}") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Num(1), 1)
    )))
  }
  // Negative literals
  test("negative literal") {
    assert(parse("x := -5") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(-5), 1))))
  }

  test("binary minus not confused with negative literal") {
    assert(parse("x := 3 - 2") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(1), 1))))
  }

  test("negative literal in array index") {
    assert(parse("x := arr[-1 + 3]") == List(Program.PCmd(
      Cmd.Assign("x", Expr.ArrIndex(Expr.Ref("arr"), Expr.Num(2)), 1)
    )))
  }

  test("binary minus in array index with variable") {
    assert(parse("x := arr[!i - 1]") == List(Program.PCmd(
      Cmd.Assign("x", Expr.ArrIndex(
        Expr.Ref("arr"),
        Expr.BinaryOp(Expr.Deref("i"), Op.Sub, Expr.Num(1))
      ), 1)
    )))
  }

  test("double negative") {
    assert(parse("x := !y - -5") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("y"), Op.Sub, Expr.Num(-5)), 1)
    )))
  }
  // Integration
  test("parse realistic while program") {
    assert(parse("x := 5 ; while !x > 0 do {x := !x - 1}") == List(
        Program.PCmd(
          Cmd.Seq(
            Cmd.Assign("x", Expr.Num(5),1), 
            Cmd.While(BoolExpr.Compare(Expr.Deref("x"), Bop.Gt, Expr.Num(0)), 
              Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("x"), Op.Sub, Expr.Num(1)),1),1
            )
          )
        )
    ))
  }


  // Arrays
  test("parse empty array") {
      assert(parse("arr := []") == List(Program.PCmd(
          Cmd.Assign("arr", Expr.ArrLiteral(List()),1)
      )))
  }

  test("parse array literal") {
      assert(parse("arr := [1, 2, 3]") == List(Program.PCmd(
          Cmd.Assign("arr", Expr.ArrLiteral(List(Expr.Num(1), Expr.Num(2), Expr.Num(3))),1)
      )))
  }

  test("parse array index read") {
      assert(parse("x := arr[0]") == List(Program.PCmd(
          Cmd.Assign("x", Expr.ArrIndex(Expr.Ref("arr"), Expr.Num(0)),1)
      )))
  }

  test("parse array index assignment") {
      assert(parse("arr[0] := 5") == List(Program.PCmd(
          Cmd.ArrAssign("arr", Expr.Num(0), Expr.Num(5),1)
      )))
  }

  test("parse array index with expression") {
      assert(parse("arr[!i + 1] := 5") == List(Program.PCmd(
          Cmd.ArrAssign("arr", Expr.BinaryOp(Expr.Deref("i"), Op.Add, Expr.Num(1)), Expr.Num(5),1)
      )))
  }

  test("parse nested array index") {
      assert(parse("x := arr[arr[0]]") == List(Program.PCmd(
          Cmd.Assign("x", Expr.ArrIndex(Expr.Ref("arr"), Expr.ArrIndex(Expr.Ref("arr"), Expr.Num(0))),1)
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
          Cmd.Assign("p", Expr.StructLiteral("Point", List(("x", Expr.Num(1)), ("y", Expr.Num(2)))),1)
      )))
  }

  test("parse field access") {
      assert(parse("x := p.y") == List(Program.PCmd(
          Cmd.Assign("x", Expr.FieldAccess(Expr.Ref("p"), "y"),1)
      )))
  }

  test("parse field assignment") {
      assert(parse("p.x := 5") == List(Program.PCmd(
          Cmd.FieldAssign("p", "x", Expr.Num(5),1)
      )))
  }

  test("parse nested field access") {
      assert(parse("x := line.start.x") == List(Program.PCmd(
          Cmd.Assign("x", Expr.FieldAccess(Expr.FieldAccess(Expr.Ref("line"), "start"), "x"),1)
      )))
  }

  // Functions
  test("parse function declaration") {
    assert(parse("fn add(x: Int, y: Int) -> Int { return !x + !y }") == List(Program.PDecl(
      Decl.FnDecl("add",
        List(("x", SimpType.TypeInt), ("y", SimpType.TypeInt)),
        Cmd.Return(Some(Expr.BinaryOp(Expr.Deref("x"), Op.Add, Expr.Deref("y"))), 1),
        SimpType.TypeInt
      )
    )))
  }
  test("parse void function") {
    assert(parse("fn greet(name: Str) -> Void { skip }") == List(Program.PDecl(
      Decl.FnDecl("greet",
        List(("name", SimpType.TypeString)),
        Cmd.Skip,
        SimpType.TypeNull
      )
    )))
  }

  test("parse function with Float return type") {
    assert(parse("fn half(x: Float) -> Float { return !x / 2.0 }") == List(Program.PDecl(
      Decl.FnDecl("half",
        List(("x", SimpType.TypeFloat)),
        Cmd.Return(Some(Expr.BinaryOp(Expr.Deref("x"), Op.Div, Expr.Flt(2.0))), 1),
        SimpType.TypeFloat
      )
    )))
  }
  test("parse array type") {
    assert(parse("fn f(x: Int[]) -> Int[] { return !x }") == List(Program.PDecl(
      Decl.FnDecl("f",
        List(("x", SimpType.TypeArr(SimpType.TypeInt))),
        Cmd.Return(Some(Expr.Deref("x")), 1),
        SimpType.TypeArr(SimpType.TypeInt)
      )
    )))
  }

  test("parse map type") {
    assert(parse("fn f(m: Map(Str, Int)) -> Void { skip }") == List(Program.PDecl(
      Decl.FnDecl("f",
        List(("m", SimpType.TypeMap(SimpType.TypeString, SimpType.TypeInt))),
        Cmd.Skip,
        SimpType.TypeNull
      )
    )))
  }

  test("parse pair type") {
    assert(parse("fn f(p: (Int, Str)) -> Void { skip }") == List(Program.PDecl(
      Decl.FnDecl("f",
        List(("p", SimpType.TypePair(SimpType.TypeInt, SimpType.TypeString))),
        Cmd.Skip,
        SimpType.TypeNull
      )
    )))
  }

  test("parse struct type in param") {
    assert(parse("fn f(p: Point) -> Void { skip }") == List(Program.PDecl(
      Decl.FnDecl("f",
        List(("p", SimpType.TypeStruct("Point"))),
        Cmd.Skip,
        SimpType.TypeNull
      )
    )))
  }

  // Impl Blocks
  test("parse impl block") {
    assert(parse(
      """struct Point { x: Int, y: Int }
        impl Point {
            fn toStr(self: Point) -> Str { return "point" }
        }""".stripMargin
    ) == List(
      Program.PDecl(Decl.StructDecl("Point", List(("x", SimpType.TypeInt, None), ("y", SimpType.TypeInt, None)))),
      Program.PImpl("Point", List(
        Decl.FnDecl("toStr",
          List(("self", SimpType.TypeStruct("Point"))),
          Cmd.Return(Some(Expr.Str("point")), 3),
          SimpType.TypeString
        )
      ))
    ))
  }

  // Method Calls
  test("parse method call") {
    assert(parse("x := p.toStr()") == List(Program.PCmd(
      Cmd.Assign("x", Expr.MethodCall(Expr.Ref("p"), "toStr", List()), 1)
    )))
  }

  test("parse method call with args") {
    assert(parse("x := p.distance(q)") == List(Program.PCmd(
      Cmd.Assign("x", Expr.MethodCall(Expr.Ref("p"), "distance", List(Expr.Ref("q"))), 1)
    )))
  }

  test("parse chained method call") {
    assert(parse("x := r1.combine(r2).area()") == List(Program.PCmd(
      Cmd.Assign("x", Expr.MethodCall(
        Expr.MethodCall(Expr.Ref("r1"), "combine", List(Expr.Ref("r2"))),
        "area", List()
      ), 1)
    )))
  }

  test("parse method call as statement") {
    assert(parse("p.translate(1, 0)") == List(Program.PCmd(
      Cmd.Assign("_", Expr.MethodCall(Expr.Ref("p"), "translate", List(Expr.Num(1), Expr.Num(0))), 1)
    )))
  }

  // Errors
  test("throw on missing closing brace") {
    assertThrows[RuntimeException](parse("if true then {skip"))
  }

  test("throw on invalid type") {
    assertThrows[RuntimeException](parse("fn f(x: 5) -> Void { skip }"))
  }

  test("throw on missing arrow in fn") {
    assertThrows[RuntimeException](parse("fn f(x: Int) Void { skip }"))
  }

  test("throw on const without name") {
    assertThrows[RuntimeException](parse("const := 5"))
  }

  test("throw on impl without struct name") {
    assertThrows[RuntimeException](parse("impl { fn f() -> Void { skip } }"))
  }
