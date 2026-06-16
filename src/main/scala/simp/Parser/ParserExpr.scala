package simp

trait ParserExpr { self: Parser =>
    protected def parsePattern(): Pattern = peek() match {
        case Token.Variable("_") => { advance(); Pattern.PWild }
        case Token.LiteralInt(_) | Token.LiteralFloat(_) | Token.StringLit(_) | Token.BoolLit(_) | Token.Null =>
            Pattern.PLit(parseAtomicExpr())
        case Token.OpenBracket => {
            advance()
            val fst = parsePattern()
            expect(Token.Comma)
            val snd = parsePattern()
            expect(Token.CloseBracket)
            Pattern.PPair(fst, snd)
        }
        case Token.Variable(name) if peekNext() == Token.OpenBrace => {
            advance()
            advance()
            val fields = parsePatternFields()
            expect(Token.CloseBrace)
            Pattern.PStruct(name, fields)
        }
        case Token.Variable(name) => { advance(); Pattern.PVar(name) }
        case x => throwError(s"Expected pattern, got '$x'")
    }
    protected def parsePostfix(expr: Expr): Expr = {
        peek() match {
            case Token.OpenSquare => {
                advance()
                val index = parseExpr()
                expect(Token.CloseSquare)
                parsePostfix(Expr.ArrIndex(expr, index))
            }
            case Token.Dot => {
                advance()
                val name = peek() match {
                    case Token.Variable(n) => { advance(); n }
                    case x => throwError(s"Expected field name after '.', got '$x'")
                }
                if peek() == Token.OpenBracket then {
                    val args = parseArgs()
                    parsePostfix(Expr.MethodCall(expr, name, args))
                } else {
                    parsePostfix(Expr.FieldAccess(expr, name))
                }
            }
            case _ => expr
        }
    }
    protected def parseMatch(): Expr = {
        expect(Token.Match)
        val expr = parseExpr()
        expect(Token.OpenBrace)
        val arms = scala.collection.mutable.ListBuffer[MatchArm]()
        while peek() != Token.CloseBrace do {
            expect(Token.Case)
            val pattern = parsePattern()
            val guard = if peek() == Token.If then {
                advance()
                Some(parseExpr())
            } else None
            expect(Token.FatArrow)
            val body = parseExpr()
            expect(Token.Semicolon)
            arms += MatchArm(pattern, guard, body)
        }
        expect(Token.CloseBrace)
        Expr.Match(expr, arms.toList)
    }
    protected def parseStructLiteralFields(): List[(String, Expr)] = {
        val fields = scala.collection.mutable.ListBuffer[(String, Expr)]()
        while peek() != Token.CloseBrace do {
            peek() match {
                case Token.Variable(name) => {
                    advance()
                    expect(Token.Colon)
                    val value = parseExpr()
                    fields += ((name, value))
                    if peek() == Token.Comma then advance()
                }
                case x => throwError(s"Expected field name, got '$x'")
            }
        }
        fields.toList
    }

