package simp

trait ParserFunctions { self: Parser =>
    protected def parseParams(): List[(String, SimpType)] = {
        expect(Token.OpenBracket)
        if peek() == Token.CloseBracket then {
            advance()
            List()
        } else {
            val params = scala.collection.mutable.ListBuffer[(String, SimpType)]()
            params += parseParam()
            while peek() == Token.Comma do {
                advance()
                params += parseParam()
            }
            expect(Token.CloseBracket)
            params.toList
        }
    }
    protected def parseParam(): (String, SimpType) = {
        peek() match {
            case Token.Variable(name) => {
                advance()
                expect(Token.Colon)
                val t = parseType()
                (name, t)
            }
            case x => throwError(s"Expected parameter name, got '$x'")
        }
    }
    protected def parseArgs(): List[Expr] = {
        expect(Token.OpenBracket)
        if peek() == Token.CloseBracket then {
            advance()
            List()
        } else {
            val args = scala.collection.mutable.ListBuffer[Expr]()
            args += parseExpr()
            while peek() == Token.Comma do {
                advance()
                args += parseExpr()
            }
            expect(Token.CloseBracket)
            args.toList
        }
    }
}