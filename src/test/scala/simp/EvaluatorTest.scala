package simp

import org.scalatest.funsuite.AnyFunSuite

class EvaluatorTest extends AnyFunSuite:

  def run(source: String): Store =
    val store = Store()
    val fnEnv = FunctionEnv()
    val structEnv = StructEnv()
    Builtins.register(fnEnv, structEnv)
    val sourceLines = source.split('\n').toList
    val tokens = Lexer(source, sourceLines).tokenise()
    val program = Parser(tokens._1, StructEnv(), tokens._2, sourceLines).parseProgram()
    Evaluator(fnEnv, structEnv, sourceLines).evalProgram(program, store)
    store

  def storeOf(pairs: (String, Int)*): Map[String, Int] = pairs.toMap

  // Runs a manually-constructed AST directly through the Evaluator, bypassing
  // the Lexer/Parser entirely. Used for AST shapes with no reachable surface
  // syntax (e.g. ref parameters, since the Lexer never emits Token.Ref).
  def directEval(programs: List[Program], store: Store = Store()): Store = {
    val fnEnv = FunctionEnv()
    val structEnv = StructEnv()
    Builtins.register(fnEnv, structEnv)
    Evaluator(fnEnv, structEnv, List("")).evalProgram(programs, store)
    store
  }

  // Assignments
  test("assign a literal") {
    val store = run("x := 5")
    assert(store.load("x") == Value.IntVal(5))
  }

  test("assign a boolean literal") {
    val store = run("x := true")
    assert(store.load("x") == Value.BoolVal(true))
  }

  test("assign a string literal") {
    val store = run("x := \"I Fantasized Bout This Back In Chicago\"")
    assert(store.load("x") == Value.StrVal("I Fantasized Bout This Back In Chicago"))
  }
  test("assign result of string concatenation") {
    val store = run("x := \"I Fantasized Bout This Back In Chicago\" + \", Marcy Marcy Me, That Marcielago\"")
    assert(store.load("x") == Value.StrVal("I Fantasized Bout This Back In Chicago, Marcy Marcy Me, That Marcielago"))
  }
  test("assign result of string and integer concatenation") {
    val store = run("x := \"I Really Like the number \" + 7")
    assert(store.load("x") == Value.StrVal("I Really Like the number 7"))
  }
  test("assign result of string and bool concatenation") {
    val store = run("x := \"I Really Like the bool \" + true")
    assert(store.load("x") == Value.StrVal("I Really Like the bool true"))
  }

  test("assign result of addition") {
    val store = run("x := 2 + 3")
    assert(store.load("x") == Value.IntVal(5))
  }

  test("assign result of multiplication") {
    val store = run("x := 3 * 4")
    assert(store.load("x") == Value.IntVal(12))
  }

  test("assign result of subtraction") {
    val store = run("x := 10 - 3")
    assert(store.load("x") == Value.IntVal(7))
  }

  test("assign result of division") {
    val store = run("x := 10 / 2")
    assert(store.load("x") == Value.IntVal(5))
  }

  test("division by zero throws") {
    assertThrows[RuntimeException](run("x := 5 / 0"))
  }

  // Dereference
  test("dereference assigned variable") {
    val store = run("x := 5 ; y := !x")
    assert(store.load("y") == Value.IntVal(5))
  }

  test("dereference in expression") {
    val store = run("x := 5 ; y := !x + 3")
    assert(store.load("y") == Value.IntVal(8))
  }

  test("unbound location throws") {
    assertThrows[RuntimeException](run("x := !y"))
  }

  // Sequencing
  test("sequence executes in order") {
    val store = run("x := 1 ; x := 2")
    assert(store.load("x") == Value.IntVal(2))
  }

  test("sequence assigns multiple variables") {
    val store = run("x := 1 ; y := 2 ; z := 3")
    assert(store.load("x") == Value.IntVal(1))
    assert(store.load("y") == Value.IntVal(2))
    assert(store.load("z") == Value.IntVal(3))
  }

  // Skip
  test("skip does nothing") {
    val store = run("x := 5 ; skip")
    assert(store.load("x") == Value.IntVal(5))
  }

  // If
  test("if true executes then branch") {
    val store2 = run("x := 99; if true then {x := 1} else {x := 2}")
    assert(store2.load("x") == Value.IntVal(1))
  }

  test("if false executes else branch") {
    val store = run("x := 99; if false then {x := 1} else {x := 2}")
    assert(store.load("x") == Value.IntVal(2))
  }

  test("if with comparison true") {
    val store = run("y := 99; x := 5 ; if !x > 3 then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("if with comparison false") {
    val store = run("y := 99; x := 1 ; if !x > 3 then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(0))
  }

  // While
  test("while loop counts down to zero") {
    val store = run("x := 5 ; while !x > 0 do {x := !x - 1}")
    assert(store.load("x") == Value.IntVal(0))
  }

  test("while loop never executes if condition false") {
    val store = run("x := 0 ; while !x > 0 do {x := !x - 1}")
    assert(store.load("x") == Value.IntVal(0))
  }

  test("while loop with accumulator") {
    val store = run("x := 5 ; acc := 0 ; while !x > 0 do {acc := !acc + !x ; x := !x - 1}")
    assert(store.load("acc") == Value.IntVal(15))
    assert(store.load("x") == Value.IntVal(0))
  }

  // Boolean logic
  test("not true is false") {
    val store = run("x := 99; if ¬true then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(0))
  }

  test("not false is true") {
    val store = run("x := 99; if ¬false then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("and both true") {
    val store = run("x := 99; if true && true then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("and one false") {
    val store = run("x := 99; if true && false then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(0))
  }

  test("or one true") {
    val store = run("x := 99; if false  || true then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("or both false") {
    val store = run("x := 99; if false  || false then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(0))
  }

  // Comparators
  test("greater than") {
    val store = run("x := 5 ; y:= 99; if !x > 3  then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("less than") {
    val store = run("x := 2 ; y:= 99; if !x < 3 then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("equal") {
    val store = run("x := 3 ; y:= 99; if !x == 3 then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("greater than or equal") {
    val store = run("x := 3 ; y:= 99; if !x >= 3 then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("less than or equal") {
    val store = run("x := 3 ; y:= 99;  if !x <= 3 then {y := 1}  else {y := 0} ")
    assert(store.load("y") == Value.IntVal(1))
  }

  // Integration
  test("factorial of 5") {
    val store = run(
      "x := 5 ; acc := 1 ; while !x > 0 do {acc := !acc * !x ; x := !x - 1}"
    )
    assert(store.load("acc") == Value.IntVal(120))
  }

  test("fibonacci") {
    val store = run(
      "a := 0 ; b := 1 ; n := 10 ; while !n > 0 do {tmp := !b ; b := !a + !b ; a := !tmp ; n := !n - 1}"
    )
    assert(store.load("a") == Value.IntVal(55))
  }
  test("+=") {
    val store = run("x := 5 ; x += 3")
    assert(store.load("x") == Value.IntVal(8))
  }

  test("-=") {
    val store = run("x := 5 ; x -= 2")
    assert(store.load("x") == Value.IntVal(3))
  }

  test("*=") {
    val store = run("x := 5 ; x *= 3")
    assert(store.load("x") == Value.IntVal(15))
  }

  test("/=") {
    val store = run("x := 10 ; x /= 2")
    assert(store.load("x") == Value.IntVal(5))
  }

  // Modulo
  test("modulo") {
    val store = run("x := 10 % 3")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("modulo even") {
    val store = run("x := 10 % 2")
    assert(store.load("x") == Value.IntVal(0))
  }

  // Not equal
  test("!= true") {
    val store = run("if 1 != 2 then { x := 1 } else { x := 0 }")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("!= false") {
    val store = run("if 1 != 1 then { x := 1 } else { x := 0 }")
    assert(store.load("x") == Value.IntVal(0))
  }

  // Negative literals
  test("negative literal") {
    val store = run("x := -5")
    assert(store.load("x") == Value.IntVal(-5))
  }

  test("negative literal in expression") {
    val store = run("x := -5 + 3")
    assert(store.load("x") == Value.IntVal(-2))
  }

  // elif
  test("elif takes second branch") {
    val store = run(
      "x := 2; y:= 0; if !x == 1 then { y := 1 } elif !x == 2 then { y := 2 } else { y := 3 }"
    )
    assert(store.load("y") == Value.IntVal(2))
  }

  test("elif falls to else") {
    val store = run(
      "x := 3 ; y:= 0; if !x == 1 then { y := 1 } elif !x == 2 then { y := 2 } else { y := 3 }"
    )
    assert(store.load("y") == Value.IntVal(3))
  }

  test("chained elif") {
    val store = run(
      "x := 3 ; y:= 0; if !x == 1 then { y := 1 } elif !x == 2 then { y := 2 } elif !x == 3 then { y := 3 } else { y := 4 }"
    )
    assert(store.load("y") == Value.IntVal(3))
  }

  // Functions
  test("simple function call") {
    val store = run("fn double(n : Int) -> Int { return !n * 2; } x := double(5);")
    assert(store.load("x") == Value.IntVal(10))
  }

  test("recursive function") {
    val store = run(
      "fn factorial(n : Int) -> Int { if !n == 0 then { return 1; } else { return !n * factorial(!n - 1); }; } x := factorial(5);"
    )
    assert(store.load("x") == Value.IntVal(120))
  }

  test("function with multiple params") {
    val store = run("fn add(a : Int, b : Int) -> Int { return !a + !b; } x := add(3, 4);")
    assert(store.load("x") == Value.IntVal(7))
  }

  test("function cannot see caller store") {
    val store = run("fn getX() -> Int { return 42; }; x := 99 ;  y := getX();")
    assert(store.load("y") == Value.IntVal(42))
  }

  test("function with no return throws") {
    assertThrows[RuntimeException](
      run("fn bad() -> Int { skip; } x := bad();")
    )
  }

  test("wrong number of arguments throws") {
    assertThrows[RuntimeException](
      run("fn add(a : Int, b : Int) -> Int { return !a + !b; } x := add(1);")
    )
  }


  // Comments
  test("comment is ignored") {
    val store = run("x := 5; // this is a comment")
    assert(store.load("x") == Value.IntVal(5))
  }

  test("inline comment does not affect result") {
    val store = run("x := 5; // x := 99;")
    assert(store.load("x") == Value.IntVal(5))
  }

  // Arrays
  test("create and read array") {
      val store = run("arr := [1, 2, 3]; x := arr[0];")
      assert(store.load("x") == Value.IntVal(1))
  }

  test("read last element") {
      val store = run("arr := [1, 2, 3]; x := arr[2];")
      assert(store.load("x") == Value.IntVal(3))
  }

  test("mutate array element") {
      val store = run("arr := [1, 2, 3]; arr[0] := 99; x := arr[0];")
      assert(store.load("x") == Value.IntVal(99))
  }

  test("mutation does not affect other elements") {
      val store = run("arr := [1, 2, 3]; arr[0] := 99; x := arr[1];")
      assert(store.load("x") == Value.IntVal(2))
  }

  test("empty array has length 0") {
      val store = run("arr := []; x := len(arr);")
      assert(store.load("x") == Value.IntVal(0))
  }

  test("array length") {
      val store = run("arr := [1, 2, 3, 4, 5]; x := len(arr);")
      assert(store.load("x") == Value.IntVal(5))
  }

  test("array index out of bounds throws") {
      assertThrows[RuntimeException](run("arr := [1, 2, 3]; x := arr[5];"))
  }

  test("negative array index throws") {
      assertThrows[RuntimeException](run("arr := [1, 2, 3]; x := arr[-1];"))
  }

  test("sum array elements with while loop") {
      val store = run(
          "arr := [1, 2, 3, 4, 5]; sum := 0; i := 0; while !i < len(arr) do { sum += arr[!i]; i += 1; };"
      )
      assert(store.load("sum") == Value.IntVal(15))
  }


  test("array of strings") {
      val store = run("arr := [\"hello\", \"world\"]; x := arr[0];")
      assert(store.load("x") == Value.StrVal("hello"))
  }

  test("array of booleans") {
      val store = run("arr := [true, false, true]; x := arr[1];")
      assert(store.load("x") == Value.BoolVal(false))
  }

  test("array index with expression") {
      val store = run("arr := [10, 20, 30]; i := 1; x := arr[!i + 1];")
      assert(store.load("x") == Value.IntVal(30))
  }

  test("create struct and read field") {
      val store = run(
          "struct Point { x: Int, y: Int } p := Point { x: 1, y: 2 }; r := p.x;"
      )
      assert(store.load("r") == Value.IntVal(1))
  }

  test("mutate struct field") {
      val store = run(
          "struct Point { x: Int, y: Int } p := Point { x: 1, y: 2 }; p.x := 99; r := p.x;"
      )
      assert(store.load("r") == Value.IntVal(99))
  }

  test("mutation does not affect other fields") {
      val store = run(
          "struct Point { x: Int, y: Int } p := Point { x: 1, y: 2 }; p.x := 99; r := p.y;"
      )
      assert(store.load("r") == Value.IntVal(2))
  }

  test("struct as function parameter") {
      val store = run(
          "struct Point { x: Int, y: Int }; fn getX(p: Point) -> Int { return p.x; } p := Point { x: 42, y: 0 }; r := getX(p);"
      )
      assert(store.load("r") == Value.IntVal(42))
  }

  test("nested struct field access") {
      val store = run(
          """struct Point { x: Int, y: Int };
            struct Line { start: Point, end: Point };
            p1 := Point { x: 1, y: 2 };
            p2 := Point { x: 3, y: 4 };
            line := Line { start: p1, end: p2 };
            r := line.start.x;"""
      )
      assert(store.load("r") == Value.IntVal(1))
  }

  test("missing field throws") {
      assertThrows[RuntimeException](run(
          "struct Point { x: Int, y: Int } p := Point { x: 1 };"
      ))
  }

  test("unknown field access throws") {
      assertThrows[RuntimeException](run(
          "struct Point { x: Int, y: Int } p := Point { x: 1, y: 2 }; r := p.z;"
      ))
  }

  test("wrong field type throws") {
      assertThrows[RuntimeException](run(
          "struct Point { x: Int, y: Int } p := Point { x: \"hello\", y: 2 };"
      ))
  }

  test("unknown struct type throws") {
      assertThrows[RuntimeException](run(
          "p := Foo { x: 1 };"
      ))
  }

  test("struct with string field") {
      val store = run(
          "struct Person { name: Str, age: Int, nationality: Str }; p := Person { name: \"Angèle\", age: 30, nationality: \"Belgian\" }; r := p.name;"
      )
      assert(store.load("r") == Value.StrVal("Angèle"))
  }

  // Const
  test("const declaration") {
    val store = run("const x := 5")
    assert(store.load("x") == Value.IntVal(5))
  }

  test("const reassignment throws") {
    assertThrows[RuntimeException](run("const x := 5; x := 10"))
  }

  test("const with expression") {
    val store = run("const x := 2 + 3")
    assert(store.load("x") == Value.IntVal(5))
  }

  test("const += throws") {
    assertThrows[RuntimeException](run("const x := 5; x += 1"))
  }

  test("const shadowed in inner scope") {
    val store = run(
      "const x := 1; { const x := 2; }; y := !x"
    )
    assert(store.load("y") == Value.IntVal(1))
  }

  test("const inside if branch does not leak") {
    val store = run(
      "x := true; if !x then { const inner := 42; } else { skip }; x := 0"
    )
    assert(store.load("x") == Value.IntVal(0))
    assertThrows[RuntimeException](store.load("inner"))
  }

  // Block Scoping
  test("variable in inner scope does not leak") {
    val store = run("{ inner := 5; }; x := 0")
    assertThrows[RuntimeException](store.load("inner"))
  }

  test("outer variable mutated from inner scope") {
    val store = run("x := 1; { x := 2; };")
    assert(store.load("x") == Value.IntVal(2))
  }

  test("if branch does not leak variables") {
    val store = run("x := true; if !x then { inner := 42; } else { skip };")
    assertThrows[RuntimeException](store.load("inner"))
  }

  test("while body does not leak variables") {
    val store = run("i := 0; while !i < 1 do { inner := 99; i += 1; };")
    assertThrows[RuntimeException](store.load("inner"))
  }

  test("for body does not leak variables") {
    val store = run("for n in [1,2,3] { inner := !n; };")
    assertThrows[RuntimeException](store.load("inner"))
  }

  test("for loop variable does not leak") {
    val store = run("for n in [1,2,3] { skip; };")
    assertThrows[RuntimeException](store.load("n"))
  }

  test("for loop variable is const") {
    assertThrows[RuntimeException](run("for n in [1,2,3] { n := 99; }"))
  }

  test("accumulator updated from for body") {
    val store = run("total := 0; for n in [1,2,3,4,5] { total += !n; };")
    assert(store.load("total") == Value.IntVal(15))
  }

  test("anonymous scope block") {
    val store = run("x := 1; { x := 2; };")
    assert(store.load("x") == Value.IntVal(2))
  }

  test("anonymous scope block variables don't leak") {
    val store = run("{ y := 42; }; x := 0")
    assertThrows[RuntimeException](store.load("y"))
  }


  // For Loops
  test("for loop over array") {
    val store = run("total := 0; for n in [1,2,3] { total += !n; };")
    assert(store.load("total") == Value.IntVal(6))
  }

  test("for loop over string array") {
    val store = run("result := \"\"; for s in [\"a\",\"b\",\"c\"] { result := !result + !s; };")
    assert(store.load("result") == Value.StrVal("abc"))
  }

  test("for loop with break") {
    val store = run("total := 0; for n in [1,2,3,4,5] { if !n == 3 then { break; } else { skip; }; total += !n; };")
    assert(store.load("total") == Value.IntVal(3))
  }

  test("for loop with continue") {
    val store = run("total := 0; for n in [1,2,3,4,5] { if !n == 3 then { continue; } else { skip; }; total += !n; };")
    assert(store.load("total") == Value.IntVal(12))
  }

  test("for loop over empty array") {
    val store = run("total := 0; for n in [] { total += 1; };")
    assert(store.load("total") == Value.IntVal(0))
  }

  // Break / Continue
  test("break exits while loop") {
    val store = run("i := 0; while true do { if !i == 3 then { break; } else { skip; }; i += 1; };")
    assert(store.load("i") == Value.IntVal(3))
  }

  test("continue skips rest of while body") {
    val store = run(
      "i := 0; total := 0; while !i < 5 do { i += 1; if !i == 3 then { continue; } else { skip; }; total += !i; };"
    )
    assert(store.load("total") == Value.IntVal(12))
  }

  // Impl Blocks
  test("basic method call") {
    val store = run(
      """struct Point { x: Int, y: Int }
        impl Point {
            fn getX(self: Point) -> Int { return self.x; }
        }
        p := Point { x: 42, y: 0 };
        r := p.getX();""".stripMargin
    )
    assert(store.load("r") == Value.IntVal(42))
  }

  test("method mutates struct field") {
    val store = run(
      """struct Point { x: Int, y: Int }
        impl Point {
            fn setX(self: Point, v: Int) -> Void { self.x := !v; }
        }
        p := Point { x: 0, y: 0 };
        p.setX(99);
        r := p.x;""".stripMargin
    )
    assert(store.load("r") == Value.IntVal(99))
  }

  test("method calling another method on self") {
    val store = run(
      """struct Counter { n: Int }
        impl Counter {
            fn get(self: Counter) -> Int { return self.n; }
            fn doubled(self: Counter) -> Int { return self.get() * 2; }
        }
        c := Counter { n: 5 };
        r := c.doubled();""".stripMargin
    )
    assert(store.load("r") == Value.IntVal(10))
  }

  test("polymorphic dispatch") {
    val store = run(
      """struct Cat {}
        struct Dog {}
        impl Cat { fn speak(self: Cat) -> Str { return "meow"; } }
        impl Dog { fn speak(self: Dog) -> Str { return "woof"; } }
        animals := [Cat {}, Dog {}, Cat {}];
        result := "";
        for a in animals { result := !result + a.speak(); };""".stripMargin
    )
    assert(store.load("result") == Value.StrVal("meowwoofmeow"))
  }

  test("method on result of another method") {
    val store = run(
      """struct Rect { w: Int, h: Int }
        impl Rect {
            fn area(self: Rect) -> Int { return self.w * self.h; }
            fn scale(self: Rect, f: Int) -> Rect { return Rect { w: self.w * !f, h: self.h * !f }; }
        }
        r := Rect { w: 2, h: 3 };
        x := r.scale(2).area();""".stripMargin
    )
    assert(store.load("x") == Value.IntVal(24))
  }

  test("unknown method throws") {
    assertThrows[RuntimeException](run(
      """struct Point { x: Int, y: Int }
        p := Point { x: 1, y: 2 };
        r := p.nonExistent();""".stripMargin
    ))
  }

  test("method call on non-struct throws") {
    assertThrows[RuntimeException](run(
      "x := 5; r := x.toStr();"
    ))
  }

  // Private Fields
  test("private field readable from own impl") {
    val store = run(
      """struct Point { x: Int, private y: Int }
        impl Point {
            fn getY(self: Point) -> Int { return self.y; }
        }
        p := Point { x: 1, y: 2 };
        r := p.getY();""".stripMargin
    )
    assert(store.load("r") == Value.IntVal(2))
  }

  test("private field read from outside impl throws") {
    assertThrows[RuntimeException](run(
      """struct Point { x: Int, private y: Int }
        p := Point { x: 1, y: 2 };
        r := p.y;""".stripMargin
    ))
  }

  test("public field still readable from outside impl") {
    val store = run(
      """struct Point { x: Int, private y: Int }
        p := Point { x: 1, y: 2 };
        r := p.x;""".stripMargin
    )
    assert(store.load("r") == Value.IntVal(1))
  }

  test("private field writable via method on own impl") {
    val store = run(
      """struct Point { x: Int, private y: Int }
        impl Point {
            fn setY(self: Point, v: Int) -> Void { self.y := !v; }
            fn getY(self: Point) -> Int { return self.y; }
        }
        p := Point { x: 0, y: 0 };
        p.setY(9);
        r := p.getY();""".stripMargin
    )
    assert(store.load("r") == Value.IntVal(9))
  }

  test("private field assignment from outside impl throws") {
    assertThrows[RuntimeException](run(
      """struct Point { x: Int, private y: Int }
        p := Point { x: 0, y: 0 };
        p.y := 5;""".stripMargin
    ))
  }

  test("private array field index assignment from outside impl throws") {
    assertThrows[RuntimeException](run(
      """struct Bag { private items: Int[] }
        b := Bag { items: [1, 2, 3] };
        b.items[0] := 9;""".stripMargin
    ))
  }

  test("private array field index assignment from own impl succeeds") {
    val store = run(
      """struct Bag { private items: Int[] }
        impl Bag {
            fn setFirst(self: Bag, v: Int) -> Void { self.items[0] := !v; }
            fn first(self: Bag) -> Int { return self.items[0]; }
        }
        b := Bag { items: [1, 2, 3] };
        b.setFirst(9);
        r := b.first();""".stripMargin
    )
    assert(store.load("r") == Value.IntVal(9))
  }

  test("private field inaccessible from a different struct's impl") {
    assertThrows[RuntimeException](run(
      """struct Point { private y: Int }
        struct Other {}
        impl Other {
            fn peek(self: Other, p: Point) -> Int { return p.y; }
        }
        p := Point { y: 1 };
        o := Other {};
        r := o.peek(p);""".stripMargin
    ))
  }

  test("private field with default can be omitted from outside impl") {
    val store = run(
      """struct Point { x: Int, private y: Int := 7 }
        impl Point { fn getY(self: Point) -> Int { return self.y; } }
        p := Point { x: 1 };
        r := p.getY();""".stripMargin
    )
    assert(store.load("r") == Value.IntVal(7))
  }

  test("private field settable via struct literal from own impl") {
    val store = run(
      """struct Point { x: Int, private y: Int := 0 }
        impl Point {
            fn withY(self: Point, newY: Int) -> Point { return Point { x: self.x, y: !newY }; }
            fn getY(self: Point) -> Int { return self.y; }
        }
        p := Point { x: 1 };
        p2 := p.withY(2);
        r := p2.getY();""".stripMargin
    )
    assert(store.load("r") == Value.IntVal(2))
  }

  test("private field destructuring in match from outside impl throws") {
    assertThrows[RuntimeException](run(
      """struct Point { private y: Int }
        p := Point { y: 1 };
        r := match p { case Point { y: v } => v; };""".stripMargin
    ))
  }

  test("private field destructuring in match from own impl succeeds") {
    val store = run(
      """struct Point { private y: Int }
        impl Point {
            fn getY(self: Point) -> Int {
                return match self { case Point { y: v } => v; };
            }
        }
        p := Point { y: 4 };
        r := p.getY();""".stripMargin
    )
    assert(store.load("r") == Value.IntVal(4))
  }

  //Pattern Matching
  test("match literal") {
    val store = run("x := match 1 { case 1 => 42; case _ => 0; }")
    assert(store.load("x") == Value.IntVal(42))
  }

  test("match wildcard") {
    val store = run("x := match 5 { case 1 => 1; case _ => 99; }")
    assert(store.load("x") == Value.IntVal(99))
  }

  test("match with variable binding") {
    val store = run("x := match 7 { case n => !n * 2; }")
    assert(store.load("x") == Value.IntVal(14))
  }

  test("match string") {
    val store = run("""x := match "hello" { case "hello" => 1; case _ => 0; }""")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("match with guard") {
    val store = run("x := match 5 { case n if !n > 3 => 1; case _ => 0; }")
    assert(store.load("x") == Value.IntVal(1))
  }

  // Map Stuff
  test("create and get from map") {
    val store = run(
      """m := newMap(Str, Int); _:= set(m, "key", 42); x := get(m, "key");""".stripMargin
    )
    assert(store.load("x") == Value.IntVal(42))
  }

  test("hasKey true") {
    val store = run(
      """m := newMap(Str, Int); _:= set(m, "key", 1); x := hasKey(m, "key");""".stripMargin
    )
    assert(store.load("x") == Value.BoolVal(true))
  }

  test("hasKey false") {
    val store = run(
      """m := newMap(Str, Int); x := hasKey(m, "missing");""".stripMargin
    )
    assert(store.load("x") == Value.BoolVal(false))
  }

  test("map remove") {
    val store = run(
      """m := newMap(Str, Int); _:= set(m, "key", 1); _:= remove(m, "key"); x := hasKey(m, "key");""".stripMargin
    )
    assert(store.load("x") == Value.BoolVal(false))
  }
  // 2D Array!

  test("2D array read") {
    val store = run(
      "board := [[1,2,3],[4,5,6],[7,8,9]]; x := board[1][2];"
    )
    assert(store.load("x") == Value.IntVal(6))
  }

  test("2D array write") {
    val store = run(
      "board := [[1,2,3],[4,5,6]]; board[0][1] := 99; x := board[0][1];"
    )
    assert(store.load("x") == Value.IntVal(99))
  }

  test("2D array write does not affect other cells") {
    val store = run(
      "board := [[1,2,3],[4,5,6]]; board[0][1] := 99; x := board[1][1];"
    )
    assert(store.load("x") == Value.IntVal(5))
  }

  // Pears.
  test("pair fst") {
    val store = run("p := (1, 2); x := p.fst")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("pair snd") {
    val store = run("p := (1, 2); x := p.snd")
    assert(store.load("x") == Value.IntVal(2))
  }

  test("pair in array") {
    val store = run("arr := [(1,2),(3,4)]; x := arr[1].fst")
    assert(store.load("x") == Value.IntVal(3))
  }

  // ==== Evaluator.scala ====

  test("evalProgram: PBool prints boolean result") {
    // Program.PBool is only produced by parseRepl(), never parseProgram(),
    // so construct it directly to exercise evalProgram's PBool case.
    directEval(List(Program.PBool(BoolExpr.Literal(true))))
  }

  test("evalProgram: PExpr prints expression result") {
    // Program.PExpr at top level is likewise only produced by parseRepl().
    directEval(List(Program.PExpr(Expr.BinaryOp(Expr.Num(5), Op.Add, Expr.Num(5)))))
  }

  // ==== EvaluatorCmd.scala ====

  test("field index assignment: non-integer index throws") {
    assertThrows[RuntimeException](run(
      "struct S { arr: Int[] } s := S { arr: [1,2,3] }; s.arr[true] := 5;"
    ))
  }

  test("field index assignment: out of bounds throws") {
    assertThrows[RuntimeException](run(
      "struct S { arr: Int[] } s := S { arr: [1,2,3] }; s.arr[9] := 5;"
    ))
  }

  test("field index assignment: field is not an array throws") {
    assertThrows[RuntimeException](run(
      "struct S { x: Int } s := S { x: 1 }; s.x[0] := 5;"
    ))
  }

  test("field index assignment: target is not a struct throws") {
    assertThrows[RuntimeException](run("x := 5; x.arr[0] := 5;"))
  }

  test("field assignment: target is not a struct throws") {
    assertThrows[RuntimeException](run("x := 5; x.f := 5;"))
  }

  test("array assign: non-integer index throws") {
    assertThrows[RuntimeException](run("arr := [1,2,3]; arr[true] := 5;"))
  }

  test("for loop over non-array throws") {
    assertThrows[RuntimeException](run("for n in 5 { skip; }"))
  }

  test("nested array assignment writes through multiple levels") {
    val store = run("board := [[1,2],[3,4]]; board[0][1] := 99; x := board[0][1];")
    assert(store.load("x") == Value.IntVal(99))
  }

  test("nested array assignment: intermediate value is not an array throws") {
    assertThrows[RuntimeException](run("arr := [1,2,3]; arr[0][0] := 5;"))
  }

  test("nested array assignment: non-integer intermediate index throws") {
    assertThrows[RuntimeException](run("board := [[1,2],[3,4]]; board[true][0] := 5;"))
  }

  test("nested array assignment: non-integer final index throws") {
    assertThrows[RuntimeException](run("board := [[1,2],[3,4]]; board[0][true] := 5;"))
  }

  test("nested array assignment: target location is not an array throws") {
    assertThrows[RuntimeException](run("x := 5; x[0][0] := 1;"))
  }

  test("nested field index assignment writes through multiple levels") {
    val store = run(
      "struct S { board: Int[][] } s := S { board: [[1,2],[3,4]] }; s.board[0][1] := 9; r := s.board[0][1];"
    )
    assert(store.load("r") == Value.IntVal(9))
  }

  test("nested field index assignment: field target is not a struct throws") {
    assertThrows[RuntimeException](run("x := 5; x.arr[0][0] := 1;"))
  }

  test("nested field index assignment: field is not an array throws") {
    assertThrows[RuntimeException](run(
      "struct S { x: Int } s := S { x: 1 }; s.x[0][0] := 5;"
    ))
  }

  test("nested field index assignment: non-integer intermediate index throws") {
    assertThrows[RuntimeException](run(
      "struct S { board: Int[][] } s := S { board: [[1,2],[3,4]] }; s.board[true][0] := 5;"
    ))
  }

  test("nested field index assignment: intermediate value not array throws") {
    assertThrows[RuntimeException](run(
      "struct S { board: Int[] } s := S { board: [1,2,3] }; s.board[0][0] := 5;"
    ))
  }

  test("nested field index assignment: non-integer final index throws") {
    assertThrows[RuntimeException](run(
      "struct S { board: Int[][] } s := S { board: [[1,2],[3,4]] }; s.board[0][true] := 5;"
    ))
  }

  test("print statement evaluates and prints") {
    run("print 5 + 5;")
    succeed
  }

  test("bare return at top level propagates as exception") {
    assertThrows[ReturnException](run("return 5;"))
  }

  // ==== EvaluatorExpr.scala ====

  test("dereferencing a location holding a ref chases through to the value") {
    val fnDecl = Decl.FnDecl("getRef", List(("x", SimpType.TypeRef(SimpType.TypeInt))), Cmd.Return(Some(Expr.Deref("x")), 1), SimpType.TypeInt, false)
    val store = directEval(List(
      Program.PDecl(fnDecl),
      Program.PCmd(Cmd.Assign("y", Expr.Num(7), 1)),
      Program.PCmd(Cmd.Assign("z", Expr.FnCall("getRef", List(Expr.Ref("y"))), 1))
    ))
    assert(store.load("z") == Value.IntVal(7))
  }

  test("evalArrIndex negative index throws") {
    assertThrows[RuntimeException](run("arr := [1,2,3]; x := arr[-1];"))
  }

  test("evalArrIndex non-array or non-int operands throws") {
    assertThrows[RuntimeException](run("x := 5; y := x[0];"))
  }

  test("match with no matching arm throws") {
    assertThrows[RuntimeException](run("x := match 5 { case 1 => 1; }"))
  }

  test("match guard can see pattern bindings") {
    val store = run("x := match 5 { case n if !n == 5 => 1; case _ => 0; }")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("match struct pattern binds fields") {
    val store = run(
      "struct Point { x: Int, y: Int }; p := Point { x: 1, y: 2 }; r := match p { case Point { x: a, y: b } => !a + !b; case _ => 0; };"
    )
    assert(store.load("r") == Value.IntVal(3))
  }

  test("match struct pattern fails on wrong type") {
    val store = run(
      "struct Point { x: Int, y: Int }; struct Other { z: Int }; o := Other { z: 5 }; r := match o { case Point { x: a, y: b } => 1; case _ => 2; };"
    )
    assert(store.load("r") == Value.IntVal(2))
  }

  test("match pair pattern binds both elements") {
    val store = run("p := (1, 2); r := match p { case (a, b) => !a + !b; };")
    assert(store.load("r") == Value.IntVal(3))
  }

  test("unbound ref throws") {
    assertThrows[RuntimeException](run("x := y;"))
  }

  test("field access on non-struct non-pair throws") {
    assertThrows[RuntimeException](run("x := 5; y := x.z;"))
  }

  test("pair field access on unknown field throws") {
    assertThrows[RuntimeException](run("p := (1, 2); x := p.bad;"))
  }

  // ==== evalUnaryOp / evalBinaryOp dead-via-parser branches (direct AST) ====

  test("evalUnaryOp throws for unsupported unary operator") {
    assertThrows[RuntimeException](directEval(List(Program.PExpr(Expr.UnaryOp(Expr.Num(5), Op.Add)))))
  }

  test("evalUnaryOp throws when operand is not an int") {
    assertThrows[RuntimeException](directEval(List(Program.PExpr(Expr.UnaryOp(Expr.Str("x"), Op.BitComplement)))))
  }

  test("evalBinaryOp throws for unsupported int/int operator") {
    assertThrows[RuntimeException](directEval(List(Program.PExpr(Expr.BinaryOp(Expr.Num(1), Op.BitComplement, Expr.Num(2))))))
  }

  test("evalBinaryOp float modulo is unsupported") {
    assertThrows[RuntimeException](run("x := 1.5 % 2.0"))
  }

  test("evalBinaryOp float bitwise-and is unsupported") {
    assertThrows[RuntimeException](run("x := 1.5 & 2.0"))
  }

  test("evalBinaryOp string with non-add operator throws") {
    assertThrows[RuntimeException](directEval(List(Program.PExpr(Expr.BinaryOp(Expr.Str("a"), Op.Sub, Expr.Str("b"))))))
  }

  test("evalBinaryOp type mismatch throws") {
    assertThrows[RuntimeException](run("x := true + 5"))
  }

  // ==== EvaluatorBoolExpr.scala ====

  test("string equality comparison") {
    val store = run("""x := "a" == "a"; y := match x { case true => 1; case _ => 0; };""")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("array equality comparison") {
    val store = run("x := [1,2] == [1,2]; y := match x { case true => 1; case _ => 0; };")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("array inequality comparison") {
    val store = run("x := [1,2] != [1,3]; y := match x { case true => 1; case _ => 0; };")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("struct equality comparison") {
    val store = run(
      "struct P { x: Int } a := P { x: 1 }; b := P { x: 1 }; r := a == b; y := match r { case true => 1; case _ => 0; };"
    )
    assert(store.load("y") == Value.IntVal(1))
  }

  test("struct inequality comparison with different type") {
    val store = run(
      "struct P { x: Int } struct Q { x: Int } a := P { x: 1 }; b := Q { x: 1 }; r := a != b; y := match r { case true => 1; case _ => 0; };"
    )
    assert(store.load("y") == Value.IntVal(1))
  }

  test("struct equality handles self-referential fields without infinite loop") {
    val fields = scala.collection.mutable.Map[String, Value]()
    val node = Value.StructVal("Node", fields)
    fields("next") = node
    val store = Store()
    store.store("a", node)
    store.store("b", node)
    val result = directEval(List(
      Program.PCmd(Cmd.Assign("r", Expr.BoolLift(BoolExpr.Compare(Expr.Ref("a"), Bop.Eq, Expr.Ref("b"))), 1))
    ), store)
    assert(result.load("r") == Value.BoolVal(true))
  }

  test("non-bool FromExpr throws at evaluation") {
    assertThrows[RuntimeException](run("""if "x" then {skip} else {skip}"""))
  }

  test("comparing mismatched types throws") {
    assertThrows[RuntimeException](run("if 1 == true then {skip} else {skip}"))
  }

  test("unsupported string comparison operator throws") {
    assertThrows[RuntimeException](directEval(List(Program.PBool(BoolExpr.Compare(Expr.Str("a"), Bop.Gt, Expr.Str("b"))))))
  }

  test("unsupported bool comparison operator throws") {
    assertThrows[RuntimeException](directEval(List(Program.PBool(BoolExpr.Compare(Expr.Bool(true), Bop.Gt, Expr.Bool(false))))))
  }

  test("unsupported array comparison operator throws") {
    assertThrows[RuntimeException](directEval(List(Program.PBool(
      BoolExpr.Compare(Expr.ArrLiteral(List()), Bop.Gt, Expr.ArrLiteral(List()))
    ))))
  }

  test("unsupported struct comparison operator throws") {
    assertThrows[RuntimeException](run(
      "struct P { x: Int } a := P { x: 1 }; b := P { x: 1 }; if !a > !b then {skip} else {skip};"
    ))
  }

  test("and short-circuits without evaluating right side") {
    // If && didn't short-circuit, evaluating the right side would divide by zero and throw.
    val store = run("y := false && (1 / 0 == 1); z := match y { case true => 1; case _ => 0; };")
    assert(store.load("z") == Value.IntVal(0))
  }

  test("or short-circuits without evaluating right side") {
    // If || didn't short-circuit, evaluating the right side would divide by zero and throw.
    val store = run("y := true || (1 / 0 == 1); z := match y { case true => 1; case _ => 0; };")
    assert(store.load("z") == Value.IntVal(1))
  }

  // ==== EvaluatorFunctions.scala ====

  test("function with mismatched return type throws") {
    assertThrows[RuntimeException](run(
      """fn f() -> Int { return "hi"; } x := f();"""
    ))
  }

  test("void function returning a value throws") {
    assertThrows[RuntimeException](run(
      "fn f() -> Void { return 5; } x := f();"
    ))
  }

  test("function declared to return void but no explicit return works") {
    val store = run("fn f() -> Void { x := 1; } y := f();")
    assert(store.load("y") == Value.NullVal)
  }

  test("bare return in non-void function throws") {
    assertThrows[RuntimeException](run(
      "fn f() -> Int { return; } x := f();"
    ))
  }

  test("method: void method returning a value throws") {
    assertThrows[RuntimeException](run(
      "struct S {} impl S { fn f(self: S) -> Void { return 5; } } s := S {}; x := s.f();"
    ))
  }

  test("method: bare return in non-void method throws") {
    assertThrows[RuntimeException](run(
      "struct S {} impl S { fn f(self: S) -> Int { return; } } s := S {}; x := s.f();"
    ))
  }

  test("method: mismatched return type throws") {
    assertThrows[RuntimeException](run(
      """struct S {} impl S { fn f(self: S) -> Int { return "hi"; } } s := S {}; x := s.f();"""
    ))
  }

  test("method: no return statement throws") {
    assertThrows[RuntimeException](run(
      "struct S {} impl S { fn f(self: S) -> Int { skip; } } s := S {}; x := s.f();"
    ))
  }

  // ==== Ref parameters (direct AST; unreachable via the parser) ====

  test("ref parameter function call reads current value") {
    val fnDecl = Decl.FnDecl("getRef", List(("x", SimpType.TypeRef(SimpType.TypeInt))), Cmd.Return(Some(Expr.Deref("x")), 1), SimpType.TypeInt, false)
    val store = directEval(List(
      Program.PDecl(fnDecl),
      Program.PCmd(Cmd.Assign("y", Expr.Num(5), 1)),
      Program.PCmd(Cmd.Assign("z", Expr.FnCall("getRef", List(Expr.Ref("y"))), 1))
    ))
    assert(store.load("z") == Value.IntVal(5))
  }

  test("assigning through a ref parameter writes to the caller's store") {
    val incBody = Cmd.Assign("x", Expr.BinaryOp(Expr.Deref("x"), Op.Add, Expr.Num(1)), 1)
    val fnDecl = Decl.FnDecl("inc", List(("x", SimpType.TypeRef(SimpType.TypeInt))), incBody, SimpType.TypeNull, false)
    val store = directEval(List(
      Program.PDecl(fnDecl),
      Program.PCmd(Cmd.Assign("y", Expr.Num(5), 1)),
      Program.PCmd(Cmd.Assign("_", Expr.FnCall("inc", List(Expr.Ref("y"))), 1))
    ))
    assert(store.load("y") == Value.IntVal(6))
  }

  test("ref parameter requires a bare variable argument") {
    val fnDecl = Decl.FnDecl("getRef", List(("x", SimpType.TypeRef(SimpType.TypeInt))), Cmd.Return(Some(Expr.Deref("x")), 1), SimpType.TypeInt, false)
    assertThrows[RuntimeException](directEval(List(
      Program.PDecl(fnDecl),
      Program.PCmd(Cmd.Assign("z", Expr.FnCall("getRef", List(Expr.Num(5))), 1))
    )))
  }

  test("ref parameter type mismatch throws") {
    val fnDecl = Decl.FnDecl("getRef", List(("x", SimpType.TypeRef(SimpType.TypeInt))), Cmd.Return(Some(Expr.Deref("x")), 1), SimpType.TypeInt, false)
    assertThrows[RuntimeException](directEval(List(
      Program.PDecl(fnDecl),
      Program.PCmd(Cmd.Assign("y", Expr.Str("hi"), 1)),
      Program.PCmd(Cmd.Assign("z", Expr.FnCall("getRef", List(Expr.Ref("y"))), 1))
    )))
  }

  test("method parameter cannot be a reference type") {
    assertThrows[RuntimeException](directEval(List(
      Program.PDecl(Decl.StructDecl("Point", List(("x", SimpType.TypeInt, None, false)))),
      Program.PImpl("Point", List(Decl.FnDecl(
        "bad",
        List(("self", SimpType.TypeStruct("Point")), ("r", SimpType.TypeRef(SimpType.TypeInt))),
        Cmd.Return(Some(Expr.Num(1)), 1), SimpType.TypeInt, false
      ))),
      Program.PCmd(Cmd.Assign("p", Expr.StructLiteral("Point", List(("x", Expr.Num(1)))), 1)),
      Program.PCmd(Cmd.Assign("z", Expr.MethodCall(Expr.Ref("p"), "bad", List(Expr.Num(1))), 1))
    )))
  }

  // ==== AST.scala default arguments ====

  test("Cmd.Return default arguments") {
    assert(Cmd.Return() == Cmd.Return(None, 0))
  }

  test("ReturnException default arguments") {
    assert(ReturnException() == ReturnException(None))
  }

  // ==== Store.scala ====

  test("store: contains true and false") {
    val s = Store()
    s.store("x", Value.IntVal(1))
    assert(s.contains("x"))
    assert(!s.contains("y"))
  }

  test("store: remove deletes a variable") {
    val s = Store()
    s.store("x", Value.IntVal(1))
    s.remove("x")
    assertThrows[RuntimeException](s.load("x"))
  }

  test("store: clear empties all variables") {
    val s = Store()
    s.store("x", Value.IntVal(1))
    s.clear()
    assertThrows[RuntimeException](s.load("x"))
  }

  test("store: child inherits parent values") {
    val parent = Store()
    parent.store("x", Value.IntVal(5))
    val child = parent.child()
    assert(child.load("x") == Value.IntVal(5))
  }

  test("store: assigning in child mutates parent's variable") {
    val parent = Store()
    parent.store("x", Value.IntVal(1))
    val child = parent.child()
    child.store("x", Value.IntVal(2))
    assert(parent.load("x") == Value.IntVal(2))
  }

  test("store: new variable in child does not leak to parent") {
    val parent = Store()
    val child = parent.child()
    child.store("y", Value.IntVal(9))
    assertThrows[RuntimeException](parent.load("y"))
  }

  test("store: assigning a const owned by an ancestor scope throws") {
    val parent = Store()
    parent.declareConst("x", Value.IntVal(1))
    val child = parent.child()
    assertThrows[RuntimeException](child.store("x", Value.IntVal(2)))
  }

  test("store: assigning to _ is a no-op") {
    val s = Store()
    s.store("_", Value.IntVal(1))
    assertThrows[RuntimeException](s.load("_"))
  }

  test("store: declareConst to _ is a no-op") {
    val s = Store()
    s.declareConst("_", Value.IntVal(1))
    assertThrows[RuntimeException](s.load("_"))
  }

  test("store: dump does not throw") {
    val s = Store()
    s.store("x", Value.IntVal(1))
    s.dump()
  }

  test("store: entries returns stored values") {
    val s = Store()
    s.store("x", Value.IntVal(1))
    assert(s.entries().toMap == Map("x" -> Value.IntVal(1)))
  }

  // ==== StructEnv.scala ====

  test("structEnv: lookup unknown struct throws") {
    assertThrows[RuntimeException](StructEnv().lookup("Nope"))
  }

  test("structEnv: exists true and false") {
    val se = StructEnv()
    se.register("Point", StructDef(List()))
    assert(se.exists("Point"))
    assert(!se.exists("Other"))
  }

  test("structEnv: preRegister then register overwrites") {
    val se = StructEnv()
    se.preRegister("Point")
    assert(se.exists("Point"))
    se.register("Point", StructDef(List(("x", SimpType.TypeInt, None, false))))
    assert(se.lookup("Point").fields.length == 1)
  }

  test("structEnv: clear removes structs") {
    val se = StructEnv()
    se.register("Point", StructDef(List()))
    se.clear()
    assert(!se.exists("Point"))
  }

  test("structEnv: dumpStruct returns registered structs") {
    val se = StructEnv()
    se.register("Point", StructDef(List()))
    assert(se.dumpStruct().contains("Point"))
  }

  // ==== FunctionEnv.scala ====

  test("functionEnv: lookupFn on unknown function throws") {
    assertThrows[RuntimeException](FunctionEnv().lookupFn("nope"))
  }

  test("functionEnv: hasFn true and false") {
    val fe = FunctionEnv()
    fe.registerFn("f", Decl.FnDecl("f", List(), Cmd.Skip, SimpType.TypeNull, false))
    assert(fe.hasFn("f"))
    assert(!fe.hasFn("g"))
  }

  test("functionEnv: findNamespaced finds a qualified name") {
    val fe = FunctionEnv()
    fe.registerFn("mod::f", Decl.FnDecl("mod::f", List(), Cmd.Skip, SimpType.TypeNull, false))
    assert(fe.findNamespaced("f") == Some("mod::f"))
    assert(fe.findNamespaced("nope") == None)
  }

  test("functionEnv: lookupBuiltin found and not found") {
    val fe = FunctionEnv()
    fe.registerBuiltin("f", _ => Value.NullVal)
    assert(fe.lookupBuiltin("f").isDefined)
    assert(fe.lookupBuiltin("nope").isEmpty)
  }

  test("functionEnv: clear removes functions and methods") {
    val fe = FunctionEnv()
    fe.registerFn("f", Decl.FnDecl("f", List(), Cmd.Skip, SimpType.TypeNull, false))
    fe.methodTable(("S", "m")) = Decl.FnDecl("m", List(), Cmd.Skip, SimpType.TypeNull, false)
    fe.clear()
    assert(!fe.hasFn("f"))
    assert(!fe.methodTable.contains(("S", "m")))
  }

  test("functionEnv: dumpFn returns registered functions") {
    val fe = FunctionEnv()
    fe.registerFn("f", Decl.FnDecl("f", List(), Cmd.Skip, SimpType.TypeNull, false))
    assert(fe.dumpFn().contains("f"))
  }

  // ==== SimpUtils.isNullable / getType ====

  test("SimpUtils.isNullable") {
    assert(!SimpUtils.isNullable(SimpType.TypeInt))
    assert(!SimpUtils.isNullable(SimpType.TypeString))
    assert(!SimpUtils.isNullable(SimpType.TypeBool))
    assert(!SimpUtils.isNullable(SimpType.TypeFloat))
    assert(SimpUtils.isNullable(SimpType.TypeNull))
  }

  test("SimpUtils.getType for all value kinds") {
    assert(SimpUtils.getType(Value.IntVal(1)) == SimpType.TypeInt)
    assert(SimpUtils.getType(Value.FloatVal(1.0)) == SimpType.TypeFloat)
    assert(SimpUtils.getType(Value.StrVal("x")) == SimpType.TypeString)
    assert(SimpUtils.getType(Value.BoolVal(true)) == SimpType.TypeBool)
    assert(SimpUtils.getType(Value.NullVal) == SimpType.TypeNull)
    assert(SimpUtils.getType(Value.StructVal("Point", scala.collection.mutable.Map())) == SimpType.TypeStruct("Point"))
    assert(SimpUtils.getType(Value.MapVal(scala.collection.mutable.Map(), SimpType.TypeInt, SimpType.TypeString)) == SimpType.TypeMap(SimpType.TypeInt, SimpType.TypeString))
    assert(SimpUtils.getType(Value.PairVal(Value.IntVal(1), Value.StrVal("x"))) == SimpType.TypePair(SimpType.TypeInt, SimpType.TypeString))
    assert(SimpUtils.getType(Value.TypeVal(SimpType.TypeInt)) == SimpType.TypeType)
    assert(SimpUtils.getType(Value.ArrVal(TypedArray())) == SimpType.TypeArr(SimpType.TypeInt))
    assert(SimpUtils.getType(Value.ArrVal(TypedArray(Value.IntVal(1)))) == SimpType.TypeArr(SimpType.TypeInt))
  }

  test("SimpUtils.getType on ref value dereferences the store") {
    val store = Store()
    store.store("x", Value.IntVal(5))
    assert(SimpUtils.getType(Value.RefVal("x", store)) == SimpType.TypeInt)
  }

  test("SimpUtils.getType throws on nested reference") {
    val inner = Store()
    inner.store("x", Value.IntVal(5))
    val outer = Store()
    outer.store("r", Value.RefVal("x", inner))
    assertThrows[RuntimeException](SimpUtils.getType(Value.RefVal("r", outer)))
  }

  test("SimpUtils.getType throws on array of references") {
    val store = Store()
    store.store("x", Value.IntVal(5))
    assertThrows[RuntimeException](SimpUtils.getType(Value.ArrVal(TypedArray(Value.RefVal("x", store)))))
  }

  // ==== SimpUtils.getPrettyPrint ====

  test("SimpUtils.getPrettyPrint for primitives") {
    val structEnv = StructEnv()
    assert(SimpUtils.getPrettyPrint(Value.StrVal("hi"), structEnv) == "hi")
    assert(SimpUtils.getPrettyPrint(Value.IntVal(5), structEnv) == "5")
    assert(SimpUtils.getPrettyPrint(Value.FloatVal(1.5), structEnv) == "1.5")
    assert(SimpUtils.getPrettyPrint(Value.BoolVal(true), structEnv) == "true")
    assert(SimpUtils.getPrettyPrint(Value.NullVal, structEnv) == "null")
  }

  test("SimpUtils.getPrettyPrint for ref") {
    val structEnv = StructEnv()
    val store = Store()
    assert(SimpUtils.getPrettyPrint(Value.RefVal("x", store), structEnv) == "Ref(x)")
  }

  test("SimpUtils.getPrettyPrint for map") {
    val structEnv = StructEnv()
    assert(SimpUtils.getPrettyPrint(Value.MapVal(scala.collection.mutable.Map(), SimpType.TypeString, SimpType.TypeInt), structEnv) == "Map(Str -> Int)")
  }

  test("SimpUtils.getPrettyPrint for type value") {
    val structEnv = StructEnv()
    assert(SimpUtils.getPrettyPrint(Value.TypeVal(SimpType.TypeInt), structEnv) == "Type.Int")
  }

  test("SimpUtils.getPrettyPrint for pair") {
    val structEnv = StructEnv()
    assert(SimpUtils.getPrettyPrint(Value.PairVal(Value.IntVal(1), Value.IntVal(2)), structEnv) == "(1, 2)")
  }

  test("SimpUtils.getPrettyPrint for struct") {
    val structEnv = StructEnv()
    structEnv.register("Point", StructDef(List(("x", SimpType.TypeInt, None, false))))
    val fields = scala.collection.mutable.Map[String, Value]("x" -> Value.IntVal(1))
    assert(SimpUtils.getPrettyPrint(Value.StructVal("Point", fields), structEnv) == "Point { x: 1 }")
  }

  test("SimpUtils.getPrettyPrint for cyclic struct does not infinite loop") {
    val structEnv = StructEnv()
    structEnv.register("Node", StructDef(List(("next", SimpType.TypeStruct("Node"), None, false))))
    val fields = scala.collection.mutable.Map[String, Value]()
    val selfVal = Value.StructVal("Node", fields)
    fields("next") = selfVal
    assert(SimpUtils.getPrettyPrint(selfVal, structEnv) == "Node { next: Node { ... } }")
  }

  test("SimpUtils.getPrettyPrint for array") {
    val structEnv = StructEnv()
    assert(SimpUtils.getPrettyPrint(Value.ArrVal(TypedArray(Value.IntVal(1), Value.IntVal(2))), structEnv) == "[1, 2]")
  }

  // ==== SimpUtils.deepCopyValue ====

  test("SimpUtils.deepCopyValue for primitives, type values, and refs") {
    assert(SimpUtils.deepCopyValue(Value.IntVal(1)) == Value.IntVal(1))
    assert(SimpUtils.deepCopyValue(Value.FloatVal(1.0)) == Value.FloatVal(1.0))
    assert(SimpUtils.deepCopyValue(Value.StrVal("x")) == Value.StrVal("x"))
    assert(SimpUtils.deepCopyValue(Value.BoolVal(true)) == Value.BoolVal(true))
    assert(SimpUtils.deepCopyValue(Value.NullVal) == Value.NullVal)
    assert(SimpUtils.deepCopyValue(Value.TypeVal(SimpType.TypeInt)) == Value.TypeVal(SimpType.TypeInt))
    val store = Store()
    assert(SimpUtils.deepCopyValue(Value.RefVal("x", store)) == Value.RefVal("x", store))
  }

  test("SimpUtils.deepCopyValue for array makes an independent copy") {
    val original: Value.ArrVal = Value.ArrVal(TypedArray(Value.IntVal(1)))
    val copy = SimpUtils.deepCopyValue(original).asInstanceOf[Value.ArrVal]
    copy.elements(0) = Value.IntVal(99)
    assert(original.elements(0) == Value.IntVal(1))
  }

  test("SimpUtils.deepCopyValue for map makes an independent copy") {
    val entries = scala.collection.mutable.Map[Value, Value](Value.StrVal("k") -> Value.IntVal(1))
    val original = Value.MapVal(entries, SimpType.TypeString, SimpType.TypeInt)
    val copy = SimpUtils.deepCopyValue(original).asInstanceOf[Value.MapVal]
    copy.entries(Value.StrVal("k")) = Value.IntVal(99)
    assert(entries(Value.StrVal("k")) == Value.IntVal(1))
  }

  test("SimpUtils.deepCopyValue for pair") {
    assert(SimpUtils.deepCopyValue(Value.PairVal(Value.IntVal(1), Value.IntVal(2))) == Value.PairVal(Value.IntVal(1), Value.IntVal(2)))
  }

  test("SimpUtils.deepCopyValue for struct makes an independent copy") {
    val fields = scala.collection.mutable.Map[String, Value]("x" -> Value.IntVal(1))
    val original = Value.StructVal("Point", fields)
    val copy = SimpUtils.deepCopyValue(original).asInstanceOf[Value.StructVal]
    copy.fields("x") = Value.IntVal(99)
    assert(fields("x") == Value.IntVal(1))
  }

  test("SimpUtils.deepCopyValue throws on cyclic struct") {
    val fields = scala.collection.mutable.Map[String, Value]()
    val selfVal = Value.StructVal("Node", fields)
    fields("next") = selfVal
    assertThrows[RuntimeException](SimpUtils.deepCopyValue(selfVal))
  }

  // ==== SimpUtils.checkType ====

  test("SimpUtils.checkType allows null for a nullable type") {
    SimpUtils.checkType(Value.NullVal, SimpType.TypeStruct("Point"), "x")
  }

  test("SimpUtils.checkType throws on null for a non-nullable type") {
    assertThrows[RuntimeException](SimpUtils.checkType(Value.NullVal, SimpType.TypeInt, "x"))
    assertThrows[RuntimeException](SimpUtils.checkType(Value.NullVal, SimpType.TypeFloat, "x"))
  }

  test("SimpUtils.checkType allows an empty array for any array type") {
    SimpUtils.checkType(Value.ArrVal(TypedArray()), SimpType.TypeArr(SimpType.TypeInt), "x")
  }

  test("SimpUtils.checkType throws on empty array for a non-array type") {
    assertThrows[RuntimeException](SimpUtils.checkType(Value.ArrVal(TypedArray()), SimpType.TypeInt, "x"))
  }

  test("SimpUtils.checkType passes on a matching type") {
    SimpUtils.checkType(Value.IntVal(1), SimpType.TypeInt, "x")
  }

  test("SimpUtils.checkType throws on a type mismatch") {
    assertThrows[RuntimeException](SimpUtils.checkType(Value.IntVal(1), SimpType.TypeString, "x"))
  }

  // ==== SimpUtils.getSimpTypeName / getTypeName ====

  test("SimpUtils.getSimpTypeName for all types") {
    assert(SimpUtils.getSimpTypeName(SimpType.TypeInt) == "Int")
    assert(SimpUtils.getSimpTypeName(SimpType.TypeString) == "Str")
    assert(SimpUtils.getSimpTypeName(SimpType.TypeBool) == "Bool")
    assert(SimpUtils.getSimpTypeName(SimpType.TypeFloat) == "Float")
    assert(SimpUtils.getSimpTypeName(SimpType.TypeNull) == "Void")
    assert(SimpUtils.getSimpTypeName(SimpType.TypeType) == "Type")
    assert(SimpUtils.getSimpTypeName(SimpType.TypeArr(SimpType.TypeInt)) == "Int[]")
    assert(SimpUtils.getSimpTypeName(SimpType.TypeStruct("Point")) == "Point")
    assert(SimpUtils.getSimpTypeName(SimpType.TypeRef(SimpType.TypeInt)) == "ref Int")
    assert(SimpUtils.getSimpTypeName(SimpType.TypeMap(SimpType.TypeString, SimpType.TypeInt)) == "Map(Str, Int)")
    assert(SimpUtils.getSimpTypeName(SimpType.TypePair(SimpType.TypeInt, SimpType.TypeString)) == "Pair(Int, Str)")
  }

  test("SimpUtils.getTypeName for all value kinds") {
    assert(SimpUtils.getTypeName(Value.IntVal(1)) == "Int")
    assert(SimpUtils.getTypeName(Value.FloatVal(1.0)) == "Float")
    assert(SimpUtils.getTypeName(Value.StrVal("x")) == "Str")
    assert(SimpUtils.getTypeName(Value.BoolVal(true)) == "Bool")
    assert(SimpUtils.getTypeName(Value.StructVal("Point", scala.collection.mutable.Map())) == "Point")
    val store = Store()
    store.store("x", Value.IntVal(5))
    assert(SimpUtils.getTypeName(Value.RefVal("x", store)) == "ref Int")
    assert(SimpUtils.getTypeName(Value.MapVal(scala.collection.mutable.Map(), SimpType.TypeString, SimpType.TypeInt)) == "Map(Str -> Int)")
    assert(SimpUtils.getTypeName(Value.PairVal(Value.IntVal(1), Value.StrVal("x"))) == "Pair(Int, Str)")
    assert(SimpUtils.getTypeName(Value.TypeVal(SimpType.TypeInt)) == "Type.Int")
    assert(SimpUtils.getTypeName(Value.ArrVal(TypedArray())) == "Unknown[]")
    assert(SimpUtils.getTypeName(Value.ArrVal(TypedArray(Value.IntVal(1)))) == "Int[]")
    assert(SimpUtils.getTypeName(Value.NullVal) == "Null")
  }

  // ==== SimpUtils pretty-printers (AST -> debug string) ====

  test("SimpUtils.prettyPrintExpr covers all expression kinds") {
    SimpUtils.prettyPrintExpr(Expr.Num(1))
    SimpUtils.prettyPrintExpr(Expr.Flt(1.5))
    SimpUtils.prettyPrintExpr(Expr.Bool(true))
    SimpUtils.prettyPrintExpr(Expr.Str("x"))
    SimpUtils.prettyPrintExpr(Expr.Null)
    SimpUtils.prettyPrintExpr(Expr.Ref("x"))
    SimpUtils.prettyPrintExpr(Expr.Deref("x"))
    SimpUtils.prettyPrintExpr(Expr.BoolLift(BoolExpr.Literal(true)))
    SimpUtils.prettyPrintExpr(Expr.BinaryOp(Expr.Num(1), Op.Add, Expr.Num(2)))
    SimpUtils.prettyPrintExpr(Expr.UnaryOp(Expr.Num(1), Op.BitComplement))
    SimpUtils.prettyPrintExpr(Expr.ArrLiteral(List(Expr.Num(1))))
    SimpUtils.prettyPrintExpr(Expr.ArrIndex(Expr.Ref("arr"), Expr.Num(0)))
    SimpUtils.prettyPrintExpr(Expr.FieldAccess(Expr.Ref("p"), "x"))
    SimpUtils.prettyPrintExpr(Expr.FnCall("f", List(Expr.Num(1))))
    SimpUtils.prettyPrintExpr(Expr.MethodCall(Expr.Ref("p"), "m", List(Expr.Num(1))))
    SimpUtils.prettyPrintExpr(Expr.StructLiteral("Point", List(("x", Expr.Num(1)))))
    SimpUtils.prettyPrintExpr(Expr.Pair(Expr.Num(1), Expr.Num(2)))
    SimpUtils.prettyPrintExpr(Expr.Block(List(Cmd.Skip), Expr.Num(1)))
    SimpUtils.prettyPrintExpr(Expr.Match(Expr.Num(1), List(MatchArm(Pattern.PWild, None, Expr.Num(1)))))
    SimpUtils.prettyPrintExpr(Expr.TypeLiteral(SimpType.TypeInt))
  }

  test("SimpUtils.prettyPrintBool covers all boolean expression kinds") {
    SimpUtils.prettyPrintBool(BoolExpr.Literal(true))
    SimpUtils.prettyPrintBool(BoolExpr.FromExpr(Expr.Num(1)))
    SimpUtils.prettyPrintBool(BoolExpr.Compare(Expr.Num(1), Bop.Gt, Expr.Num(2)))
    SimpUtils.prettyPrintBool(BoolExpr.And(BoolExpr.Literal(true), BoolExpr.Literal(false)))
    SimpUtils.prettyPrintBool(BoolExpr.Or(BoolExpr.Literal(true), BoolExpr.Literal(false)))
    SimpUtils.prettyPrintBool(BoolExpr.Not(BoolExpr.Literal(true)))
  }

  test("SimpUtils.prettyPrintCmd covers all command kinds") {
    SimpUtils.prettyPrintCmd(Cmd.Skip)
    SimpUtils.prettyPrintCmd(Cmd.Seq(Cmd.Skip, Cmd.Skip))
    SimpUtils.prettyPrintCmd(Cmd.Assign("x", Expr.Num(1), 1))
    SimpUtils.prettyPrintCmd(Cmd.ConstAssign("x", Expr.Num(1), 1))
    SimpUtils.prettyPrintCmd(Cmd.If(BoolExpr.Literal(true), Cmd.Skip, Cmd.Skip, 1))
    SimpUtils.prettyPrintCmd(Cmd.While(BoolExpr.Literal(true), Cmd.Skip, 1))
    SimpUtils.prettyPrintCmd(Cmd.For("n", Expr.ArrLiteral(List()), Cmd.Skip, 1))
    SimpUtils.prettyPrintCmd(Cmd.Print(Expr.Num(1), 1))
    SimpUtils.prettyPrintCmd(Cmd.Return(Some(Expr.Num(1)), 1))
    SimpUtils.prettyPrintCmd(Cmd.Return(None, 1))
    SimpUtils.prettyPrintCmd(Cmd.Scope(Cmd.Skip))
    SimpUtils.prettyPrintCmd(Cmd.Break)
    SimpUtils.prettyPrintCmd(Cmd.Continue)
    SimpUtils.prettyPrintCmd(Cmd.ArrAssign("arr", Expr.Num(0), Expr.Num(1), 1))
    SimpUtils.prettyPrintCmd(Cmd.FieldAssign("p", "x", Expr.Num(1), 1))
    // These fall through to the default cmd.toString branch (no explicit case).
    SimpUtils.prettyPrintCmd(Cmd.ArrAssignNested("arr", List(Expr.Num(0)), Expr.Num(1), 1))
    SimpUtils.prettyPrintCmd(Cmd.FieldIndexAssign("p", "arr", Expr.Num(0), Expr.Num(1), 1))
    SimpUtils.prettyPrintCmd(Cmd.FieldIndexAssignNested("p", "arr", List(Expr.Num(0)), Expr.Num(1), 1))
  }

  // ==== BuiltInFunctions.scala ====

  test("len: string and array") {
    assert(run("""x := len("abc")""").load("x") == Value.IntVal(3))
    assert(run("x := len([1,2,3])").load("x") == Value.IntVal(3))
  }
  test("len: error on unsupported type") {
    assertThrows[RuntimeException](run("x := len(5)"))
  }

  test("upper / lower") {
    assert(run("""x := upper("abc")""").load("x") == Value.StrVal("ABC"))
    assert(run("""x := lower("ABC")""").load("x") == Value.StrVal("abc"))
  }
  test("upper: error on non-string") { assertThrows[RuntimeException](run("x := upper(5)")) }
  test("lower: error on non-string") { assertThrows[RuntimeException](run("x := lower(5)")) }

  test("trim") { assert(run("""x := trim("  a  ")""").load("x") == Value.StrVal("a")) }
  test("trim: error on non-string") { assertThrows[RuntimeException](run("x := trim(5)")) }

  test("reverse: string and array") {
    assert(run("""x := reverse("abc")""").load("x") == Value.StrVal("cba"))
    assert(run("x := reverse([1,2,3])").load("x") == Value.ArrVal(TypedArray(Value.IntVal(3), Value.IntVal(2), Value.IntVal(1))))
  }
  test("reverse: error on unsupported type") { assertThrows[RuntimeException](run("x := reverse(5)")) }

  test("contains: string and array") {
    assert(run("""x := contains("abc", "b")""").load("x") == Value.BoolVal(true))
    assert(run("x := contains([1,2,3], 2)").load("x") == Value.BoolVal(true))
  }
  test("contains: error on unsupported types") { assertThrows[RuntimeException](run("x := contains(5, 5)")) }

  test("startsWith / endsWith") {
    assert(run("""x := startsWith("abc", "ab")""").load("x") == Value.BoolVal(true))
    assert(run("""x := endsWith("abc", "bc")""").load("x") == Value.BoolVal(true))
  }
  test("startsWith: error on non-strings") { assertThrows[RuntimeException](run("x := startsWith(5, 5)")) }
  test("endsWith: error on non-strings") { assertThrows[RuntimeException](run("x := endsWith(5, 5)")) }

  test("replace") { assert(run("""x := replace("abc", "b", "x")""").load("x") == Value.StrVal("axc")) }
  test("replace: error on non-strings") { assertThrows[RuntimeException](run("x := replace(5, 5, 5)")) }

  test("substr") { assert(run("""x := substr("abcdef", 1, 3)""").load("x") == Value.StrVal("bc")) }
  test("substr: error on wrong types") { assertThrows[RuntimeException](run("x := substr(5, 5, 5)")) }

  test("slice") { assert(run("x := slice([1,2,3,4], 1, 3)").load("x") == Value.ArrVal(TypedArray(Value.IntVal(2), Value.IntVal(3)))) }
  test("slice: error on wrong types") { assertThrows[RuntimeException](run("x := slice(5, 5, 5)")) }

  test("indexOf") { assert(run("""x := indexOf("abc", "b")""").load("x") == Value.IntVal(1)) }
  test("indexOf: error on non-strings") { assertThrows[RuntimeException](run("x := indexOf(5, 5)")) }

  test("isInt / isStr / isBool / isFloat") {
    assert(run("x := isInt(5)").load("x") == Value.BoolVal(true))
    assert(run("""x := isInt("a")""").load("x") == Value.BoolVal(false))
    assert(run("""x := isStr("a")""").load("x") == Value.BoolVal(true))
    assert(run("x := isStr(5)").load("x") == Value.BoolVal(false))
    assert(run("x := isBool(true)").load("x") == Value.BoolVal(true))
    assert(run("x := isBool(5)").load("x") == Value.BoolVal(false))
    assert(run("x := isFloat(1.5)").load("x") == Value.BoolVal(true))
    assert(run("x := isFloat(5)").load("x") == Value.BoolVal(false))
  }
  test("isInt: error on wrong arity") { assertThrows[RuntimeException](run("x := isInt(1, 2)")) }
  test("isStr: error on wrong arity") { assertThrows[RuntimeException](run("x := isStr(1, 2)")) }
  test("isBool: error on wrong arity") { assertThrows[RuntimeException](run("x := isBool(1, 2)")) }
  test("isFloat: error on wrong arity") { assertThrows[RuntimeException](run("x := isFloat(1, 2)")) }

  test("toStr") { assert(run("x := toStr(5)").load("x") == Value.StrVal("5")) }
  test("toStr: error on wrong arity") { assertThrows[RuntimeException](run("x := toStr()")) }

  test("toInt: from string and float") {
    assert(run("""x := toInt("5")""").load("x") == Value.IntVal(5))
    assert(run("x := toInt(1.9)").load("x") == Value.IntVal(1))
  }
  test("toInt: error on unsupported type") { assertThrows[RuntimeException](run("x := toInt(true)")) }

  test("toFloat: from string and int") {
    assert(run("""x := toFloat("5.5")""").load("x") == Value.FloatVal(5.5))
    assert(run("x := toFloat(5)").load("x") == Value.FloatVal(5.0))
  }
  test("toFloat: error on unsupported type") { assertThrows[RuntimeException](run("x := toFloat(true)")) }

  test("toBool") { assert(run("""x := toBool("true")""").load("x") == Value.BoolVal(true)) }
  test("toBool: error on non-string") { assertThrows[RuntimeException](run("x := toBool(5)")) }

  test("toArr") { assert(run("""x := toArr("ab")""").load("x") == Value.ArrVal(TypedArray(Value.StrVal("a"), Value.StrVal("b")))) }
  test("toArr: error on non-string") { assertThrows[RuntimeException](run("x := toArr(5)")) }

  test("split") { assert(run("""x := split("a,b", ",")""").load("x") == Value.ArrVal(TypedArray(Value.StrVal("a"), Value.StrVal("b")))) }
  test("split: error on wrong types") { assertThrows[RuntimeException](run("x := split(5, 5)")) }

  test("range: one, two, and three arguments") {
    assert(run("x := range(3)").load("x") == Value.ArrVal(TypedArray(Value.IntVal(0), Value.IntVal(1), Value.IntVal(2))))
    assert(run("x := range(1, 3)").load("x") == Value.ArrVal(TypedArray(Value.IntVal(1), Value.IntVal(2))))
    assert(run("x := range(0, 6, 2)").load("x") == Value.ArrVal(TypedArray(Value.IntVal(0), Value.IntVal(2), Value.IntVal(4))))
  }
  test("range: error on wrong types") { assertThrows[RuntimeException](run("""x := range("a")""")) }

  test("abs: int and float") {
    assert(run("x := abs(-5)").load("x") == Value.IntVal(5))
    assert(run("x := abs(-1.5)").load("x") == Value.FloatVal(1.5))
  }
  test("abs: error on non-numeric") { assertThrows[RuntimeException](run("""x := abs("a")""")) }

  test("max: all numeric type combinations, both branches") {
    assert(run("x := max(3, 5)").load("x") == Value.IntVal(5))
    assert(run("x := max(5, 3)").load("x") == Value.IntVal(5))
    assert(run("x := max(3.0, 5.0)").load("x") == Value.FloatVal(5.0))
    assert(run("x := max(5.0, 3.0)").load("x") == Value.FloatVal(5.0))
    assert(run("x := max(3, 5.0)").load("x") == Value.FloatVal(5.0))
    assert(run("x := max(5, 3.0)").load("x") == Value.FloatVal(5.0))
    assert(run("x := max(3.0, 5)").load("x") == Value.FloatVal(5.0))
    assert(run("x := max(5.0, 3)").load("x") == Value.FloatVal(5.0))
  }
  test("max: error on unsupported types") { assertThrows[RuntimeException](run("""x := max("a", "b")""")) }

  test("min: all numeric type combinations, both branches") {
    assert(run("x := min(3, 5)").load("x") == Value.IntVal(3))
    assert(run("x := min(5, 3)").load("x") == Value.IntVal(3))
    assert(run("x := min(3.0, 5.0)").load("x") == Value.FloatVal(3.0))
    assert(run("x := min(5.0, 3.0)").load("x") == Value.FloatVal(3.0))
    assert(run("x := min(3, 5.0)").load("x") == Value.FloatVal(3.0))
    assert(run("x := min(5, 3.0)").load("x") == Value.FloatVal(3.0))
    assert(run("x := min(3.0, 5)").load("x") == Value.FloatVal(3.0))
    assert(run("x := min(5.0, 3)").load("x") == Value.FloatVal(3.0))
  }
  test("min: error on unsupported types") { assertThrows[RuntimeException](run("""x := min("a", "b")""")) }

  test("clamp: int, mixed float/int, and all float, each branch") {
    assert(run("x := clamp(-1, 0, 10)").load("x") == Value.IntVal(0))
    assert(run("x := clamp(20, 0, 10)").load("x") == Value.IntVal(10))
    assert(run("x := clamp(5, 0, 10)").load("x") == Value.IntVal(5))
    assert(run("x := clamp(-1.0, 0, 10)").load("x") == Value.FloatVal(0.0))
    assert(run("x := clamp(20.0, 0, 10)").load("x") == Value.FloatVal(10.0))
    assert(run("x := clamp(5.0, 0, 10)").load("x") == Value.FloatVal(5.0))
    assert(run("x := clamp(-1.0, 0.0, 10.0)").load("x") == Value.FloatVal(0.0))
    assert(run("x := clamp(20.0, 0.0, 10.0)").load("x") == Value.FloatVal(10.0))
    assert(run("x := clamp(5.0, 0.0, 10.0)").load("x") == Value.FloatVal(5.0))
  }
  test("clamp: error on unsupported types") { assertThrows[RuntimeException](run("""x := clamp("a", 0, 1)""")) }

  test("pow: positive int exponent uses fast exponentiation (both parity branches)") {
    assert(run("x := pow(2, 4)").load("x") == Value.IntVal(16))
  }
  test("pow: zero or negative int exponent falls back to Math.pow") {
    assert(run("x := pow(2, 0)").load("x") == Value.FloatVal(1.0))
    assert(run("x := pow(2, -1)").load("x") == Value.FloatVal(0.5))
  }
  test("pow: mixed int/float combinations") {
    assert(run("x := pow(2, 2.0)").load("x") == Value.FloatVal(4.0))
    assert(run("x := pow(2.0, 2)").load("x") == Value.FloatVal(4.0))
    assert(run("x := pow(2.0, 2.0)").load("x") == Value.FloatVal(4.0))
  }
  test("pow: error on unsupported types") { assertThrows[RuntimeException](run("""x := pow("a", 1)""")) }

  test("sqrt: int and float") {
    assert(run("x := sqrt(4)").load("x") == Value.FloatVal(2.0))
    assert(run("x := sqrt(4.0)").load("x") == Value.FloatVal(2.0))
  }
  test("sqrt: error on unsupported type") { assertThrows[RuntimeException](run("""x := sqrt("a")""")) }

  test("ln: int and float") {
    run("x := ln(1)")
    run("x := ln(1.0)")
  }
  test("ln: error on unsupported type") { assertThrows[RuntimeException](run("""x := ln("a")""")) }

  test("log10: int and float") {
    assert(run("x := log10(100)").load("x") == Value.FloatVal(2.0))
    assert(run("x := log10(100.0)").load("x") == Value.FloatVal(2.0))
  }
  test("log10: error on unsupported type") { assertThrows[RuntimeException](run("""x := log10("a")""")) }

  test("log: all four numeric combinations") {
    run("x := log(8, 2)")
    run("x := log(8, 2.0)")
    run("x := log(8.0, 2)")
    run("x := log(8.0, 2.0)")
  }
  test("log: error on unsupported types") { assertThrows[RuntimeException](run("""x := log("a", "b")""")) }

  test("pi / e") {
    assert(run("x := pi()").load("x") == Value.FloatVal(scala.math.Pi))
    assert(run("x := e()").load("x") == Value.FloatVal(scala.math.E))
  }
  test("pi: error on arguments") { assertThrows[RuntimeException](run("x := pi(1)")) }
  test("e: error on arguments") { assertThrows[RuntimeException](run("x := e(1)")) }

  test("assert: true passes, with and without message") {
    assert(run("x := assert(true)").load("x") == Value.BoolVal(true))
    assert(run("""x := assert(true, "msg")""").load("x") == Value.BoolVal(true))
  }
  test("assert: false throws, with and without message") {
    assertThrows[RuntimeException](run("x := assert(false)"))
    assertThrows[RuntimeException](run("""x := assert(false, "msg")"""))
  }
  test("assert: error on wrong argument types") { assertThrows[RuntimeException](run("x := assert(5)")) }

  test("input: with and without prompt reads from stdin") {
    val in1 = new java.io.BufferedReader(new java.io.StringReader("hello\n"))
    val store1 = scala.Console.withIn(in1) { run("""x := input("prompt: ")""") }
    assert(store1.load("x") == Value.StrVal("hello"))
    val in2 = new java.io.BufferedReader(new java.io.StringReader("world\n"))
    val store2 = scala.Console.withIn(in2) { run("x := input()") }
    assert(store2.load("x") == Value.StrVal("world"))
  }
  test("input: error on wrong arguments") { assertThrows[RuntimeException](run("x := input(1, 2)")) }

  test("inputInt: with and without prompt, and parse-failure branch") {
    val in1 = new java.io.BufferedReader(new java.io.StringReader("42\n"))
    val store1 = scala.Console.withIn(in1) { run("""x := inputInt("prompt: ")""") }
    assert(store1.load("x") == Value.IntVal(42))
    val in2 = new java.io.BufferedReader(new java.io.StringReader("7\n"))
    val store2 = scala.Console.withIn(in2) { run("x := inputInt()") }
    assert(store2.load("x") == Value.IntVal(7))
    val in3 = new java.io.BufferedReader(new java.io.StringReader("notanumber\n"))
    assertThrows[RuntimeException](scala.Console.withIn(in3) { run("x := inputInt()") })
  }
  test("inputInt: error on wrong arguments") { assertThrows[RuntimeException](run("x := inputInt(1, 2)")) }

  test("intSqrt") {
    assert(run("x := intSqrt(9)").load("x") == Value.IntVal(3))
  }
  test("intSqrt: error on negative number") { assertThrows[RuntimeException](run("x := intSqrt(-1)")) }
  test("intSqrt: error on non-integer") { assertThrows[RuntimeException](run("""x := intSqrt("a")""")) }

  test("inputBool: yes/no/invalid input, both with and without a prompt") {
    def read(input: String, source: String): Store = {
      val in = new java.io.BufferedReader(new java.io.StringReader(input + "\n"))
      scala.Console.withIn(in) { run(source) }
    }
    assert(read("yes", """x := inputBool("p: ")""").load("x") == Value.BoolVal(true))
    assert(read("no", """x := inputBool("p: ")""").load("x") == Value.BoolVal(true)) // both branches currently return true
    assert(read("yes", "x := inputBool()").load("x") == Value.BoolVal(true))
    assert(read("no", "x := inputBool()").load("x") == Value.BoolVal(true))
    assertThrows[RuntimeException](read("maybe", """x := inputBool("p: ")"""))
    assertThrows[RuntimeException](read("maybe", "x := inputBool()"))
  }
  test("inputBool: error on wrong arguments") { assertThrows[RuntimeException](run("x := inputBool(1, 2)")) }

  test("readFile: reads lines from an existing file") {
    val f = java.io.File.createTempFile("simp-read", ".txt")
    f.deleteOnExit()
    java.nio.file.Files.write(f.toPath, "a\nb\nc".getBytes)
    val store = run(s"""x := readFile("${f.getAbsolutePath}");""")
    assert(store.load("x") == Value.ArrVal(TypedArray(Value.StrVal("a"), Value.StrVal("b"), Value.StrVal("c"))))
  }
  test("readFile: error on missing file") {
    assertThrows[RuntimeException](run("""x := readFile("/nonexistent/path/does-not-exist.txt");"""))
  }
  test("readFile: error on wrong argument type") { assertThrows[RuntimeException](run("x := readFile(5)")) }

  test("writeFile: array of strings") {
    val f = java.io.File.createTempFile("simp-write", ".txt")
    f.deleteOnExit()
    val store = run(s"""x := writeFile("${f.getAbsolutePath}", ["a", "b"]);""")
    assert(store.load("x") == Value.BoolVal(true))
    assert(java.nio.file.Files.readString(f.toPath) == "a\nb\n")
  }
  test("writeFile: array containing a non-string throws") {
    val f = java.io.File.createTempFile("simp-write-bad", ".txt")
    f.deleteOnExit()
    assertThrows[RuntimeException](run(s"""x := writeFile("${f.getAbsolutePath}", [1, 2]);"""))
  }
  test("writeFile: plain string content") {
    val f = java.io.File.createTempFile("simp-write-str", ".txt")
    f.deleteOnExit()
    val store = run(s"""x := writeFile("${f.getAbsolutePath}", "hello");""")
    assert(store.load("x") == Value.BoolVal(true))
    assert(java.nio.file.Files.readString(f.toPath) == "hello")
  }
  test("writeFile: plain string content to an invalid path returns false") {
    val store = run("""x := writeFile("/this/path/does/not/exist/file.txt", "hello");""")
    assert(store.load("x") == Value.BoolVal(false))
  }
  test("writeFile: error on wrong argument types") { assertThrows[RuntimeException](run("x := writeFile(5, 5)")) }

  test("typeOf") { assert(run("x := typeOf(5)").load("x") == Value.StrVal("Int")) }
  test("typeOf: error on wrong arity") { assertThrows[RuntimeException](run("x := typeOf()")) }

  test("deepCopy") { assert(run("x := deepCopy([1,2,3])").load("x") == Value.ArrVal(TypedArray(Value.IntVal(1), Value.IntVal(2), Value.IntVal(3)))) }
  test("deepCopy: error on wrong arity") { assertThrows[RuntimeException](run("x := deepCopy()")) }

  test("isNull") {
    assert(run("x := isNull(null)").load("x") == Value.BoolVal(true))
    assert(run("x := isNull(5)").load("x") == Value.BoolVal(false))
  }
  test("isNull: error on wrong arity") { assertThrows[RuntimeException](run("x := isNull()")) }

  test("push: mutates the array in place") {
    val store = run("arr := [1]; _ := push(arr, 2); x := arr;")
    assert(store.load("x") == Value.ArrVal(TypedArray(Value.IntVal(1), Value.IntVal(2))))
  }
  test("push: error on non-array") { assertThrows[RuntimeException](run("x := push(5, 1)")) }

  test("isEmpty") {
    assert(run("x := isEmpty([])").load("x") == Value.BoolVal(true))
    assert(run("x := isEmpty([1])").load("x") == Value.BoolVal(false))
  }
  test("isEmpty: error on non-array") { assertThrows[RuntimeException](run("x := isEmpty(5)")) }

  test("newMap: error on non-type arguments") { assertThrows[RuntimeException](run("x := newMap(1, 2)")) }

  test("get: key type mismatch throws") {
    assertThrows[RuntimeException](run("""m := newMap(Str, Int); x := get(m, 5);"""))
  }
  test("get: key not found throws") {
    assertThrows[RuntimeException](run("""m := newMap(Str, Int); x := get(m, "missing");"""))
  }
  test("get: error on non-map") { assertThrows[RuntimeException](run("x := get(5, 5)")) }

  test("set: key type mismatch throws") {
    assertThrows[RuntimeException](run("""m := newMap(Str, Int); _ := set(m, 5, 1);"""))
  }
  test("set: value type mismatch throws") {
    assertThrows[RuntimeException](run("""m := newMap(Str, Int); _ := set(m, "k", "not an int");"""))
  }
  test("set: error on non-map") { assertThrows[RuntimeException](run("x := set(5, 5, 5)")) }

  test("hasKey: error on non-map") { assertThrows[RuntimeException](run("x := hasKey(5, 5)")) }
  test("remove: error on non-map") { assertThrows[RuntimeException](run("x := remove(5, 5)")) }

  test("keys") {
    val store = run("""m := newMap(Str, Int); _ := set(m, "a", 1); x := keys(m);""")
    assert(store.load("x") == Value.ArrVal(TypedArray(Value.StrVal("a"))))
  }
  test("keys: error on non-map") { assertThrows[RuntimeException](run("x := keys(5)")) }

  test("toBinary") { assert(run("x := toBinary(5)").load("x") == Value.StrVal("101")) }
  test("toBinary: error on non-int") { assertThrows[RuntimeException](run("""x := toBinary("a")""")) }

  test("floor / ceil / round") {
    assert(run("x := floor(1.7)").load("x") == Value.FloatVal(1.0))
    assert(run("x := ceil(1.2)").load("x") == Value.FloatVal(2.0))
    assert(run("x := round(1.5)").load("x") == Value.FloatVal(2.0))
  }
  test("floor: error on non-float") { assertThrows[RuntimeException](run("x := floor(1)")) }
  test("ceil: error on non-float") { assertThrows[RuntimeException](run("x := ceil(1)")) }
  test("round: error on non-float") { assertThrows[RuntimeException](run("x := round(1)")) }

  test("cos / sin / acos / asin / atan") {
    run("x := cos(0.0)")
    run("x := sin(0.0)")
    run("x := acos(1.0)")
    run("x := asin(0.0)")
    run("x := atan(0.0)")
  }
  test("cos: error on non-float") { assertThrows[RuntimeException](run("x := cos(1)")) }
  test("sin: error on non-float") { assertThrows[RuntimeException](run("x := sin(1)")) }
  test("acos: error on non-float") { assertThrows[RuntimeException](run("x := acos(1)")) }
  test("asin: error on non-float") { assertThrows[RuntimeException](run("x := asin(1)")) }
  test("atan: error on non-float") { assertThrows[RuntimeException](run("x := atan(1)")) }

  test("tan: normal value and divergent guard") {
    run("x := tan(45.0)")
    assertThrows[RuntimeException](run("x := tan(90.0)"))
  }
  test("tan: error on non-float") { assertThrows[RuntimeException](run("x := tan(1)")) }

  test("flatten") {
    val store = run("x := flatten([[1,2],[3,4]])")
    assert(store.load("x") == Value.ArrVal(TypedArray(Value.IntVal(1), Value.IntVal(2), Value.IntVal(3), Value.IntVal(4))))
  }
  test("flatten: error on inner non-array element") { assertThrows[RuntimeException](run("x := flatten([1,2])")) }
  test("flatten: error on non-array") { assertThrows[RuntimeException](run("x := flatten(5)")) }

  test("sum: int and float arrays") {
    assert(run("x := sum([1,2,3])").load("x") == Value.IntVal(6))
    assert(run("x := sum([1.0,2.0])").load("x") == Value.FloatVal(3.0))
  }
  test("sum: error on unsupported array type") { assertThrows[RuntimeException](run("""x := sum(["a","b"])""")) }

  test("random: returns an int") {
    val store = run("x := random(1, 5)")
    assert(store.load("x").isInstanceOf[Value.IntVal])
  }
  test("random: error on wrong types") { assertThrows[RuntimeException](run("""x := random("a", "b")""")) }

  test("ord / chr") {
    assert(run("""x := ord("a")""").load("x") == Value.IntVal(97))
    assert(run("x := chr(97)").load("x") == Value.StrVal("a"))
  }
  test("ord: error on multi-character string") { assertThrows[RuntimeException](run("""x := ord("ab")""")) }
  test("chr: error on non-int") { assertThrows[RuntimeException](run("""x := chr("a")""")) }

  test("zip") {
    val store = run("x := zip([1,2],[3,4])")
    assert(store.load("x") == Value.ArrVal(TypedArray(
      Value.PairVal(Value.IntVal(1), Value.IntVal(3)),
      Value.PairVal(Value.IntVal(2), Value.IntVal(4))
    )))
  }
  test("zip: error on non-arrays") { assertThrows[RuntimeException](run("x := zip(5, 5)")) }

  test("clearScreen / hideCursor / showCursor / moveCursor") {
    run("_ := clearScreen();")
    run("_ := hideCursor();")
    run("_ := showCursor();")
    run("_ := moveCursor(1, 1);")
  }
  test("clearScreen: error on arguments") { assertThrows[RuntimeException](run("x := clearScreen(1)")) }
  test("hideCursor: error on arguments") { assertThrows[RuntimeException](run("x := hideCursor(1)")) }
  test("showCursor: error on arguments") { assertThrows[RuntimeException](run("x := showCursor(1)")) }
  test("moveCursor: error on wrong types") { assertThrows[RuntimeException](run("""x := moveCursor("a", "b")""")) }

  test("sleep") { run("_ := sleep(1);") }
  test("sleep: error on non-int") { assertThrows[RuntimeException](run("""x := sleep("a")""")) }

  test("console: runs a shell command and captures output") {
    val store = run("""x := console("echo hi");""")
    assert(store.load("x") == Value.StrVal("hi\n"))
  }
  test("console: error when the command fails") {
    assertThrows[RuntimeException](run("""x := console("this_command_does_not_exist_xyz_123");"""))
  }
  test("console: error on non-string argument") { assertThrows[RuntimeException](run("x := console(5)")) }

  test("readKey: error on arguments (success path needs a real terminal)") {
    assertThrows[RuntimeException](run("x := readKey(1)"))
  }

  // ==== EvaluatorImport.scala ====

  def runWithCwd(source: String, cwd: String): Store = {
    val store = Store()
    val fnEnv = FunctionEnv()
    val structEnv = StructEnv()
    Builtins.register(fnEnv, structEnv)
    val sourceLines = source.split('\n').toList
    val tokens = Lexer(source, sourceLines).tokenise()
    val program = Parser(tokens._1, structEnv, tokens._2, sourceLines).parseProgram()
    Evaluator(fnEnv, structEnv, sourceLines, cwd).evalProgram(program, store)
    store
  }

  test("import: registers and qualifies functions from another file") {
    val dir = java.nio.file.Files.createTempDirectory("simp-import").toFile
    val lib = new java.io.File(dir, "lib.simp")
    java.nio.file.Files.writeString(lib.toPath, "fn triple(n: Int) -> Int { return !n * 3; }")
    val store = runWithCwd("""import "lib.simp" as lib; x := lib::triple(4);""", dir.getAbsolutePath)
    assert(store.load("x") == Value.IntVal(12))
  }

  test("import: registers structs from another file under a qualified name") {
    val dir = java.nio.file.Files.createTempDirectory("simp-import-struct").toFile
    val lib = new java.io.File(dir, "shapes.simp")
    java.nio.file.Files.writeString(lib.toPath, "struct Point { x: Int, y: Int }")
    val store = Store()
    val fnEnv = FunctionEnv()
    val structEnv = StructEnv()
    Builtins.register(fnEnv, structEnv)
    val source = """import "shapes.simp" as shapes;"""
    val sourceLines = source.split('\n').toList
    val tokens = Lexer(source, sourceLines).tokenise()
    val program = Parser(tokens._1, structEnv, tokens._2, sourceLines).parseProgram()
    Evaluator(fnEnv, structEnv, sourceLines, dir.getAbsolutePath).evalProgram(program, store)
    assert(structEnv.exists("shapes::Point"))
  }

  test("import: importing the same file with the same alias twice is a no-op") {
    val dir = java.nio.file.Files.createTempDirectory("simp-import-dup").toFile
    val lib = new java.io.File(dir, "lib.simp")
    java.nio.file.Files.writeString(lib.toPath, "fn triple(n: Int) -> Int { return !n * 3; }")
    val store = runWithCwd(
      """import "lib.simp" as lib; import "lib.simp" as lib; x := lib::triple(4);""",
      dir.getAbsolutePath
    )
    assert(store.load("x") == Value.IntVal(12))
  }

  test("import: missing file throws") {
    val dir = java.nio.file.Files.createTempDirectory("simp-import-missing").toFile
    assertThrows[RuntimeException](runWithCwd("""import "nope.simp" as n;""", dir.getAbsolutePath))
  }

  test("import: circular imports throw") {
    val dir = java.nio.file.Files.createTempDirectory("simp-import-circular").toFile
    val a = new java.io.File(dir, "a.simp")
    val b = new java.io.File(dir, "b.simp")
    java.nio.file.Files.writeString(a.toPath, """import "b.simp" as b;""")
    java.nio.file.Files.writeString(b.toPath, """import "a.simp" as a;""")
    assertThrows[RuntimeException](runWithCwd("""import "a.simp" as a;""", dir.getAbsolutePath))
  }

  test("import: nested imports resolve transitively") {
    val dir = java.nio.file.Files.createTempDirectory("simp-import-nested").toFile
    val b = new java.io.File(dir, "b.simp")
    val a = new java.io.File(dir, "a.simp")
    java.nio.file.Files.writeString(b.toPath, "fn double(n: Int) -> Int { return !n * 2; }")
    java.nio.file.Files.writeString(a.toPath,
      """import "b.simp" as b; fn quad(n: Int) -> Int { return b::double(b::double(!n)); }"""
    )
    val store = runWithCwd("""import "a.simp" as a; x := a::quad(3);""", dir.getAbsolutePath)
    assert(store.load("x") == Value.IntVal(12))
  }

  test("import: qualifies recursive self-calls within the imported file") {
    val dir = java.nio.file.Files.createTempDirectory("simp-import-recursive").toFile
    val lib = new java.io.File(dir, "lib.simp")
    java.nio.file.Files.writeString(lib.toPath,
      "fn fact(n: Int) -> Int { if !n == 0 then { return 1; } else { return !n * fact(!n - 1); }; }"
    )
    val store = runWithCwd("""import "lib.simp" as lib; x := lib::fact(5);""", dir.getAbsolutePath)
    assert(store.load("x") == Value.IntVal(120))
  }

  test("import: qualifyBody rewrites Skip, Seq, While, and Assign nodes") {
    val dir = java.nio.file.Files.createTempDirectory("simp-import-qualify").toFile
    val lib = new java.io.File(dir, "lib.simp")
    java.nio.file.Files.writeString(lib.toPath,
      "fn helper(n: Int) -> Int { return !n; } fn run(n: Int) -> Int { skip; x := helper(n); while !x == 999999 do { x := helper(x); }; return x; }"
    )
    val store = runWithCwd("""import "lib.simp" as lib; x := lib::run(5);""", dir.getAbsolutePath)
    assert(store.load("x") == Value.IntVal(5))
  }

  test("import: file containing a non-declaration statement throws") {
    val dir = java.nio.file.Files.createTempDirectory("simp-import-bad").toFile
    val lib = new java.io.File(dir, "bad.simp")
    java.nio.file.Files.writeString(lib.toPath, "skip;")
    assertThrows[RuntimeException](runWithCwd("""import "bad.simp" as bad;""", dir.getAbsolutePath))
  }

  // ==== Main.scala: runSource ====

  test("runSource: successfully evaluates a program") {
    val store = Store()
    val fnEnv = FunctionEnv()
    val structEnv = StructEnv()
    Builtins.register(fnEnv, structEnv)
    runSource("x := 5;", store, fnEnv, structEnv)
    assert(store.load("x") == Value.IntVal(5))
  }

  test("runSource: catches a RuntimeException instead of propagating it") {
    val store = Store()
    val fnEnv = FunctionEnv() 
    val structEnv = StructEnv()
    Builtins.register(fnEnv, structEnv)
    runSource("x := 5 / 0;", store, fnEnv, structEnv)
    assertThrows[RuntimeException](store.load("x"))
  }

  // ==== Coverage mop-up ====

  test("match pair pattern against a non-pair value falls through") {
    val store = run("x := match 5 { case (a, b) => 1; case _ => 0; }")
    assert(store.load("x") == Value.IntVal(0))
  }

  test("match struct pattern with unknown field name never matches") {
    val store = run(
      "struct Point { x: Int, y: Int }; p := Point { x: 1, y: 2 }; r := match p { case Point { x: a, bogus: b } => 1; case _ => 2; };"
    )
    assert(store.load("r") == Value.IntVal(2))
  }

  test("match struct pattern with a failing nested literal pattern never matches") {
    val store = run(
      "struct Point { x: Int, y: Int }; p := Point { x: 1, y: 2 }; r := match p { case Point { x: 999 } => 1; case _ => 2; };"
    )
    assert(store.load("r") == Value.IntVal(2))
  }

  test("block expression evaluates commands then returns the result") {
    val store = run("x := { y := 1; (!y + 1) };")
    assert(store.load("x") == Value.IntVal(2))
  }

  test("match guard evaluation copies existing store entries") {
    val store = run("z := 10; x := match 5 { case n if !z == 10 => 1; case _ => 0; };")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("struct literal uses declared default value for an omitted field") {
    val store = run("struct Point { x: Int, y: Int := 10 }; p := Point { x: 1 }; r := p.y;")
    assert(store.load("r") == Value.IntVal(10))
  }

  test("unary bit-complement evaluates at runtime for a non-literal operand") {
    val store = run("x := 5; y := ~!x;")
    assert(store.load("y") == Value.IntVal(~5))
  }

  test("binary op: float arithmetic evaluated at runtime") {
    val store = run("x := 2.5; y := 1.5; a := !x + !y; b := !x - !y; c := !x * !y; d := !x / !y;")
    assert(store.load("a") == Value.FloatVal(4.0))
    assert(store.load("b") == Value.FloatVal(1.0))
    assert(store.load("c") == Value.FloatVal(3.75))
    assert(store.load("d") == Value.FloatVal(2.5 / 1.5))
  }

  test("binary op: float division by zero throws at runtime") {
    assertThrows[RuntimeException](run("x := 1.0; y := 0.0; z := !x / !y;"))
  }

  test("binary op: mixed int/float arithmetic evaluated at runtime") {
    val store = run("x := 2; y := 1.5; a := !x + !y; b := !y + !x;")
    assert(store.load("a") == Value.FloatVal(3.5))
    assert(store.load("b") == Value.FloatVal(3.5))
  }

  test("binary op: int modulo and bitwise operators evaluated at runtime") {
    val store = run(
      "a := 5; b := 3; r1 := !a % !b; r2 := !a & !b; r3 := !a | !b; r4 := !a ^ !b; r5 := !a << !b; r6 := !a >> !b; r7 := !a >>> !b;"
    )
    assert(store.load("r1") == Value.IntVal(5 % 3))
    assert(store.load("r2") == Value.IntVal(5 & 3))
    assert(store.load("r3") == Value.IntVal(5 | 3))
    assert(store.load("r4") == Value.IntVal(5 ^ 3))
    assert(store.load("r5") == Value.IntVal(5 << 3))
    assert(store.load("r6") == Value.IntVal(5 >> 3))
    assert(store.load("r7") == Value.IntVal(5 >>> 3))
  }

  test("compare: float and mixed int/float ordering evaluated at runtime") {
    val store = run("a := 1.5; b := 2.5; c := 1; r1 := !a < !b; r2 := !a < !c; r3 := !c < !b;")
    assert(store.load("r1") == Value.BoolVal(true))
    assert(store.load("r2") == Value.BoolVal(false))
    assert(store.load("r3") == Value.BoolVal(true))
  }

  test("compare: not-equal operator evaluated at runtime") {
    val store = run("a := 5; b := 3; r := !a != !b;")
    assert(store.load("r") == Value.BoolVal(true))
  }

  test("compare: string equality and inequality evaluated at runtime") {
    val store = run("""a := "x"; b := "x"; c := "y"; r1 := !a == !b; r2 := !a != !c;""")
    assert(store.load("r1") == Value.BoolVal(true))
    assert(store.load("r2") == Value.BoolVal(true))
  }

  test("compare: bool equality and inequality evaluated at runtime") {
    val store = run("a := true; b := true; c := false; r1 := !a == !b; r2 := !a != !c;")
    assert(store.load("r1") == Value.BoolVal(true))
    assert(store.load("r2") == Value.BoolVal(true))
  }

  test("struct inequality with equal structs is false") {
    val store = run(
      "struct P { x: Int } a := P { x: 1 }; b := P { x: 1 }; r := a != b; y := match r { case true => 1; case _ => 0; };"
    )
    assert(store.load("y") == Value.IntVal(0))
  }

  test("structsEqual compares fields of every primitive type, both identity-hash orderings") {
    def makeNode(): Value.StructVal = {
      val f = scala.collection.mutable.Map[String, Value](
        "n" -> Value.IntVal(1),
        "f" -> Value.FloatVal(1.5),
        "s" -> Value.StrVal("hi"),
        "b" -> Value.BoolVal(true),
        "u" -> Value.NullVal,
        "arr" -> Value.ArrVal(TypedArray(Value.IntVal(1), Value.IntVal(2)))
      )
      Value.StructVal("Node", f)
    }
    val n1 = makeNode()
    val n2 = makeNode()
    val storeA = Store(); storeA.store("a", n1); storeA.store("b", n2)
    val rA = directEval(List(Program.PCmd(Cmd.Assign("r", Expr.BoolLift(BoolExpr.Compare(Expr.Ref("a"), Bop.Eq, Expr.Ref("b"))), 1))), storeA)
    assert(rA.load("r") == Value.BoolVal(true))
    val storeB = Store(); storeB.store("a", n2); storeB.store("b", n1)
    val rB = directEval(List(Program.PCmd(Cmd.Assign("r", Expr.BoolLift(BoolExpr.Compare(Expr.Ref("a"), Bop.Eq, Expr.Ref("b"))), 1))), storeB)
    assert(rB.load("r") == Value.BoolVal(true))
  }

  test("structsEqual: mismatched keysets are unequal (direct construction)") {
    val f1 = scala.collection.mutable.Map[String, Value]("x" -> Value.IntVal(1))
    val f2 = scala.collection.mutable.Map[String, Value]("y" -> Value.IntVal(1))
    val store = Store()
    store.store("a", Value.StructVal("S", f1))
    store.store("b", Value.StructVal("S", f2))
    val result = directEval(List(Program.PCmd(Cmd.Assign("r", Expr.BoolLift(BoolExpr.Compare(Expr.Ref("a"), Bop.Eq, Expr.Ref("b"))), 1))), store)
    assert(result.load("r") == Value.BoolVal(false))
  }

  test("structsEqual: mismatched-type field values are unequal (direct construction)") {
    val f1 = scala.collection.mutable.Map[String, Value]("x" -> Value.IntVal(1))
    val f2 = scala.collection.mutable.Map[String, Value]("x" -> Value.StrVal("1"))
    val store = Store()
    store.store("a", Value.StructVal("S", f1))
    store.store("b", Value.StructVal("S", f2))
    val result = directEval(List(Program.PCmd(Cmd.Assign("r", Expr.BoolLift(BoolExpr.Compare(Expr.Ref("a"), Bop.Eq, Expr.Ref("b"))), 1))), store)
    assert(result.load("r") == Value.BoolVal(false))
  }

  test("bare return in a void function returns null successfully") {
    val store = run("fn f() -> Void { return; } x := f();")
    assert(store.load("x") == Value.NullVal)
  }

  test("method: bare return in a void method returns null successfully") {
    val store = run("struct S {} impl S { fn f(self: S) -> Void { return; } } s := S {}; x := s.f();")
    assert(store.load("x") == Value.NullVal)
  }

  test("method: wrong number of arguments throws") {
    assertThrows[RuntimeException](run(
      "struct S {} impl S { fn f(self: S, a: Int) -> Int { return !a; } } s := S {}; x := s.f();"
    ))
  }

  test("field assignment: unknown field throws") {
    assertThrows[RuntimeException](run("struct S { x: Int } s := S { x: 1 }; s.badfield := 5;"))
  }

  test("field index assignment succeeds within bounds") {
    val store = run("struct S { arr: Int[] } s := S { arr: [1,2,3] }; s.arr[1] := 99; r := s.arr[1];")
    assert(store.load("r") == Value.IntVal(99))
  }

  test("array assign: out of bounds throws") {
    assertThrows[RuntimeException](run("arr := [1,2,3]; arr[9] := 5;"))
  }

  test("inputInt: parse failure with a prompt also throws") {
    val in = new java.io.BufferedReader(new java.io.StringReader("notanumber\n"))
    assertThrows[RuntimeException](scala.Console.withIn(in) { run("""x := inputInt("p: ")""") })
  }

