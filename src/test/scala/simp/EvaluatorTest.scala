package simp

import org.scalatest.funsuite.AnyFunSuite

class EvaluatorTest extends AnyFunSuite:

  def run(source: String): Store =
    val store = Store()
    Evaluator(store).evalProgram(Parser(Lexer(source).tokenise()).parseProgram())
    store

  def storeOf(pairs: (String, Int)*): Map[String, Int] = pairs.toMap

  // Assignments
  test("assign a literal") {
    val store = run("x := 5")
    assert(store.load("x") == 5)
  }

  test("assign result of addition") {
    val store = run("x := 2 + 3")
    assert(store.load("x") == 5)
  }

  test("assign result of multiplication") {
    val store = run("x := 3 * 4")
    assert(store.load("x") == 12)
  }

  test("assign result of subtraction") {
    val store = run("x := 10 - 3")
    assert(store.load("x") == 7)
  }

  test("assign result of division") {
    val store = run("x := 10 / 2")
    assert(store.load("x") == 5)
  }

  test("division by zero throws") {
    assertThrows[RuntimeException](run("x := 5 / 0"))
  }

  // Dereference
  test("dereference assigned variable") {
    val store = run("x := 5 ; y := !x")
    assert(store.load("y") == 5)
  }

  test("dereference in expression") {
    val store = run("x := 5 ; y := !x + 3")
    assert(store.load("y") == 8)
  }

  test("unbound location throws") {
    assertThrows[RuntimeException](run("x := !y"))
  }

  // Sequencing
  test("sequence executes in order") {
    val store = run("x := 1 ; x := 2")
    assert(store.load("x") == 2)
  }

  test("sequence assigns multiple variables") {
    val store = run("x := 1 ; y := 2 ; z := 3")
    assert(store.load("x") == 1)
    assert(store.load("y") == 2)
    assert(store.load("z") == 3)
  }

  // Skip
  test("skip does nothing") {
    val store = run("x := 5 ; skip")
    assert(store.load("x") == 5)
  }

  // If
  test("if true executes then branch") {
    val store2 = run("if true then x := 1 else x := 2")
    assert(store2.load("x") == 1)
  }

  test("if false executes else branch") {
    val store = run("if false then x := 1 else x := 2")
    assert(store.load("x") == 2)
  }

  test("if with comparison true") {
    val store = run("x := 5 ; if !x > 3 then y := 1 else y := 0")
    assert(store.load("y") == 1)
  }

  test("if with comparison false") {
    val store = run("x := 1 ; if !x > 3 then y := 1 else y := 0")
    assert(store.load("y") == 0)
  }

  // While
  test("while loop counts down to zero") {
    val store = run("x := 5 ; while !x > 0 do x := !x - 1")
    assert(store.load("x") == 0)
  }

  test("while loop never executes if condition false") {
    val store = run("x := 0 ; while !x > 0 do x := !x - 1")
    assert(store.load("x") == 0)
  }

  test("while loop with accumulator") {
    val store = run("x := 5 ; acc := 0 ; while !x > 0 do (acc := !acc + !x ; x := !x - 1)")
    assert(store.load("acc") == 15)
    assert(store.load("x") == 0)
  }

  // Boolean logic
  test("not true is false") {
    val store = run("if ¬true then x := 1 else x := 0")
    assert(store.load("x") == 0)
  }

  test("not false is true") {
    val store = run("if ¬false then x := 1 else x := 0")
    assert(store.load("x") == 1)
  }

  test("and both true") {
    val store = run("if true && true then x := 1 else x := 0")
    assert(store.load("x") == 1)
  }

  test("and one false") {
    val store = run("if true && false then x := 1 else x := 0")
    assert(store.load("x") == 0)
  }

  test("or one true") {
    val store = run("if false  || true then x := 1 else x := 0")
    assert(store.load("x") == 1)
  }

  test("or both false") {
    val store = run("if false  || false then x := 1 else x := 0")
    assert(store.load("x") == 0)
  }

  // Comparators
  test("greater than") {
    val store = run("x := 5 ; if !x > 3 then y := 1 else y := 0")
    assert(store.load("y") == 1)
  }

  test("less than") {
    val store = run("x := 2 ; if !x < 3 then y := 1 else y := 0")
    assert(store.load("y") == 1)
  }

  test("equal") {
    val store = run("x := 3 ; if !x == 3 then y := 1 else y := 0")
    assert(store.load("y") == 1)
  }

  test("greater than or equal") {
    val store = run("x := 3 ; if !x >= 3 then y := 1 else y := 0")
    assert(store.load("y") == 1)
  }

  test("less than or equal") {
    val store = run("x := 3 ; if !x <= 3 then y := 1 else y := 0")
    assert(store.load("y") == 1)
  }

  // Integration
  test("factorial of 5") {
    val store = run(
      "x := 5 ; acc := 1 ; while !x > 0 do (acc := !acc * !x ; x := !x - 1)"
    )
    assert(store.load("acc") == 120)
  }

  test("fibonacci") {
    val store = run(
      "a := 0 ; b := 1 ; n := 10 ; while !n > 0 do (tmp := !b ; b := !a + !b ; a := !tmp ; n := !n - 1)"
    )
    assert(store.load("a") == 55)
  }