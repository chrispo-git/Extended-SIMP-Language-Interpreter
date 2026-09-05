package simp

trait ParserBoolExpr { self: Parser =>
    // `parseExpr` (ParserExpr.scala) now handles `&&`/`||` directly as part of
    // the ordinary expression grammar, so this is just the old, now-narrower
    // entry point kept as an alias for its existing call sites.
    protected def parseBoolExpr(): Expr = parseExpr()




    protected def parseBool(): BoolExpr = {
        var left : BoolExpr = parseAtomicBool()
        while List(Token.And, Token.Or).contains(peek()) do {
            val op = peek()
            advance()
            val right = parseAtomicBool()
            left = (op: @unchecked) match {
                case Token.And => BoolExpr.And(left, right)
                case Token.Or => BoolExpr.Or(left, right)
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
                        val bop = parseBoolOp(peek())
                        advance()
                        // A comparison's right-hand side must not swallow a
                        // trailing `&&`/`||` into itself (`parseExprCore`, not
                        // the `&&`/`||`-including `parseExpr`) - otherwise
                        // `a == b && c` would wrongly parse as `a == (b && c)`
                        // instead of `(a == b) && c`.
                        val right = parseExprCore()
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
                makeFromExpr(left)
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
                        val right = parseExprCore()
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
                        val right = parseExprCore()
                        foldCompare(expr, bop, right)
                    case _ => makeFromExpr(expr)
                }
            }
            case x => throwError(s"Expected boolean expression, got '$x'")
        }
    }
}