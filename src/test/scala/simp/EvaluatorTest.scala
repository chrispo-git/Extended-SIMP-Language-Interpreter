package simp

import org.scalatest.funsuite.AnyFunSuite

class EvaluatorTest extends AnyFunSuite:

  def run(source: String): Store =
    val store = Store()
    val fnEnv = FunctionEnv()
    Builtins.register(fnEnv)
    val tokens = Lexer(source).tokenise()
    val program = Parser(tokens).parseProgram()
    Evaluator(fnEnv).evalProgram(program, store)
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
    val store2 = run("if true then {x := 1} else {x := 2}")
    assert(store2.load("x") == Value.IntVal(1))
  }

  test("if false executes else branch") {
    val store = run("if false then {x := 1} else {x := 2}")
    assert(store.load("x") == Value.IntVal(2))
  }

  test("if with comparison true") {
    val store = run("x := 5 ; if !x > 3 then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("if with comparison false") {
    val store = run("x := 1 ; if !x > 3 then {y := 1} else {y := 0}")
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
    val store = run("if ¬true then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(0))
  }

  test("not false is true") {
    val store = run("if ¬false then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("and both true") {
    val store = run("if true && true then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("and one false") {
    val store = run("if true && false then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(0))
  }

  test("or one true") {
    val store = run("if false  || true then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(1))
  }

  test("or both false") {
    val store = run("if false  || false then {x := 1} else {x := 0}")
    assert(store.load("x") == Value.IntVal(0))
  }

  // Comparators
  test("greater than") {
    val store = run("x := 5 ; if !x > 3  then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("less than") {
    val store = run("x := 2 ; if !x < 3 then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("equal") {
    val store = run("x := 3 ; if !x == 3 then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("greater than or equal") {
    val store = run("x := 3 ; if !x >= 3 then {y := 1} else {y := 0}")
    assert(store.load("y") == Value.IntVal(1))
  }

  test("less than or equal") {
    val store = run("x := 3 ; if !x <= 3 then {y := 1}  else {y := 0} ")
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
      "x := 2 ; if !x == 1 then { y := 1 } elif !x == 2 then { y := 2 } else { y := 3 }"
    )
    assert(store.load("y") == Value.IntVal(2))
  }

  test("elif falls to else") {
    val store = run(
      "x := 3 ; if !x == 1 then { y := 1 } elif !x == 2 then { y := 2 } else { y := 3 }"
    )
    assert(store.load("y") == Value.IntVal(3))
  }

  test("chained elif") {
    val store = run(
      "x := 3 ; if !x == 1 then { y := 1 } elif !x == 2 then { y := 2 } elif !x == 3 then { y := 3 } else { y := 4 }"
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

  // Procedures
  test("procedure modifies nothing in caller store") {
    val store = run("pd noop(n : Int) { n := 99; } x := 5 ; call noop(!x);")
    assert(store.load("x") == Value.IntVal(5))
  }

  test("procedure call with side effects") {
    val store = run("pd setY(n : Int) { y := !n; } call setY(42);")
    // y only exists in localStore, not in caller store
    assertThrows[RuntimeException](store.load("y"))
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

  test("array mutation in procedure propagates to caller") {
      val store = run(
          "pd double(a: Int[]) { i := 0; while !i < len(a) do { a[!i] := a[!i] * 2; i += 1; }; } nums := [1, 2, 3]; call double(nums);"
      )
      assert(store.load("nums") == Value.ArrVal(scala.collection.mutable.ArrayBuffer(Value.IntVal(2), Value.IntVal(4), Value.IntVal(6))))
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