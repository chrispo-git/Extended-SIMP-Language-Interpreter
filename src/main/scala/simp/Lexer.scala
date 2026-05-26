package simp

//Lexer for SIMP

class Lexer(source: String):
    private var pos: Int = 0
    private val tokens = scala.collection.mutable.ListBuffer[Token]()
    private var line: Int = 1
    private val whitespaces : List[Char] = List(' ', '\t', '\n', '\r')
    private val numbers : List[Char] = List('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    private val valid_symbols : List[Char] = List(';', '(', ')', '&', '|', '¬', '+', '-', '/', '*')


    def tokenise(): List[Token] = {
        while !isAtEnd() do {
            skipWhitespace()
            if !isAtEnd() then scanToken()
        }
        tokens += Token.EOF 

        tokens.toList
    }

    private def isAtEnd(): Boolean = pos >= source.length()

    private def peek(): Char = if isAtEnd() then '\u0000' else source.charAt(pos)

    private def peekNext(): Char = if pos + 1 >= source.length() then '\u0000' else source.charAt(pos + 1)


    private def peekN(n: Int): Char = if pos + n >= source.length() then '\u0000' else source.charAt(pos + n)

    private def getWholeWord(): String = {
        val end = source.indexWhere(c => whitespaces.contains(c) || (!c.isLetterOrDigit && !List('_','-').contains(c)), pos) match
            case -1 => source.length
            case n  => n

        source.slice(pos, end)
    }
    private def isWordMatch(word: String): Boolean = getWholeWord() == word

    private def isInteger(): Boolean = {

        val possibleNumber = getWholeWord()

        possibleNumber.nonEmpty && possibleNumber.forall(numbers.contains)
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
            advance()
        }
    }

    private def advanceUntilNextWord(): Unit = {
        while !isAtEnd() && !whitespaces.contains(peek()) && !valid_symbols.contains(peek()) do {
            advance()
        }
        skipWhitespace()
    }

    private def scanToken(): Unit = {
        val token : Token = peek() match {
            
            case x if isInteger() => {val word = getWholeWord().toInt; advanceUntilNextWord(); Token.LiteralInt(word)}
            case x if isWordMatch("true") => {advanceUntilNextWord(); Token.BoolLit(true)}
            case x if isWordMatch("false") => {advanceUntilNextWord(); Token.BoolLit(false)}

            case '+' => {advance();Token.Add}
            case '-' => {advance();Token.Sub}
            case '/' => {advance();Token.Div} 
            case '*' => {advance();Token.Mul} 
            case '!' => {advance();Token.Deref} 
            
            case ':' if peekNext() == '=' => {advanceN(2);Token.Assign} 
            
            case '¬' => {advance();Token.Not} 
            case '&' if peekNext() == '&' => {advanceN(2);Token.And} 
            case '|' if peekNext() == '|' => {advanceN(2);Token.Or} 

            case '>' if peekNext() == '=' => {advanceN(2);Token.Gte} 
            case '>' => {advance();Token.Gt} 
            case '<' if peekNext() == '=' => {advanceN(2);Token.Lte} 
            case '<' => {advance();Token.Lt}
            case '=' if peekNext() == '=' => {advanceN(2);Token.Eq} 

            case x if isWordMatch("skip") => {advanceUntilNextWord(); Token.Skip}
            case x if isWordMatch("if") => {advanceUntilNextWord(); Token.If}
            case x if isWordMatch("then") => {advanceUntilNextWord(); Token.Then}
            case x if isWordMatch("else") => {advanceUntilNextWord(); Token.Else}
            case x if isWordMatch("while") => {advanceUntilNextWord(); Token.While}
            case x if isWordMatch("do") => {advanceUntilNextWord(); Token.Do}

            case ';' => {advance();Token.Semicolon}
            case '(' => {advance();Token.OpenBracket}
            case ')' => {advance();Token.CloseBracket}

            case x if isIdentifier(getWholeWord()) => {val word = getWholeWord(); advanceUntilNextWord(); Token.Variable(word)}

            case c => throw RuntimeException(s"Unexpected character '$c' at line $line")
        }

        tokens += token
    }