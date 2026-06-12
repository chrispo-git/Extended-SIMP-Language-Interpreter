package simp

//Lexer for SIMP

class Lexer(source: String):
    private var pos: Int = 0
    private val tokens = scala.collection.mutable.ListBuffer[Token]()
    private val lines = scala.collection.mutable.ListBuffer[Int]()
    private var line: Int = 1
    private val whitespaces : List[Char] = List(' ', '\t', '\n', '\r')
    private val numbers : List[Char] = List('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    private val valid_symbols : List[Char] = List(';', '(', ')', '&', '|', '¬', '+', '-', '/', '*', '.', ',', '%', '{', '}',':','[',']')

    private var isComment: Boolean = false

    private def throwError(msg: String): Nothing = throw RuntimeException(s"on line $line: $msg")

    def tokenise(): (List[Token], List[Int]) = {
        while !isAtEnd() do {
            skipWhitespace()
            if !isAtEnd() then scanToken()
        }
        tokens += Token.EOF 
        lines += line

        (tokens.toList, lines.toList)
    }

    private def isAtEnd(): Boolean = pos >= source.length()

    private def peek(): Char = if isAtEnd() then '\u0000' else source.charAt(pos)

    private def peekNext(): Char = if pos + 1 >= source.length() then '\u0000' else source.charAt(pos + 1)


    private def peekN(n: Int): Char = if pos + n >= source.length() then '\u0000' else source.charAt(pos + n)

    private def getWholeWord(): String = {
        val end = source.indexWhere(c => whitespaces.contains(c) || (!c.isLetterOrDigit && !List('_','-').contains(c)), pos) match {
            case -1 => source.length
            case n  => n
        }

        source.slice(pos, end)
    }
    private def getWholeFloat(): String = {
        val end = source.indexWhere(c => whitespaces.contains(c) || (!c.isLetterOrDigit && !List('_','-','.').contains(c)), pos) match {
            case -1 => source.length
            case n  => n
        }

        source.slice(pos, end)
    }
    private val escapeSequences = Map(
        'n'  -> '\n',
        't'  -> '\t',
        'r'  -> '\r',
        '\'' -> '\'',
        '"'  -> '"',
        '\\' -> '\\',
        'a'  -> '\u0007',
        'b'  -> '\u0008',
        'f'  -> '\u000C',
        'v'  -> '\u000B',
        '0'  -> '\u0000'
    )
    private def getWholeString(): String = {
        val out = StringBuilder()
        advance()
        while !isAtEnd() && peek() != '"' do {
            if peek() == '\\' then {
                advance()
                escapeSequences.get(peek()) match {
                    case Some(escaped) => {advance(); out += escaped}
                    case None => throwError(s"Unknown escape sequence '\\${peek()}'")
                }
            } else {
                out += advance()
            }
        }
        if isAtEnd() then throwError("Unexpected EOF - maybe you forgot to close a string")
        advance()
        out.toString
    }
    private def isWordMatch(word: String): Boolean = getWholeWord() == word

    private def isInteger(): Boolean = {

        val possibleNumber = getWholeWord()

        possibleNumber.nonEmpty && possibleNumber.forall(numbers.contains)
    }


    private def isFloat(): Boolean = {
        val possibleNumber = getWholeFloat()
        if possibleNumber.isEmpty then return false
        val parts = possibleNumber.split("\\.")
        parts.length == 2 &&
        parts(0).nonEmpty && parts(0).forall(numbers.contains) &&
        parts(1).nonEmpty && parts(1).forall(numbers.contains)
    }

    private def isNextInteger(): Boolean = {
        pos += 1
        val possibleNumber = getWholeWord()

        pos -= 1

        possibleNumber.nonEmpty && possibleNumber.forall(numbers.contains)
    }
    private def isNextFloat(): Boolean = {
        pos += 1
        val possibleNumber = getWholeFloat()
        pos -= 1
        if possibleNumber.isEmpty then return false
        val parts = possibleNumber.split("\\.")
        parts.length == 2 &&
        parts(0).nonEmpty && parts(0).forall(numbers.contains) &&
        parts(1).nonEmpty && parts(1).forall(numbers.contains)
    }
    private def isIdentifier(word: String): Boolean = {
        word.nonEmpty &&
        (
            word.head.isLetter ||
            word.head == '_'
        ) &&
        word.forall(c =>
            c.isLetterOrDigit ||
            c == '_'
        )
    }

    private def advance(): Char = {
        val char = source.charAt(pos)
        pos += 1
        char
    }
    private def advanceN(n : Int): Char = {
        val char = source.charAt(pos)
        pos += n
        char
    }

    private def skipWhitespace(): Unit = {
        while (whitespaces.contains(peek())) do {
            if peek() == '\n' then line += 1;
            advance()
        }
    }

    private def skipToNextLine(): Unit = {
        while !isAtEnd() && (peek() != '\n') do {
            advance()
        }
        line += 1;
        skipWhitespace()
    }

    private def advanceUntilNextWord(): Unit = {
        while !isAtEnd() && !whitespaces.contains(peek()) && !valid_symbols.contains(peek()) do {
            advance()
        }
        skipWhitespace()
    }

    private def scanToken(): Unit = {
        if peek() == '/' && peekNext() == '/' then {
            skipToNextLine();
            return
        }
        if peek() == '/' && peekNext() == '*' then {
            advanceN(2);
            skipWhitespace();
            isComment = true;
            return
        }
        if peek() == '*' && peekNext() == '/' then {
            advanceN(2);
            skipWhitespace();
            isComment = false;
        }
        if isComment then {
            advance();
            return
        }
        val token : Token = peek() match {
            
            case x if isFloat() => {val word = getWholeFloat(); advanceN(word.length); Token.LiteralFloat(word.toDouble)}
            case x if isInteger() => {val word = getWholeWord(); advanceN(word.length); Token.LiteralInt(word.toInt)}
            case x if isWordMatch("true") => {advanceUntilNextWord(); Token.BoolLit(true)}
            case x if isWordMatch("false") => {advanceUntilNextWord(); Token.BoolLit(false)}

            case '-' if isNextFloat() => {advance(); val word = getWholeFloat(); advanceN(word.length); Token.LiteralFloat(word.toDouble * -1)}
            case '-' if isNextInteger() => {advance(); val word = getWholeWord(); advanceN(word.length); Token.LiteralInt(word.toInt * -1)}

            case x if isWordMatch("match") => { advanceUntilNextWord(); Token.Match }
            case x if isWordMatch("case")  => { advanceUntilNextWord(); Token.Case }
            case '=' if peekNext() == '>'  => { advanceN(2); Token.FatArrow }
            
            case ':' if peekNext() == '=' => {advanceN(2);Token.Assign} 

            
            case '¬' => {advance();Token.Not} 
            case '&' if peekNext() == '&' => {advanceN(2);Token.And} 
            case '|' if peekNext() == '|' => {advanceN(2);Token.Or} 


            case '+' if peekNext() == '=' => {advanceN(2);Token.PlusEq} 
            case '-' if peekNext() == '=' => {advanceN(2);Token.MinusEq} 
            case '/' if peekNext() == '=' => {advanceN(2);Token.DivEq} 
            case '*' if peekNext() == '=' => {advanceN(2);Token.MulEq} 


            case '-' if peekNext() == '>' => {advanceN(2);Token.Arrow} 

            case '&' => {advance();Token.BitAnd} 
            case '|' => {advance();Token.BitOr} 
            case '^' => {advance();Token.BitXor}
            case '~' => {advance();Token.BitComplement}
            case '<' if peekNext() == '<' => {advanceN(2);Token.BitLeft}
            case '>' if peekNext() == '>' && peekN(2) == '>' => {advanceN(3);Token.BitRightFill}
            case '>' if peekNext() == '>' => {advanceN(2);Token.BitRight}

            case '>' if peekNext() == '=' => {advanceN(2);Token.Gte} 
            case '>' => {advance();Token.Gt} 
            case '<' if peekNext() == '=' => {advanceN(2);Token.Lte} 
            case '<' => {advance();Token.Lt}
            case '=' if peekNext() == '=' => {advanceN(2);Token.Eq} 
            case '!' if peekNext() == '=' => {advanceN(2);Token.Neq} 

            case '+' => {advance();Token.Add}
            case '-' => {advance();Token.Sub}
            case '/' => {advance();Token.Div} 
            case '%' => {advance();Token.Mod} 
            case '*' => {advance();Token.Mul} 
            case '!' => {advance();Token.Deref} 

            case x if isWordMatch("skip") => {advanceUntilNextWord(); Token.Skip}
            case x if isWordMatch("if") => {advanceUntilNextWord(); Token.If}
            case x if isWordMatch("then") => {advanceUntilNextWord(); Token.Then}
            case x if isWordMatch("else") => {advanceUntilNextWord(); Token.Else}
            case x if isWordMatch("elif") => {advanceUntilNextWord(); Token.Elif}
            case x if isWordMatch("while") => {advanceUntilNextWord(); Token.While}
            case x if isWordMatch("do") => {advanceUntilNextWord(); Token.Do}
            case x if isWordMatch("break") => {advanceUntilNextWord(); Token.Break}
            case x if isWordMatch("continue") => {advanceUntilNextWord(); Token.Continue}

            case x if isWordMatch("for") => { advanceUntilNextWord(); Token.For }
            case x if isWordMatch("in")  => { advanceUntilNextWord(); Token.In }


            case x if isWordMatch("import") => { advanceUntilNextWord(); Token.Import }
            case x if isWordMatch("as")  => { advanceUntilNextWord(); Token.As }
            case ':' if peekNext() == ':' => {advanceN(2);Token.DoubleColon} 

            case x if isWordMatch("fn") => {advanceUntilNextWord(); Token.Fn} 
            case x if isWordMatch("return") => {advanceUntilNextWord(); Token.Return} 
            case ',' => {advance();Token.Comma} 

            case x if isWordMatch("struct") => { advanceUntilNextWord(); Token.Struct }
            case '.' => { advance(); Token.Dot }

            case ';' => {advance();Token.Semicolon}
            case '(' => {advance();Token.OpenBracket}
            case ')' => {advance();Token.CloseBracket}
            case '{' => {advance();Token.OpenBrace}
            case '}' => {advance();Token.CloseBrace}
            case '[' => {advance(); Token.OpenSquare}
            case ']' => {advance(); Token.CloseSquare}

            case '"' => Token.StringLit(getWholeString())

            case ':' => {advance();Token.Colon}
            case x if isWordMatch("Int")  => {advanceUntilNextWord(); Token.TypeInt}
            case x if isWordMatch("Str")  => {advanceUntilNextWord(); Token.TypeString}
            case x if isWordMatch("Bool") => {advanceUntilNextWord(); Token.TypeBool}
            case x if isWordMatch("Void") => {advanceUntilNextWord(); Token.TypeNull}
            case x if isWordMatch("Map") => { advanceUntilNextWord(); Token.TypeMap }

            case x if isWordMatch("const") => {advanceUntilNextWord(); Token.Const}

            case x if isWordMatch("null") => { advanceUntilNextWord(); Token.Null }


            case x if isWordMatch("print") => {advanceUntilNextWord(); Token.Print}

            case x if isIdentifier(getWholeWord()) => {val word = getWholeWord(); advanceUntilNextWord(); Token.Variable(word)}

            case c => throwError(s"Unexpected character '$c' at line $line")
        }

        lines += line
        tokens += token
    }