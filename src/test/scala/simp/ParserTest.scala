package simp

import org.scalatest.funsuite.AnyFunSuite

class ParserTest extends AnyFunSuite:

  def parse(source: String): List[Program] = {
    val sourceLines = source.split('\n').toList
    val tokens = Lexer(source, sourceLines).tokenise()
    Parser(tokens._1, StructEnv(), tokens._2, sourceLines).parseProgram()
  }

  def repl(source: String): List[Program] = {
    val sourceLines = source.split('\n').toList
    val tokens = Lexer(source, sourceLines).tokenise()
    Parser(tokens._1, StructEnv(), tokens._2, sourceLines).parseRepl()
  }

  // Test-only subclass exposing parseType so we can exercise the 'ref' type
  // branch, which has no surface syntax reachable through the Lexer.
  class TestParser(tokens: List[Token], structEnv: StructEnv, lines: List[Int], sourceLines: List[String])
      extends Parser(tokens, structEnv, lines, sourceLines) {
    def testParseType(): SimpType = parseType()
    def testParseBoolOp(t: Token): Bop = parseBoolOp(t)
    def setPos(n: Int): Unit = pos = n
    def testPeek(): Token = peek()
    def testPeekNext(): Token = peekNext()
    def testAdvance(): Token = advance()
    def testCurrentLine(): Int = currentLine()
    def testCurrentLineSource(): String = currentLineSource()
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
          Decl.StructDecl("Point", List(("x", SimpType.TypeInt, None, false), ("y", SimpType.TypeInt, None, false)))
      )))
  }

  test("parse struct literal") {
      assert(parse("struct Point { x: Int, y: Int }; p := Point { x: 1, y: 2 }") == List(Program.PDecl(
          Decl.StructDecl("Point", List(("x", SimpType.TypeInt, None, false), ("y", SimpType.TypeInt, None, false)))
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
      Program.PDecl(Decl.StructDecl("Point", List(("x", SimpType.TypeInt, None, false), ("y", SimpType.TypeInt, None, false)))),
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

  // ---- Patterns ----

  test("parse wildcard pattern in match") {
    assert(parse("x := match 1 { case _ => 0; }") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Match(Expr.Num(1), List(MatchArm(Pattern.PWild, None, Expr.Num(0)))), 1)
    )))
  }

  test("parse pair pattern in match") {
    assert(parse("x := match p { case (a, b) => a; }") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Match(Expr.Ref("p"), List(
        MatchArm(Pattern.PPair(Pattern.PVar("a"), Pattern.PVar("b")), None, Expr.Ref("a"))
      )), 1)
    )))
  }

  test("parse struct pattern in match") {
    assert(parse("x := match p { case Point { x: a, y: b } => a; }") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Match(Expr.Ref("p"), List(
        MatchArm(Pattern.PStruct("Point", List(("x", Pattern.PVar("a")), ("y", Pattern.PVar("b")))), None, Expr.Ref("a"))
      )), 1)
    )))
  }

  test("parse bool literal pattern in match") {
    assert(parse("x := match true { case true => 1; case _ => 0; }") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Match(Expr.Bool(true), List(
        MatchArm(Pattern.PLit(Expr.Bool(true)), None, Expr.Num(1)),
        MatchArm(Pattern.PWild, None, Expr.Num(0))
      )), 1)
    )))
  }

  test("throw on invalid pattern") {
    assertThrows[RuntimeException](parse("x := match 1 { case + => 0; }"))
  }

  // ---- Postfix / method chaining ----

  test("parse chained array index") {
    assert(parse("x := arr[0][1]") == List(Program.PCmd(
      Cmd.Assign("x", Expr.ArrIndex(Expr.ArrIndex(Expr.Ref("arr"), Expr.Num(0)), Expr.Num(1)), 1)
    )))
  }

  test("throw on missing field name after dot") {
    assertThrows[RuntimeException](parse("x := p.5"))
  }

  // ---- Namespaced expressions ----

  test("parse namespaced function call") {
    assert(parse("x := math::square(2)") == List(Program.PCmd(
      Cmd.Assign("x", Expr.FnCall("math::square", List(Expr.Num(2))), 1)
    )))
  }

  test("parse namespaced struct literal") {
    // Namespaced structs are only registered in structEnv once an import has
    // been evaluated, so simulate that post-import state directly.
    val structEnv = StructEnv()
    structEnv.register("shapes::Point", StructDef(List(("x", SimpType.TypeInt, None, false), ("y", SimpType.TypeInt, None, false))))
    val source = "x := shapes::Point { x: 1, y: 2 }"
    val sourceLines = source.split('\n').toList
    val tokens = Lexer(source, sourceLines).tokenise()
    val result = Parser(tokens._1, structEnv, tokens._2, sourceLines).parseProgram()
    assert(result == List(Program.PCmd(
      Cmd.Assign("x", Expr.StructLiteral("shapes::Point", List(("x", Expr.Num(1)), ("y", Expr.Num(2)))), 1)
    )))
  }

  test("throw on invalid namespaced expression") {
    assertThrows[RuntimeException](parse("x := math::5"))
  }

  // ---- Type literals as expressions ----

  test("parse Int type literal expression") {
    assert(parse("x := Int") == List(Program.PCmd(Cmd.Assign("x", Expr.TypeLiteral(SimpType.TypeInt), 1))))
  }

  test("parse Str type literal expression") {
    assert(parse("x := Str") == List(Program.PCmd(Cmd.Assign("x", Expr.TypeLiteral(SimpType.TypeString), 1))))
  }

  test("parse Bool type literal expression") {
    assert(parse("x := Bool") == List(Program.PCmd(Cmd.Assign("x", Expr.TypeLiteral(SimpType.TypeBool), 1))))
  }

  test("parse Float type literal expression") {
    assert(parse("x := Float") == List(Program.PCmd(Cmd.Assign("x", Expr.TypeLiteral(SimpType.TypeFloat), 1))))
  }

  // ---- Null / not / unexpected ----

  test("parse null literal") {
    assert(parse("x := null") == List(Program.PCmd(Cmd.Assign("x", Expr.Null, 1))))
  }

  test("throw on deref without variable") {
    assertThrows[RuntimeException](parse("x := !5"))
  }

  test("parse boollift via not in expr position") {
    assert(parse("x := ¬true") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BoolLift(BoolExpr.Not(BoolExpr.Literal(true))), 1)
    )))
  }

  test("throw on unexpected token in atomic expr") {
    assertThrows[RuntimeException](parse("x := &"))
  }

  // ---- Block expressions ----

  test("parse block expression") {
    // The trailing result must start with a token parseSingleCmd() can also
    // attempt (here OpenBracket), since the block loop rolls back to
    // reparse it as an expression only after a successful-but-wrong parseSingleCmd.
    assert(parse("x := { y := 1; (!y + 1) }") == List(Program.PCmd(
      Cmd.Assign("x", Expr.Block(List(Cmd.Assign("y", Expr.Num(1), 1)), Expr.BinaryOp(Expr.Deref("y"), Op.Add, Expr.Num(1))), 1)
    )))
  }

  test("throw on block missing result expression") {
    assertThrows[RuntimeException](parse("x := { y := 1; }"))
  }

  // ---- Parenthesised expressions ----

  test("parse parenthesised expression") {
    assert(parse("x := (1 + 2)") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(3), 1))))
  }

  // ---- Struct literal field parse errors ----

  test("throw on bad field name in struct literal") {
    assertThrows[RuntimeException](parse("struct Point { x: Int } p := Point { 5: 1 }"))
  }

  // ---- parseCmd: declarations mid-sequence are skipped ----

  test("fn declaration inside parseCmd position yields skip") {
    // parseCmd() sees the trailing ';' and recurses expecting another
    // command; hitting Fn there short-circuits to Skip, wrapped in a Seq.
    assert(parse("skip; fn f() -> Void { skip }") == List(
      Program.PCmd(Cmd.Seq(Cmd.Skip, Cmd.Skip)),
      Program.PDecl(Decl.FnDecl("f", List(), Cmd.Skip, SimpType.TypeNull))
    ))
  }

  // ---- Field / index assignment variants ----

  test("parse field plus-equals assignment") {
    assert(parse("p.x += 1") == List(Program.PCmd(
      Cmd.FieldAssign("p", "x", Expr.BinaryOp(Expr.FieldAccess(Expr.Ref("p"), "x"), Op.Add, Expr.Num(1)), 1)
    )))
  }

  test("parse field minus-equals assignment") {
    assert(parse("p.x -= 1") == List(Program.PCmd(
      Cmd.FieldAssign("p", "x", Expr.BinaryOp(Expr.FieldAccess(Expr.Ref("p"), "x"), Op.Sub, Expr.Num(1)), 1)
    )))
  }

  test("parse field mul-equals assignment") {
    assert(parse("p.x *= 2") == List(Program.PCmd(
      Cmd.FieldAssign("p", "x", Expr.BinaryOp(Expr.FieldAccess(Expr.Ref("p"), "x"), Op.Mul, Expr.Num(2)), 1)
    )))
  }

  test("parse field div-equals assignment") {
    assert(parse("p.x /= 2") == List(Program.PCmd(
      Cmd.FieldAssign("p", "x", Expr.BinaryOp(Expr.FieldAccess(Expr.Ref("p"), "x"), Op.Div, Expr.Num(2)), 1)
    )))
  }

  test("throw on missing operator in field assignment") {
    assertThrows[RuntimeException](parse("p.x 5"))
  }

  test("parse field index assignment") {
    assert(parse("p.arr[0] := 5") == List(Program.PCmd(
      Cmd.FieldIndexAssign("p", "arr", Expr.Num(0), Expr.Num(5), 1)
    )))
  }

  test("parse nested field index assignment") {
    assert(parse("p.arr[0][1] := 5") == List(Program.PCmd(
      Cmd.FieldIndexAssignNested("p", "arr", List(Expr.Num(0), Expr.Num(1)), Expr.Num(5), 1)
    )))
  }

  test("parse field index plus-equals assignment") {
    assert(parse("p.arr[0] += 1") == List(Program.PCmd(
      Cmd.FieldIndexAssign("p", "arr", Expr.Num(0), Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref("p"), "arr"), Expr.Num(0)), Op.Add, Expr.Num(1)), 1)
    )))
  }

  test("parse field index minus-equals assignment") {
    assert(parse("p.arr[0] -= 1") == List(Program.PCmd(
      Cmd.FieldIndexAssign("p", "arr", Expr.Num(0), Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref("p"), "arr"), Expr.Num(0)), Op.Sub, Expr.Num(1)), 1)
    )))
  }

  test("parse field index mul-equals assignment") {
    assert(parse("p.arr[0] *= 2") == List(Program.PCmd(
      Cmd.FieldIndexAssign("p", "arr", Expr.Num(0), Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref("p"), "arr"), Expr.Num(0)), Op.Mul, Expr.Num(2)), 1)
    )))
  }

  test("parse field index div-equals assignment") {
    assert(parse("p.arr[0] /= 2") == List(Program.PCmd(
      Cmd.FieldIndexAssign("p", "arr", Expr.Num(0), Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref("p"), "arr"), Expr.Num(0)), Op.Div, Expr.Num(2)), 1)
    )))
  }

  test("throw on missing operator in field index assignment") {
    assertThrows[RuntimeException](parse("p.arr[0] 5"))
  }

  test("parse method call as statement via dot bracket") {
    assert(parse("p.translate(1)") == List(Program.PCmd(
      Cmd.Assign("_", Expr.MethodCall(Expr.Ref("p"), "translate", List(Expr.Num(1))), 1)
    )))
  }

  test("throw on non-variable after dot in statement position") {
    assertThrows[RuntimeException](parse("p.5"))
  }

  // ---- Variable assignment variants ----

  test("throw on array index compound assignment (not supported)") {
    assertThrows[RuntimeException](parse("arr[0] += 1"))
  }

  test("parse nested array assignment") {
    assert(parse("arr[0][1] := 5") == List(Program.PCmd(
      Cmd.ArrAssignNested("arr", List(Expr.Num(0), Expr.Num(1)), Expr.Num(5), 1)
    )))
  }

  test("parse plus-equals assignment") {
    assert(parse("x += 1") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("x"), Op.Add, Expr.Num(1)), 1)
    )))
  }

  test("parse minus-equals assignment") {
    assert(parse("x -= 1") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("x"), Op.Sub, Expr.Num(1)), 1)
    )))
  }

  test("parse mul-equals assignment") {
    assert(parse("x *= 2") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("x"), Op.Mul, Expr.Num(2)), 1)
    )))
  }

  test("parse div-equals assignment") {
    assert(parse("x /= 2") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("x"), Op.Div, Expr.Num(2)), 1)
    )))
  }

  test("throw on missing operator in var assignment") {
    assertThrows[RuntimeException](parse("x 5"))
  }

  // ---- If / elif / while / for ----

  test("parse if with elif chain") {
    assert(parse("if true then {skip} elif false then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("parse if with neither elif nor else") {
    assert(parse("if !x == 1 then {skip}") == List(Program.PCmd(
      Cmd.If(BoolExpr.Compare(Expr.Deref("x"), Bop.Eq, Expr.Num(1)), Cmd.Skip, Cmd.Skip, 1)
    )))
  }

  test("throw on for loop missing variable") {
    assertThrows[RuntimeException](parse("for 5 in [1,2,3] {skip}"))
  }

  // ---- Parenthesised commands ----

  test("parse pair statement via bare parens") {
    assert(parse("(1, 2)") == List(Program.PCmd(Cmd.Assign("_", Expr.Pair(Expr.Num(1), Expr.Num(2)), 1))))
  }

  test("parse bare expression statement via parens") {
    assert(parse("(1 + 2)") == List(Program.PCmd(Cmd.Assign("_", Expr.Num(3), 1))))
  }

  test("parse parenthesised command") {
    assert(parse("(x := 5)") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(5), 1))))
  }

  test("parse parenthesised command triggering fallback on parse failure") {
    assert(parse("(if true then {skip} else {skip})") == List(Program.PCmd(Cmd.Skip)))
  }

  // ---- Return ----

  test("parse bare return") {
    assert(parse("fn f() -> Void { return; }") == List(Program.PDecl(
      Decl.FnDecl("f", List(), Cmd.Return(None), SimpType.TypeNull)
    )))
  }

  // ---- Const errors ----

  test("throw on const without variable name value") {
    assertThrows[RuntimeException](parse("const 5 := 1"))
  }

  // ---- Scope ----

  // ---- Decl errors ----

  test("throw on fn missing name") {
    assertThrows[RuntimeException](parse("fn () -> Void { skip }"))
  }

  test("throw on struct missing name") {
    assertThrows[RuntimeException](parse("struct { x: Int }"))
  }

  test("throw on import missing path") {
    assertThrows[RuntimeException](parse("import 5"))
  }

  test("throw on import missing alias after as") {
    assertThrows[RuntimeException](parse("import \"foo.simp\" as 5"))
  }

  test("import without alias derives alias from filename") {
    assert(parse("import \"utils.simp\"") == List(Program.PDecl(Decl.ImportDecl("utils.simp", "utils"))))
  }

  test("import with explicit alias") {
    assert(parse("import \"utils.simp\" as u") == List(Program.PDecl(Decl.ImportDecl("utils.simp", "u"))))
  }

  test("throw on unexpected token starting a declaration") {
    // parseImpl() calls parseDecl() unconditionally for each member, so a
    // non-declaration token inside an impl block reaches parseDecl's default case.
    assertThrows[RuntimeException](parse("impl Point { skip }"))
  }

  test("throw on impl containing non-function declaration") {
    assertThrows[RuntimeException](parse("impl Point { struct Bad { x: Int } }"))
  }

  // ---- Types ----

  test("parse 2D array type") {
    assert(parse("fn f(x: Int[][]) -> Void { skip }") == List(Program.PDecl(
      Decl.FnDecl("f", List(("x", SimpType.TypeArr(SimpType.TypeArr(SimpType.TypeInt)))), Cmd.Skip, SimpType.TypeNull)
    )))
  }

  test("throw on missing parameter name") {
    assertThrows[RuntimeException](parse("fn f(5: Int) -> Void { skip }"))
  }

  test("parse empty params list") {
    assert(parse("fn f() -> Void { skip }") == List(Program.PDecl(
      Decl.FnDecl("f", List(), Cmd.Skip, SimpType.TypeNull)
    )))
  }

  test("parse ref type via direct parser access") {
    val tokens = List(Token.Ref, Token.TypeInt, Token.EOF)
    val p = TestParser(tokens, StructEnv(), List(1, 1, 1), List(""))
    assert(p.testParseType() == SimpType.TypeRef(SimpType.TypeInt))
  }

  // ---- Folding ----

  test("fold float subtraction") {
    assert(parse("x := 5.0 - 2.0") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(3.0), 1))))
  }

  test("fold float multiplication") {
    assert(parse("x := 2.0 * 3.0") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(6.0), 1))))
  }

  test("fold float division") {
    assert(parse("x := 6.0 / 2.0") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(3.0), 1))))
  }

  test("float division by zero not folded") {
    assert(parse("x := 5.0 / 0.0") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Flt(5.0), Op.Div, Expr.Flt(0.0)), 1)
    )))
  }

  test("fold int minus float subtraction") {
    assert(parse("x := 5 - 2.0") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(3.0), 1))))
  }

  test("fold int mul float multiplication") {
    assert(parse("x := 2 * 3.0") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(6.0), 1))))
  }

  test("fold int div float division") {
    assert(parse("x := 6 / 2.0") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(3.0), 1))))
  }

  test("int div float by zero not folded") {
    assert(parse("x := 5 / 0.0") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Num(5), Op.Div, Expr.Flt(0.0)), 1)
    )))
  }

  test("fold float minus int subtraction") {
    assert(parse("x := 5.0 - 2") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(3.0), 1))))
  }

  test("fold float mul int multiplication") {
    assert(parse("x := 2.0 * 3") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(6.0), 1))))
  }

  test("fold float div int division") {
    assert(parse("x := 6.0 / 2") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(3.0), 1))))
  }

  test("float div int by zero not folded") {
    assert(parse("x := 5.0 / 0") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BinaryOp(Expr.Flt(5.0), Op.Div, Expr.Num(0)), 1)
    )))
  }

  test("fold bitwise or") {
    assert(parse("x := 5 | 2") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(7), 1))))
  }

  test("fold bitwise xor") {
    assert(parse("x := 5 ^ 3") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(6), 1))))
  }

  test("fold bitwise right shift") {
    assert(parse("x := 16 >> 2") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(4), 1))))
  }

  test("fold bitwise right fill shift") {
    assert(parse("x := 16 >>> 2") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(4), 1))))
  }

  test("fold float equality") {
    assert(parse("if 1.5 == 1.5 then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("fold float inequality") {
    assert(parse("if 1.5 != 2.5 then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("fold string inequality") {
    assert(parse("""if "a" != "b" then {skip} else {skip}""") == List(Program.PCmd(Cmd.Skip)))
  }

  test("fold bool inequality") {
    assert(parse("if true != false then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("string comparison with non-eq operator not folded") {
    assert(parse("""if "a" < "b" then {x := 1} else {x := 2}""") == List(Program.PCmd(
      Cmd.If(BoolExpr.Compare(Expr.Str("a"), Bop.Lt, Expr.Str("b")), Cmd.Assign("x", Expr.Num(1), 1), Cmd.Assign("x", Expr.Num(2), 1), 1)
    )))
  }

  test("unary complement on non-literal not folded") {
    assert(parse("x := ~!y") == List(Program.PCmd(
      Cmd.Assign("x", Expr.UnaryOp(Expr.Deref("y"), Op.BitComplement), 1)
    )))
  }

  // ---- Boolean expression parsing ----

  test("parse and expression at boolExpr level") {
    val store_prog = parse("x := true && false")
    assert(store_prog == List(Program.PCmd(Cmd.Assign("x", Expr.BoolLift(BoolExpr.And(BoolExpr.Literal(true), BoolExpr.Literal(false))), 1))))
  }

  test("parse or expression at boolExpr level") {
    assert(parse("x := true || false") == List(Program.PCmd(
      Cmd.Assign("x", Expr.BoolLift(BoolExpr.Or(BoolExpr.Literal(true), BoolExpr.Literal(false))), 1)
    )))
  }

  test("parse chained and/or at boolExpr level") {
    assert(parse("if true && false || true then {x:=1} else {x:=2}") == List(Program.PCmd(
      Cmd.If(
        BoolExpr.Or(BoolExpr.And(BoolExpr.Literal(true), BoolExpr.Literal(false)), BoolExpr.Literal(true)),
        Cmd.Assign("x", Expr.Num(1), 1), Cmd.Assign("x", Expr.Num(2), 1), 1
      )
    )))
  }

  test("parse bool literal equality") {
    assert(parse("if true == true then {x:=1} else {x:=2}") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(1), 1))))
  }

  test("parse bool literal without trailing comparison") {
    assert(parse("if true then {x:=1} else {x:=2}") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(1), 1))))
  }

  test("parse not in bool position") {
    assert(parse("if ¬false then {x:=1} else {x:=2}") == List(Program.PCmd(
      Cmd.If(BoolExpr.Not(BoolExpr.Literal(false)), Cmd.Assign("x", Expr.Num(1), 1), Cmd.Assign("x", Expr.Num(2), 1), 1)
    )))
  }

  test("parse float comparison in bool position") {
    assert(parse("if 1.5 > 1.0 then {x:=1} else {x:=2}") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(1), 1))))
  }

  test("parse string in bool position without comparison") {
    assert(parse("if \"x\" then {skip} else {skip}") == List(Program.PCmd(
      Cmd.If(BoolExpr.FromExpr(Expr.Str("x")), Cmd.Skip, Cmd.Skip, 1)
    )))
  }

  test("parse parenthesised bool expression") {
    assert(parse("if (true) then {x:=1} else {x:=2}") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(1), 1))))
  }

  test("parse function call in bool position with comparison") {
    assert(parse("fn f() -> Int { return 5; } if f() > 3 then {x:=1} else {x:=2}") == List(
      Program.PDecl(Decl.FnDecl("f", List(), Cmd.Return(Some(Expr.Num(5)), 1), SimpType.TypeInt)),
      Program.PCmd(Cmd.If(
        BoolExpr.Compare(Expr.FnCall("f", List()), Bop.Gt, Expr.Num(3)),
        Cmd.Assign("x", Expr.Num(1), 1), Cmd.Assign("x", Expr.Num(2), 1), 1
      ))
    ))
  }

  test("parse function call in bool position without comparison") {
    assert(parse("fn f() -> Bool { return true; } if f() then {x:=1} else {x:=2}") == List(
      Program.PDecl(Decl.FnDecl("f", List(), Cmd.Return(Some(Expr.Bool(true)), 1), SimpType.TypeBool)),
      Program.PCmd(Cmd.If(
        BoolExpr.FromExpr(Expr.FnCall("f", List())),
        Cmd.Assign("x", Expr.Num(1), 1), Cmd.Assign("x", Expr.Num(2), 1), 1
      ))
    ))
  }

  test("parse array index in bool position with comparison") {
    assert(parse("if arr[0] > 3 then {x:=1} else {x:=2}") == List(Program.PCmd(
      Cmd.If(BoolExpr.Compare(Expr.ArrIndex(Expr.Ref("arr"), Expr.Num(0)), Bop.Gt, Expr.Num(3)), Cmd.Assign("x", Expr.Num(1), 1), Cmd.Assign("x", Expr.Num(2), 1), 1)
    )))
  }

  test("parse array index in bool position without comparison") {
    assert(parse("if arr[0] then {skip} else {skip}") == List(Program.PCmd(
      Cmd.If(BoolExpr.FromExpr(Expr.ArrIndex(Expr.Ref("arr"), Expr.Num(0))), Cmd.Skip, Cmd.Skip, 1)
    )))
  }

  test("parse field access in bool position with comparison") {
    assert(parse("if p.x > 3 then {x:=1} else {x:=2}") == List(Program.PCmd(
      Cmd.If(BoolExpr.Compare(Expr.FieldAccess(Expr.Ref("p"), "x"), Bop.Gt, Expr.Num(3)), Cmd.Assign("x", Expr.Num(1), 1), Cmd.Assign("x", Expr.Num(2), 1), 1)
    )))
  }

  test("parse field access in bool position without comparison") {
    assert(parse("if p.x then {skip} else {skip}") == List(Program.PCmd(
      Cmd.If(BoolExpr.FromExpr(Expr.FieldAccess(Expr.Ref("p"), "x")), Cmd.Skip, Cmd.Skip, 1)
    )))
  }

  test("throw on unexpected token in bool position") {
    assertThrows[RuntimeException](parse("if & then {skip} else {skip}"))
  }

  // ---- REPL parsing ----

  test("repl parses declaration") {
    assert(repl("fn f() -> Void { skip }") == List(Program.PDecl(
      Decl.FnDecl("f", List(), Cmd.Skip, SimpType.TypeNull)
    )))
  }

  test("repl parses impl block") {
    assert(repl(
      """struct Point { x: Int }
        impl Point { fn get(self: Point) -> Int { return self.x; } }""".stripMargin
    ).length == 2)
  }

  test("repl parses bool literal") {
    assert(repl("true") == List(Program.PBool(BoolExpr.Literal(true))))
  }

  test("repl parses not-prefixed bool") {
    assert(repl("¬true") == List(Program.PBool(BoolExpr.Not(BoolExpr.Literal(true)))))
  }

  test("repl parses literal comparison as a folded boolean expr") {
    // parseExpr() eagerly consumes a trailing comparator itself, so by the
    // time parseRepl() re-checks peek() it can never see one; the result
    // comes back wrapped as a BoolLift expression, not Program.PBool.
    assert(repl("1 > 2") == List(Program.PExpr(Expr.BoolLift(BoolExpr.Literal(false)))))
  }

  test("repl parses int literal without comparison as expr") {
    assert(repl("42") == List(Program.PExpr(Expr.Num(42))))
  }

  test("repl parses float literal as expr") {
    assert(repl("1.5") == List(Program.PExpr(Expr.Flt(1.5))))
  }

  test("repl parses deref as expr") {
    assert(repl("!x") == List(Program.PExpr(Expr.Deref("x"))))
  }

  test("repl parses bit complement as expr") {
    assert(repl("~5") == List(Program.PExpr(Expr.Num(-6))))
  }

  test("repl parses function call as expr") {
    assert(repl("f(1)") == List(Program.PExpr(Expr.FnCall("f", List(Expr.Num(1))))))
  }

  test("repl array index lookahead without assignment only consumes the base variable") {
    // The non-assignment branch parses via parseAtomicExpr(), which doesn't
    // include postfix indexing, so the '[0]' is left unconsumed and the
    // subsequent re-parse of that leftover token fails.
    assertThrows[RuntimeException](repl("arr[0]"))
  }

  test("repl parses array index assignment lookahead as cmd") {
    assert(repl("arr[0] := 5") == List(Program.PCmd(Cmd.ArrAssign("arr", Expr.Num(0), Expr.Num(5), 1))))
  }

  test("repl array index depth tracking handles nested brackets") {
    assertThrows[RuntimeException](repl("arr[arr[0]]"))
  }

  test("repl parses default cmd branch") {
    assert(repl("skip") == List(Program.PCmd(Cmd.Skip)))
  }

  test("repl skips multiple semicolons between items") {
    assert(repl("true;; false") == List(Program.PBool(BoolExpr.Literal(true)), Program.PBool(BoolExpr.Literal(false))))
  }

  // ---- Coverage mop-up ----

  test("throw on bad field name in struct pattern") {
    assertThrows[RuntimeException](parse("x := match p { case Point { 5: a } => 1; case _ => 0; }"))
  }

  test("fold literal comparison with gte and lte") {
    assert(parse("if 5 >= 5 then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
    assert(parse("if 5 <= 5 then {skip} else {skip}") == List(Program.PCmd(Cmd.Skip)))
  }

  test("fold literal comparison with mixed int and float operands") {
    assert(parse("if 5 > 3.0 then {x:=1} else {x:=2}") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(1), 1))))
    assert(parse("if 5.0 > 3 then {x:=1} else {x:=2}") == List(Program.PCmd(Cmd.Assign("x", Expr.Num(1), 1))))
  }

  test("throw on bad struct field name in declaration") {
    assertThrows[RuntimeException](parse("struct S { 5: Int }"))
  }

  test("fold int plus float addition") {
    assert(parse("x := 2 + 3.0") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(5.0), 1))))
  }

  test("fold float plus int addition") {
    assert(parse("x := 2.0 + 3") == List(Program.PCmd(Cmd.Assign("x", Expr.Flt(5.0), 1))))
  }

  test("parseBoolOp throws on a non-comparison token (direct access)") {
    val p = TestParser(List(Token.EOF), StructEnv(), List(1), List(""))
    assertThrows[RuntimeException](p.testParseBoolOp(Token.Add))
  }

  test("Parser cursor: peek/peekNext/advance return EOF and currentLine is -1 past the end") {
    val p = TestParser(List(Token.EOF), StructEnv(), List(1), List(""))
    p.setPos(5)
    assert(p.testPeek() == Token.EOF)
    assert(p.testPeekNext() == Token.EOF)
    assert(p.testAdvance() == Token.EOF)
    assert(p.testCurrentLine() == -1)
    assert(p.testCurrentLineSource() == "")
  }

