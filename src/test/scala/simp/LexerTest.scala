package simp

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class LexerTest extends AnyFunSuite with Matchers {

  private def lex(input: String): List[Token] =
    Lexer(input, input.split('\n').toList).tokenise()._1

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

  // Float literals

  test("lex float literal") {
    lex("1.5") shouldEqual List(Token.LiteralFloat(1.5), Token.EOF)
  }

  test("lex float arithmetic") {
    lex("1.5 + 2.25") shouldEqual List(
      Token.LiteralFloat(1.5), Token.Add, Token.LiteralFloat(2.25), Token.EOF
    )
  }

  // Negative literals

  test("lex negative integer literal") {
    lex("-5") shouldEqual List(Token.LiteralInt(-5), Token.EOF)
  }

  test("lex negative float literal") {
    lex("-1.5") shouldEqual List(Token.LiteralFloat(-1.5), Token.EOF)
  }

  test("lex binary minus after variable is not negative literal") {
    lex("x - 5") shouldEqual List(
      Token.Variable("x"), Token.Sub, Token.LiteralInt(5), Token.EOF
    )
  }

  test("lex binary minus after int literal is not negative literal") {
    lex("5 - 5") shouldEqual List(
      Token.LiteralInt(5), Token.Sub, Token.LiteralInt(5), Token.EOF
    )
  }

  test("lex minus followed by space then number is subtraction not negative") {
    lex("- 5") shouldEqual List(Token.Sub, Token.LiteralInt(5), Token.EOF)
  }

  test("lex bare minus operator") {
    lex("x - y") shouldEqual List(
      Token.Variable("x"), Token.Sub, Token.Variable("y"), Token.EOF
    )
  }

  // Not-equal

  test("lex not equal operator") {
    lex("!=") shouldEqual List(Token.Neq, Token.EOF)
  }

  // Compound assignment

  test("lex plus-equals operator") {
    lex("+=") shouldEqual List(Token.PlusEq, Token.EOF)
  }

  test("lex minus-equals operator") {
    lex("-=") shouldEqual List(Token.MinusEq, Token.EOF)
  }

  test("lex mul-equals operator") {
    lex("*=") shouldEqual List(Token.MulEq, Token.EOF)
  }

  test("lex div-equals operator") {
    lex("/=") shouldEqual List(Token.DivEq, Token.EOF)
  }

  // Arrow / colon / double-colon

  test("lex arrow operator") {
    lex("->") shouldEqual List(Token.Arrow, Token.EOF)
  }

  test("lex colon") {
    lex(":") shouldEqual List(Token.Colon, Token.EOF)
  }

  test("lex double colon") {
    lex("::") shouldEqual List(Token.DoubleColon, Token.EOF)
  }

  // Braces / squares / comma / dot

  test("lex open and close brace") {
    lex("{}") shouldEqual List(Token.OpenBrace, Token.CloseBrace, Token.EOF)
  }

  test("lex open and close square") {
    lex("[]") shouldEqual List(Token.OpenSquare, Token.CloseSquare, Token.EOF)
  }

  test("lex comma") {
    lex(",") shouldEqual List(Token.Comma, Token.EOF)
  }

  test("lex dot") {
    lex(".") shouldEqual List(Token.Dot, Token.EOF)
  }

  // Bitwise operators

  test("lex bitwise and") {
    lex("&") shouldEqual List(Token.BitAnd, Token.EOF)
  }

  test("lex bitwise or") {
    lex("|") shouldEqual List(Token.BitOr, Token.EOF)
  }

  test("lex bitwise xor") {
    lex("^") shouldEqual List(Token.BitXor, Token.EOF)
  }

  test("lex bitwise complement") {
    lex("~") shouldEqual List(Token.BitComplement, Token.EOF)
  }

  test("lex bitwise left shift") {
    lex("<<") shouldEqual List(Token.BitLeft, Token.EOF)
  }

  test("lex bitwise right shift") {
    lex(">>") shouldEqual List(Token.BitRight, Token.EOF)
  }

  test("lex bitwise right fill shift") {
    lex(">>>") shouldEqual List(Token.BitRightFill, Token.EOF)
  }

  // Match / case

  test("lex match keyword") {
    lex("match") shouldEqual List(Token.Match, Token.EOF)
  }

  test("lex case keyword") {
    lex("case") shouldEqual List(Token.Case, Token.EOF)
  }

  test("lex fat arrow") {
    lex("=>") shouldEqual List(Token.FatArrow, Token.EOF)
  }

  // Remaining keywords

  test("lex elif keyword") {
    lex("elif") shouldEqual List(Token.Elif, Token.EOF)
  }

  test("lex break keyword") {
    lex("break") shouldEqual List(Token.Break, Token.EOF)
  }

  test("lex continue keyword") {
    lex("continue") shouldEqual List(Token.Continue, Token.EOF)
  }

  test("lex for keyword") {
    lex("for") shouldEqual List(Token.For, Token.EOF)
  }

  test("lex in keyword") {
    lex("in") shouldEqual List(Token.In, Token.EOF)
  }

  test("lex import keyword") {
    lex("import") shouldEqual List(Token.Import, Token.EOF)
  }

  test("lex as keyword") {
    lex("as") shouldEqual List(Token.As, Token.EOF)
  }

  test("lex fn keyword") {
    lex("fn") shouldEqual List(Token.Fn, Token.EOF)
  }

  test("lex return keyword") {
    lex("return") shouldEqual List(Token.Return, Token.EOF)
  }

  test("lex struct keyword") {
    lex("struct") shouldEqual List(Token.Struct, Token.EOF)
  }

  test("lex const keyword") {
    lex("const") shouldEqual List(Token.Const, Token.EOF)
  }

  test("lex null keyword") {
    lex("null") shouldEqual List(Token.Null, Token.EOF)
  }

  test("lex impl keyword") {
    lex("impl") shouldEqual List(Token.Impl, Token.EOF)
  }

  test("lex print keyword") {
    lex("print") shouldEqual List(Token.Print, Token.EOF)
  }

  // Type keywords

  test("lex Int type keyword") {
    lex("Int") shouldEqual List(Token.TypeInt, Token.EOF)
  }

  test("lex Str type keyword") {
    lex("Str") shouldEqual List(Token.TypeString, Token.EOF)
  }

  test("lex Bool type keyword") {
    lex("Bool") shouldEqual List(Token.TypeBool, Token.EOF)
  }

  test("lex Float type keyword") {
    lex("Float") shouldEqual List(Token.TypeFloat, Token.EOF)
  }

  test("lex Void type keyword") {
    lex("Void") shouldEqual List(Token.TypeNull, Token.EOF)
  }

  test("lex Map type keyword") {
    lex("Map") shouldEqual List(Token.TypeMap, Token.EOF)
  }

  // String literals

  test("lex simple string literal") {
    lex("\"hello\"") shouldEqual List(Token.StringLit("hello"), Token.EOF)
  }

  test("lex empty string literal") {
    lex("\"\"") shouldEqual List(Token.StringLit(""), Token.EOF)
  }

  test("lex string with newline escape") {
    lex("\"a\\nb\"") shouldEqual List(Token.StringLit("a\nb"), Token.EOF)
  }

  test("lex string with tab escape") {
    lex("\"a\\tb\"") shouldEqual List(Token.StringLit("a\tb"), Token.EOF)
  }

  test("lex string with carriage return escape") {
    lex("\"a\\rb\"") shouldEqual List(Token.StringLit("a\rb"), Token.EOF)
  }

  test("lex string with escaped single quote") {
    lex("\"a\\'b\"") shouldEqual List(Token.StringLit("a'b"), Token.EOF)
  }

  test("lex string with escaped double quote") {
    lex("\"a\\\"b\"") shouldEqual List(Token.StringLit("a\"b"), Token.EOF)
  }

  test("lex string with escaped backslash") {
    lex("\"a\\\\b\"") shouldEqual List(Token.StringLit("a\\b"), Token.EOF)
  }

  test("lex string with bell escape") {
    lex("\"a\\ab\"") shouldEqual List(Token.StringLit("ab"), Token.EOF)
  }

  test("lex string with backspace escape") {
    lex("\"a\\bb\"") shouldEqual List(Token.StringLit("ab"), Token.EOF)
  }

  test("lex string with form feed escape") {
    lex("\"a\\fb\"") shouldEqual List(Token.StringLit("ab"), Token.EOF)
  }

  test("lex string with vertical tab escape") {
    lex("\"a\\vb\"") shouldEqual List(Token.StringLit("ab"), Token.EOF)
  }

  test("lex string with null escape") {
    lex("\"a\\0b\"") shouldEqual List(Token.StringLit("a b"), Token.EOF)
  }

  test("throw on unknown escape sequence") {
    val ex = intercept[RuntimeException] { lex("\"a\\qb\"") }
    ex.getMessage should include ("Unknown escape sequence")
  }

  test("throw on unterminated string") {
    val ex = intercept[RuntimeException] { lex("\"abc") }
    ex.getMessage should include ("Unexpected EOF")
  }

  // Comments

  test("line comment is ignored") {
    lex("x := 1 // this is a comment") shouldEqual List(
      Token.Variable("x"), Token.Assign, Token.LiteralInt(1), Token.EOF
    )
  }

  test("line comment at start of input") {
    lex("// comment only") shouldEqual List(Token.EOF)
  }

  test("block comment is ignored") {
    lex("x := /* comment */ 1") shouldEqual List(
      Token.Variable("x"), Token.Assign, Token.LiteralInt(1), Token.EOF
    )
  }

  test("block comment spanning tokens") {
    lex("x := /* 1 + 2 */ 3") shouldEqual List(
      Token.Variable("x"), Token.Assign, Token.LiteralInt(3), Token.EOF
    )
  }

  test("block comment spanning multiple lines") {
    lex("x := /* line one\nline two */ 3") shouldEqual List(
      Token.Variable("x"), Token.Assign, Token.LiteralInt(3), Token.EOF
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

  test("line comment followed by code on the next line consumes the newline") {
    lex("// comment\nx") shouldEqual List(Token.Variable("x"), Token.EOF)
  }

  // canBeBinaryContext() is only actually evaluated (rather than short-circuited by
  // isNextFloat()/isNextInteger() already being false) when '-' is immediately
  // followed by a digit with no space, so these omit the space deliberately.

  test("lex binary minus with no space after close paren is not negative literal") {
    lex("(1)-5") shouldEqual List(
      Token.OpenBracket, Token.LiteralInt(1), Token.CloseBracket, Token.Sub, Token.LiteralInt(5), Token.EOF
    )
  }

  test("lex binary minus with no space after close square is not negative literal") {
    lex("arr[0]-5") shouldEqual List(
      Token.Variable("arr"), Token.OpenSquare, Token.LiteralInt(0), Token.CloseSquare, Token.Sub, Token.LiteralInt(5), Token.EOF
    )
  }

  test("lex binary minus with no space after close brace is not negative literal") {
    lex("{1}-5") shouldEqual List(
      Token.OpenBrace, Token.LiteralInt(1), Token.CloseBrace, Token.Sub, Token.LiteralInt(5), Token.EOF
    )
  }

  test("lex binary minus with no space after variable is not negative literal") {
    lex("x-5") shouldEqual List(Token.Variable("x"), Token.Sub, Token.LiteralInt(5), Token.EOF)
  }

  test("lex binary minus with no space after int literal is not negative literal") {
    lex("5-5") shouldEqual List(Token.LiteralInt(5), Token.Sub, Token.LiteralInt(5), Token.EOF)
  }

  test("lex binary minus with no space after float literal is not negative literal") {
    lex("1.5-5") shouldEqual List(Token.LiteralFloat(1.5), Token.Sub, Token.LiteralInt(5), Token.EOF)
  }

  test("lex binary minus with no space after string literal is not negative literal") {
    lex("\"a\"-5") shouldEqual List(Token.StringLit("a"), Token.Sub, Token.LiteralInt(5), Token.EOF)
  }

  test("lex binary minus with no space after bool literal is not negative literal") {
    lex("true-5") shouldEqual List(Token.BoolLit(true), Token.Sub, Token.LiteralInt(5), Token.EOF)
  }

  test("lex binary minus with no space after null literal is not negative literal") {
    lex("null-5") shouldEqual List(Token.Null, Token.Sub, Token.LiteralInt(5), Token.EOF)
  }
}