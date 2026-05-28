package simp

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class LexerTest extends AnyFunSuite with Matchers {

  private def lex(input: String): List[Token] =
    Lexer(input).tokenise()

  // Integer literals

  test("lex single integer literal") {
    lex("123") shouldEqual List(
      Token.LiteralInt(123),
      Token.EOF
    )
  }

  test("lex multiple integer literals") {
    lex("1 22 333") shouldEqual List(
      Token.LiteralInt(1),
      Token.LiteralInt(22),
      Token.LiteralInt(333),
      Token.EOF
    )
  }

  test("integer followed by newline") {
    lex("42\n") shouldEqual List(
      Token.LiteralInt(42),
      Token.EOF
    )
  }

  // Boolean literals

  test("lex boolean true") {
    lex("true") shouldEqual List(
      Token.BoolLit(true),
      Token.EOF
    )
  }

  test("lex boolean false") {
    lex("false") shouldEqual List(
      Token.BoolLit(false),
      Token.EOF
    )
  }

  test("lex sequence of booleans") {
    lex("true false true") shouldEqual List(
      Token.BoolLit(true),
      Token.BoolLit(false),
      Token.BoolLit(true),
      Token.EOF
    )
  }

  // Arithmetic operators

  test("lex addition operator") {
    lex("+") shouldEqual List(
      Token.Add,
      Token.EOF
    )
  }

  test("lex subtraction operator") {
    lex("-") shouldEqual List(
      Token.Sub,
      Token.EOF
    )
  }

  test("lex multiplication operator") {
    lex("*") shouldEqual List(
      Token.Mul,
      Token.EOF
    )
  }

  test("lex division operator") {
    lex("/") shouldEqual List(
      Token.Div,
      Token.EOF
    )
  }

  test("lex dereference operator") {
    lex("!") shouldEqual List(
      Token.Deref,
      Token.EOF
    )
  }

  test("lex arithmetic expression") {
    lex("1 + 2 - 3 * 4 / 5") shouldEqual List(
      Token.LiteralInt(1),
      Token.Add,
      Token.LiteralInt(2),
      Token.Sub,
      Token.LiteralInt(3),
      Token.Mul,
      Token.LiteralInt(4),
      Token.Div,
      Token.LiteralInt(5),
      Token.EOF
    )
  }

  // Assignment and comparison

  test("lex assignment operator") {
    lex(":=") shouldEqual List(
      Token.Assign,
      Token.EOF
    )
  }

  test("lex equality operator") {
    lex("==") shouldEqual List(
      Token.Eq,
      Token.EOF
    )
  }

  test("lex greater than operator") {
    lex(">") shouldEqual List(
      Token.Gt,
      Token.EOF
    )
  }

  test("lex greater than or equal operator") {
    lex(">=") shouldEqual List(
      Token.Gte,
      Token.EOF
    )
  }

  test("lex less than operator") {
    lex("<") shouldEqual List(
      Token.Lt,
      Token.EOF
    )
  }

  test("lex less than or equal operator") {
    lex("<=") shouldEqual List(
      Token.Lte,
      Token.EOF
    )
  }

  test("lex comparison expression") {
    lex("x >= 10") shouldEqual List(
      Token.Variable("x"),
      Token.Gte,
      Token.LiteralInt(10),
      Token.EOF
    )
  }

  // Boolean operators

  test("lex not operator") {
    lex("¬") shouldEqual List(
      Token.Not,
      Token.EOF
    )
  }

  test("lex and operator") {
    lex("&&") shouldEqual List(
      Token.And,
      Token.EOF
    )
  }

  test("lex or operator") {
    lex("||") shouldEqual List(
      Token.Or,
      Token.EOF
    )
  }

  test("lex boolean expression") {
    lex("true && false || ¬ false") shouldEqual List(
      Token.BoolLit(true),
      Token.And,
      Token.BoolLit(false),
      Token.Or,
      Token.Not,
      Token.BoolLit(false),
      Token.EOF
    )
  }

  
  // Keywords
  

  test("lex skip keyword") {
    lex("skip") shouldEqual List(
      Token.Skip,
      Token.EOF
    )
  }

  test("lex if keyword") {
    lex("if") shouldEqual List(
      Token.If,
      Token.EOF
    )
  }

  test("lex then keyword") {
    lex("then") shouldEqual List(
      Token.Then,
      Token.EOF
    )
  }

  test("lex else keyword") {
    lex("else") shouldEqual List(
      Token.Else,
      Token.EOF
    )
  }

  test("lex while keyword") {
    lex("while") shouldEqual List(
      Token.While,
      Token.EOF
    )
  }

  test("lex do keyword") {
    lex("do") shouldEqual List(
      Token.Do,
      Token.EOF
    )
  }

  
  // Delimiters
  

  test("lex semicolon") {
    lex(";") shouldEqual List(
      Token.Semicolon,
      Token.EOF
    )
  }

  test("lex open bracket") {
    lex("(") shouldEqual List(
      Token.OpenBracket,
      Token.EOF
    )
  }

  test("lex close bracket") {
    lex(")") shouldEqual List(
      Token.CloseBracket,
      Token.EOF
    )
  }

  test("lex bracketed expression") {
    lex("(1 + 2)") shouldEqual List(
      Token.OpenBracket,
      Token.LiteralInt(1),
      Token.Add,
      Token.LiteralInt(2),
      Token.CloseBracket,
      Token.EOF
    )
  }

  
  // Variables / identifiers
  

  test("lex simple identifier") {
    lex("abc") shouldEqual List(
      Token.Variable("abc"),
      Token.EOF
    )
  }

  test("lex identifier with underscore") {
    lex("_temp") shouldEqual List(
      Token.Variable("_temp"),
      Token.EOF
    )
  }

  test("lex identifier with digits") {
    lex("var123") shouldEqual List(
      Token.Variable("var123"),
      Token.EOF
    )
  }

  test("keywords should not match prefixes") {
    lex("ifx theny while_loop") shouldEqual List(
      Token.Variable("ifx"),
      Token.Variable("theny"),
      Token.Variable("while_loop"),
      Token.EOF
    )
  }

  test("trueValue should be identifier not boolean") {
    lex("trueValue") shouldEqual List(
      Token.Variable("trueValue"),
      Token.EOF
    )
  }

  test("false_1 should be identifier not boolean") {
    lex("false_1") shouldEqual List(
      Token.Variable("false_1"),
      Token.EOF
    )
  }

  
  // Whitespace handling
  

  test("ignore spaces") {
    lex("   1   +   2   ") shouldEqual List(
      Token.LiteralInt(1),
      Token.Add,
      Token.LiteralInt(2),
      Token.EOF
    )
  }

  test("ignore tabs") {
    lex("\t1\t+\t2\t") shouldEqual List(
      Token.LiteralInt(1),
      Token.Add,
      Token.LiteralInt(2),
      Token.EOF
    )
  }

  test("ignore newlines") {
    lex("1\n+\n2") shouldEqual List(
      Token.LiteralInt(1),
      Token.Add,
      Token.LiteralInt(2),
      Token.EOF
    )
  }

  test("ignore carriage returns") {
    lex("1\r+\r2") shouldEqual List(
      Token.LiteralInt(1),
      Token.Add,
      Token.LiteralInt(2),
      Token.EOF
    )
  }

  
  // Complex programs
  

  test("lex assignment statement") {
    lex("x := 10") shouldEqual List(
      Token.Variable("x"),
      Token.Assign,
      Token.LiteralInt(10),
      Token.EOF
    )
  }

  test("lex sequence of statements") {
    lex("x := 1; y := 2") shouldEqual List(
      Token.Variable("x"),
      Token.Assign,
      Token.LiteralInt(1),
      Token.Semicolon,
      Token.Variable("y"),
      Token.Assign,
      Token.LiteralInt(2),
      Token.EOF
    )
  }

  test("lex if statement") {
    lex("if true then skip else skip") shouldEqual List(
      Token.If,
      Token.BoolLit(true),
      Token.Then,
      Token.Skip,
      Token.Else,
      Token.Skip,
      Token.EOF
    )
  }

  test("lex while loop") {
    lex("while x < 10 do x := x + 1") shouldEqual List(
      Token.While,
      Token.Variable("x"),
      Token.Lt,
      Token.LiteralInt(10),
      Token.Do,
      Token.Variable("x"),
      Token.Assign,
      Token.Variable("x"),
      Token.Add,
      Token.LiteralInt(1),
      Token.EOF
    )
  }

  test("lex nested expression") {
    lex("(x + 1) * (y - 2)") shouldEqual List(
      Token.OpenBracket,
      Token.Variable("x"),
      Token.Add,
      Token.LiteralInt(1),
      Token.CloseBracket,
      Token.Mul,
      Token.OpenBracket,
      Token.Variable("y"),
      Token.Sub,
      Token.LiteralInt(2),
      Token.CloseBracket,
      Token.EOF
    )
  }

  
  // Error cases
  

  test("throw exception on invalid character") {
    val ex = intercept[RuntimeException] {
      lex("@")
    }

    ex.getMessage should include ("Unexpected character")
  }

  test("throw exception on single ampersand") {
    intercept[RuntimeException] {
      lex("&")
    }
  }

  test("throw exception on single pipe") {
    intercept[RuntimeException] {
      lex("|")
    }
  }


  
  // Edge cases
  

  test("empty input") {
    lex("") shouldEqual List(
      Token.EOF
    )
  }

  test("whitespace only input") {
    lex("   \n\t\r  ") shouldEqual List(
      Token.EOF
    )
  }

  test("single identifier character") {
    lex("x") shouldEqual List(
      Token.Variable("x"),
      Token.EOF
    )
  }

  test("single digit integer") {
    lex("7") shouldEqual List(
      Token.LiteralInt(7),
      Token.EOF
    )
  }

  test("long mixed program") {
    val program =
      """
        while counter < 10 do
          if counter == 5 then
            skip
          else
            counter := counter + 1;
      """

    lex(program) shouldEqual List(
      Token.While,
      Token.Variable("counter"),
      Token.Lt,
      Token.LiteralInt(10),
      Token.Do,
      Token.If,
      Token.Variable("counter"),
      Token.Eq,
      Token.LiteralInt(5),
      Token.Then,
      Token.Skip,
      Token.Else,
      Token.Variable("counter"),
      Token.Assign,
      Token.Variable("counter"),
      Token.Add,
      Token.LiteralInt(1),
      Token.Semicolon,
      Token.EOF
    )
  }
}