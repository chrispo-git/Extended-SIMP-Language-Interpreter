package simp

import SimpUtils.*

class Parser(protected val tokens: List[Token], protected val structEnv : StructEnv, protected val lines: List[Int], protected val sourceLines: List[String])
    extends ParserFolding
    with ParserExpr
    with ParserFunctions
    with ParserDecl
    with ParserCmd
    with ParserBoolExpr
    with ParserRepl:
    
    protected var pos: Int = 0

    protected def isAtEnd(): Boolean = pos >= tokens.length

    protected def peek(): Token =  if isAtEnd() then Token.EOF else tokens(pos)

    protected def peekNext(): Token = if pos+1 >= tokens.length then Token.EOF else tokens(pos+1)

    protected def peekN(n: Int): Token = if pos+n >= tokens.length then Token.EOF else tokens(pos+n)

    protected def advance(): Token = if isAtEnd() then Token.EOF else {val t = tokens(pos);pos+=1;t}

    protected def advanceTo(n: Int): Token = if isAtEnd() then Token.EOF else {val t = tokens(pos);pos+=n;t} 

    protected def expect(expected: Token): Unit = {
        if peek() != expected then throwError(s"Expected '$expected', got '${peek()}'")
        else advance()
    }

    protected def currentLine(): Int = if pos < lines.length then lines(pos) else -1
    protected def currentLineSource(): String = if currentLine() > -1 then sourceLines(currentLine()-1).trim else ""
    protected def throwError(msg: String): Nothing = {
        throw RuntimeException(s"on line ${currentLine()}\n${currentLineSource()}\n\u001b[31m$msg\u001b[0m")
    }


    preRegisterStructs()
    
    def parseProgram(): List[Program] = {
        val items = scala.collection.mutable.ListBuffer[Program]()
        while !isAtEnd() && peek() != Token.EOF do {
            peek() match {
                case Token.Fn  | Token.Struct | Token.Import => items += Program.PDecl(parseDecl())
                case Token.Impl => {
                    items += parseImpl()
                }
                case _ => items += Program.PCmd(parseCmd())
            }
            while peek() == Token.Semicolon do {
                advance()
            }
        }
        items.toList
    }