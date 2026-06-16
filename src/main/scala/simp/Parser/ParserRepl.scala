package simp

trait ParserRepl { self: Parser =>
    def parseRepl(): List[Program] = {
        val items = scala.collection.mutable.ListBuffer[Program]()

        while !isAtEnd() && peek() != Token.EOF do {
            val item = peek() match {
                case Token.Fn |  Token.Struct | Token.Import  => Program.PDecl(parseDecl())
                case Token.Impl => {
                    parseImpl()
                }
                case Token.BoolLit(_) | Token.Not => Program.PBool(parseBool())
                case  Token.LiteralFloat(_) |Token.LiteralInt(_) | Token.Deref | Token.BitComplement =>
                    val expr = parseExpr()
                    peek() match {
                        case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq =>
                            val bop = parseBoolOp(peek())
                            advance()
                            val right = parseExpr()
                            Program.PBool(foldCompare(expr, bop, right))
                        case _ => Program.PExpr(expr)
                    }
                case Token.Variable(_) if peekNext() == Token.OpenBracket => Program.PExpr(parseExpr())
                case Token.Variable(_) if peekNext() == Token.OpenSquare => {
                    val savedPos = pos
                    advance()
                    advance()
                    var depth = 1
                    while depth > 0 && !isAtEnd() do {
                        peek() match {
                            case Token.OpenSquare => {depth += 1; advance()}
                            case Token.CloseSquare => {depth -= 1; advance()}
                            case _ => advance()
                        }
                    }
                    val isAssignment = peek() == Token.Assign
                    pos = savedPos
                    isAssignment match {
                        case true => Program.PCmd(parseCmd())
                        case false => Program.PExpr(parseAtomicExpr())
                    }
                }
                case _ => Program.PCmd(parseCmd())
            }
            items += item
            while peek() == Token.Semicolon do advance()
        }
        items.toList
    }
}