    protected def parsePatternFields(): List[(String, Pattern)] = {
        val fields = scala.collection.mutable.ListBuffer[(String, Pattern)]()
        while peek() != Token.CloseBrace do {
            peek() match {
                case Token.Variable(name) => {
                    advance()
                    expect(Token.Colon)
                    val pattern = parsePattern()
                    fields += ((name, pattern))
                    if peek() == Token.Comma then advance()
                }
                case x => throwError(s"Expected field name, got '$x'")
            }
        }
        fields.toList
    }
    protected def parseExpr(): Expr = {
        var left = parseAddSub()
        while List(Token.BitAnd, Token.BitOr, Token.BitXor, Token.BitLeft, Token.BitRight, Token.BitRightFill).contains(peek()) do {
            val op: Op  = peek() match {
                case Token.BitAnd => Op.BitAnd
                case Token.BitOr => Op.BitOr
                case Token.BitXor => Op.BitXor
                case Token.BitLeft => Op.BitLeft
                case Token.BitRight => Op.BitRight
                case Token.BitRightFill => Op.BitRightFill
                case _ => throwError("unreachable")
            }
            advance()
            val right = parseAddSub()
            left = foldBinary(left, op, right)
        }
        peek() match {
            case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq => {
                val bop = parseBoolOp(peek())
                advance()
                val right = parseExpr()
                Expr.BoolLift(foldCompare(left, bop, right))
            }
            case _ => left
        }
    }
    protected def parseAddSub(): Expr = {
        var left = parseTerm()
        while List(Token.Add, Token.Sub).contains(peek()) do {
            val op: Op = peek() match {
                case Token.Add => Op.Add
                case Token.Sub => Op.Sub
                case _ => throwError("unreachable")
            }
            advance()
            val right = parseTerm()
            left = foldBinary(left, op, right)
        }
        left
    }
    protected def parseTerm(): Expr = {
        var left = parsePostfix(parseAtomicExpr())
        while List(Token.Mul, Token.Div, Token.Mod).contains(peek()) do {
            val op: Op = peek() match {
                case Token.Mul => Op.Mul
                case Token.Div => Op.Div
                case Token.Mod => Op.Mod
                case _ => throwError("unreachable")
            }
            advance()
            val right = parsePostfix(parseAtomicExpr())
            left = foldBinary(left, op, right)
        }
        left
    }
    protected def parseArrLiteral(): Expr = {
        advance()
        if peek() == Token.CloseSquare then {
            advance()
            Expr.ArrLiteral(List())
        } else {
            val elements = scala.collection.mutable.ListBuffer[Expr]()
            elements += parseExpr()
            while peek() == Token.Comma do {
                advance()
                elements += parseExpr()
            }
            expect(Token.CloseSquare)
            Expr.ArrLiteral(elements.toList)
        }
    }
    protected def parseNamespace(namespace: String): Expr = {
        advance()
        advance()
        peek() match {
            case Token.Variable(name) if peekNext() == Token.OpenBrace && structEnv.exists(s"$namespace::$name") => {
                advance()
                advance()
                val fields = parseStructLiteralFields()
                expect(Token.CloseBrace)
                Expr.StructLiteral(s"$namespace::$name", fields)
            }
            case Token.Variable(name) if peekNext() == Token.OpenBracket => {
                advance()
                val args = parseArgs()
                Expr.FnCall(s"$namespace::$name", args)
            }
            case x => throwError(s"Expected name after '::', got '$x'")
        }
    }
    protected def parseAtomicExpr(): Expr = {
        peek() match {
            case Token.BitComplement => {
                advance()
                val left = foldUnary(parseAtomicExpr(), Op.BitComplement)
                left
            }
            case Token.Match => parseMatch()
            case Token.LiteralInt(n) => {
                advance()
                val left = Expr.Num(n)
                left
            }
            case Token.LiteralFloat(n) => {
                advance()
                val left = Expr.Flt(n)
                left
            }
            case Token.StringLit(s) => {
                advance()
                Expr.Str(s)
            }
            case Token.OpenSquare => parseArrLiteral()
            case Token.TypeInt    => { advance(); Expr.TypeLiteral(SimpType.TypeInt) }
            case Token.TypeString => { advance(); Expr.TypeLiteral(SimpType.TypeString) }
            case Token.TypeBool   => { advance(); Expr.TypeLiteral(SimpType.TypeBool) }
            case Token.TypeFloat  => { advance(); Expr.TypeLiteral(SimpType.TypeFloat) }

            case Token.Variable(namespace) if peekNext() == Token.DoubleColon => parseNamespace(namespace)
            case Token.Variable(name) if peekNext() == Token.OpenBrace && structEnv.exists(name) => {
                advance()
                advance()
                val fields = parseStructLiteralFields()
                expect(Token.CloseBrace)
                Expr.StructLiteral(name, fields)
            }
            case Token.Null => { advance(); Expr.Null }
            case Token.Deref => {
                advance()
                val left = peek() match {
                    case Token.Variable(l) => {
                        advance()
                        Expr.Deref(l)
                    }
                    case x => throwError(s"Expected variable after '!', got '$x'")
                }
                left
            }
            case Token.Not => {
                Expr.BoolLift(parseBool())
            }
            
            case Token.BoolLit(b) => {
                advance()
                Expr.Bool(b)
            }
            case Token.Variable(name) if peekNext() == Token.OpenBracket => {
                advance()
                val args = parseArgs()
                Expr.FnCall(name, args)
            }
            case Token.Variable(name) => {
                advance()
                Expr.Ref(name)
            }
            case Token.OpenBrace => {
                advance()
                val cmds = scala.collection.mutable.ListBuffer[Cmd]()
                while peek() != Token.CloseBrace do {
                    val savedPos = pos
                    val cmd = parseSingleCmd()
                    if peek() == Token.Semicolon then {
                        advance()
                        cmds += cmd
                    } else {
                        pos = savedPos
                        val result = parseExpr()
                        expect(Token.CloseBrace)
                        return Expr.Block(cmds.toList, result)
                    }
                }
                throwError("Block expression must end with a value expression")
            }
            case Token.OpenBracket => {
                advance()
                val left = parseExpr()
                peek() match {
                    case Token.Comma => {
                        advance()
                        val right = parseExpr()
                        expect(Token.CloseBracket)
                        Expr.Pair(left, right)
                    }
                    case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq => {
                        expect(Token.CloseBracket);
                        val bop = parseBoolOp(peek())
                        advance()
                        val right = parseExpr()
                        Expr.BoolLift(foldCompare(left, bop, right))
                    }
                    case _ => {expect(Token.CloseBracket); left}
                }
            }
            case x => throwError(s"Unexpected '$x'")
        }
    }
}