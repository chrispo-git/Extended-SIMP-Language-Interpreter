package simp

trait ParserBoolExpr { self: Parser =>
    protected def parseBoolExpr(): Expr = {
        var left = parseExpr()
        while List(Token.And, Token.Or).contains(peek()) do {
            val op = peek()
            advance()
            val right = parseExpr()
            left = op match {
                case Token.And => Expr.BoolLift(BoolExpr.And(makeFromExpr(left), makeFromExpr(right)))
                case Token.Or  => Expr.BoolLift(BoolExpr.Or(makeFromExpr(left), makeFromExpr(right)))
                case _ => throwError("unreachable")
            }
        }
        left
    }




    protected def parseBool(): BoolExpr = {
        var left : BoolExpr = parseAtomicBool()
        while List(Token.And, Token.Or).contains(peek()) do {
            val op = peek()
            advance()
            val right = parseAtomicBool()
            left = op match {
                case Token.And => BoolExpr.And(left, right)
                case Token.Or => BoolExpr.Or(left, right)
                case _ => throwError("unreachable")
            }
        }
        left
    }
    protected def makeFromExpr(expr: Expr): BoolExpr = expr match {
        case Expr.BoolLift(inner) => inner
        case Expr.Bool(b) => BoolExpr.Literal(b)
        case _ => BoolExpr.FromExpr(expr)
    }
    protected def parseBoolOp(tok: Token): Bop = {
        tok match {
            case Token.Gt => Bop.Gt
            case Token.Gte => Bop.Gte
            case Token.Lt => Bop.Lt
            case Token.Lte => Bop.Lte
            case Token.Eq => Bop.Eq
            case Token.Neq => Bop.Neq
            case x => throwError(s"Expected boolean operator, got '${x}'")
        }
    }
    protected def parseAtomicBool(): BoolExpr = {
        peek() match {
            case Token.BoolLit(b) => {
                advance()
                val left = Expr.Bool(b)
                peek() match {
                    case Token.Eq | Token.Neq => {
                        val bop = peek() match {
                            case Token.Eq  => Bop.Eq
                            case Token.Neq => Bop.Neq
                            case x => throwError(s"Expected boolean operator, got '${x}'")
                        }
                        advance()
                        val right = parseExpr()
                        foldCompare(left, bop, right)
                    }
                    case _ => BoolExpr.Literal(b)
                }
            }
            case Token.Not => {
                advance()
                val inside = parseBool()
                BoolExpr.Not(inside)
            }
            case Token.Deref | Token.LiteralInt(_) | Token.LiteralFloat(_) |  Token.StringLit(_)  => {
                val left = parseExpr()
                peek() match {
                    case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq => {
                        val bop = parseBoolOp(peek())
                        advance()
                        val right = parseExpr()
                        foldCompare(left, bop, right)
                    }
                    case _ => makeFromExpr(left)
                }
            }
            case Token.OpenBracket => {
                advance()
                val inside = parseBool()
                expect(Token.CloseBracket)
                inside
            }
            case Token.Variable(_) if peekNext() == Token.OpenBracket || peekNext() == Token.OpenSquare => {
                val expr = parsePostfix(parseAtomicExpr())
                peek() match {
                    case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq =>
                        val bop = parseBoolOp(peek())
                        advance()
                        val right = parseExpr()
                        foldCompare(expr, bop, right)
                    case _ => makeFromExpr(expr)
                }
            }
            case Token.Variable(_) if peekNext() == Token.Dot => {
                val expr = parsePostfix(parseAtomicExpr())
                peek() match {
                    case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq =>
                        val bop = parseBoolOp(peek())
                        advance()
                        val right = parseExpr()
                        foldCompare(expr, bop, right)
                    case _ => makeFromExpr(expr)
                }
            }
            case x => throwError(s"Expected boolean expression, got '$x'")
        }
    }
}