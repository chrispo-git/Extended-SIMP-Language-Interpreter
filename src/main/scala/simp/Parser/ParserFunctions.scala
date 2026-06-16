package simp

trait ParserFunctions { self: Parser =>
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