package simp

import org.scalatest.funsuite.AnyFunSuite

class EvaluatorTest extends AnyFunSuite:

  def run(source: String): Store =
    val store = Store()
    val fnEnv = FunctionEnv()
    val structEnv = StructEnv()
    Builtins.register(fnEnv)
    val sourceLines = source.split('\n').toList
    val tokens = Lexer(source, sourceLines).tokenise()
    val program = Parser(tokens._1, StructEnv(), tokens._2, sourceLines).parseProgram()
    Evaluator(fnEnv, structEnv, sourceLines).evalProgram(program, store)
    store

  def storeOf(pairs: (String, Int)*): Map[String, Int] = pairs.toMap

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