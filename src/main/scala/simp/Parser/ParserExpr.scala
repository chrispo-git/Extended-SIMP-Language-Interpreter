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
    // Parses a `<Type, Type, ...>` type-argument list at an expression site
    // (a generic struct literal or static method call), e.g. the `<Int>` in
    // `Stack<Int>{...}` or `Stack<Int>.new()`. Assumes the leading `<` has not
    // yet been consumed.
    protected def parseTypeArgList(): List[SimpType] = {
        expect(Token.Lt)
        val args = scala.collection.mutable.ListBuffer[SimpType]()
        args += parseType()
        while peek() == Token.Comma do {
            advance()
            args += parseType()
        }
        expect(Token.Gt)
        args.toList
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
    // The expression grammar excluding `&&`/`||` (those are layered on top by
    // `parseExpr`, below). Kept separate so a comparison's right-hand side
    // (recursed into from within this same function) stays restricted to this
    // tighter grammar - `a < b && c` must parse as `(a < b) && c`, not
    // `a < (b && c)`, i.e. `&&`/`||` bind looser than comparisons and must not
    // be swallowed into a comparison's own right operand.
    protected def parseExprCore(): Expr = {
        var left = parseAddSub()
        while List(Token.BitAnd, Token.BitOr, Token.BitXor, Token.BitLeft, Token.BitRight, Token.BitRightFill).contains(peek()) do {
            val op: Op  = (peek(): @unchecked) match {
                case Token.BitAnd => Op.BitAnd
                case Token.BitOr => Op.BitOr
                case Token.BitXor => Op.BitXor
                case Token.BitLeft => Op.BitLeft
                case Token.BitRight => Op.BitRight
                case Token.BitRightFill => Op.BitRightFill
            }
            advance()
            val right = parseAddSub()
            left = foldBinary(left, op, right)
        }
        peek() match {
            case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq => {
                val bop = parseBoolOp(peek())
                advance()
                val right = parseExprCore()
                Expr.BoolLift(foldCompare(left, bop, right))
            }
            case _ => left
        }
    }
    // The full expression grammar, including `&&`/`||` at the loosest
    // precedence - unlike an earlier revision of this parser, `&&`/`||` are
    // ordinary expression operators usable anywhere an expression is expected
    // (a function-call argument, an array-literal element, a struct-literal
    // field value, etc.), not just at a handful of "top level" call sites.
    protected def parseExpr(): Expr = {
        var left = parseExprCore()
        while List(Token.And, Token.Or).contains(peek()) do {
            val op = peek()
            advance()
            val right = parseExprCore()
            left = (op: @unchecked) match {
                case Token.And => Expr.BoolLift(BoolExpr.And(makeFromExpr(left), makeFromExpr(right)))
                case Token.Or  => Expr.BoolLift(BoolExpr.Or(makeFromExpr(left), makeFromExpr(right)))
            }
        }
        left
    }
    protected def parseAddSub(): Expr = {
        var left = parseTerm()
        while List(Token.Add, Token.Sub).contains(peek()) do {
            val op: Op = (peek(): @unchecked) match {
                case Token.Add => Op.Add
                case Token.Sub => Op.Sub
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
            val op: Op = (peek(): @unchecked) match {
                case Token.Mul => Op.Mul
                case Token.Div => Op.Div
                case Token.Mod => Op.Mod
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
            // Unlike the plain (non-namespaced) struct-literal/type-ref cases in
            // parseAtomicExpr, these don't (and can't) guard on
            // `structEnv.exists(...)`: an imported file's structs are only
            // registered when its `import` statement is *evaluated*, which
            // happens strictly after the *entire* importing file has already
            // been parsed - so the existence check would always fail here,
            // even for a perfectly valid `alias::Struct{...}` right below a
            // real `import ... as alias;`. That's fine: there's no other valid
            // parse for `namespace::Name` followed by `{`/`<`/`.` in this
            // grammar (unlike the bare, non-namespaced case, a qualified name
            // with nothing following is already a parse error below, so there's
            // no ambiguity to guard against by checking existence first) -
            // any actual mistake (wrong alias, wrong struct name) still surfaces
            // at eval time via the normal "unknown struct type" error.
            case Token.Variable(name) if peekNext() == Token.OpenBrace => {
                advance()
                advance()
                val fields = parseStructLiteralFields()
                expect(Token.CloseBrace)
                Expr.StructLiteral(s"$namespace::$name", fields, List())
            }
            case Token.Variable(name) if peekNext() == Token.Lt => {
                advance()
                val typeArgs = parseTypeArgList()
                peek() match {
                    case Token.OpenBrace => {
                        advance()
                        val fields = parseStructLiteralFields()
                        expect(Token.CloseBrace)
                        Expr.StructLiteral(s"$namespace::$name", fields, typeArgs)
                    }
                    case Token.Dot => Expr.StructTypeRef(s"$namespace::$name", typeArgs)
                    case x => throwError(s"Expected '{' or '.' after type arguments, got '$x'")
                }
            }
            case Token.Variable(name) if peekNext() == Token.Dot => {
                advance()
                Expr.StructTypeRef(s"$namespace::$name", List())
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
                Expr.StructLiteral(name, fields, List())
            }
            case Token.Variable(name) if peekNext() == Token.Lt && structEnv.exists(name) => {
                advance()
                val typeArgs = parseTypeArgList()
                peek() match {
                    case Token.OpenBrace => {
                        advance()
                        val fields = parseStructLiteralFields()
                        expect(Token.CloseBrace)
                        Expr.StructLiteral(name, fields, typeArgs)
                    }
                    case Token.Dot => Expr.StructTypeRef(name, typeArgs)
                    case x => throwError(s"Expected '{' or '.' after type arguments, got '$x'")
                }
            }
            case Token.Variable(name) if peekNext() == Token.Dot && structEnv.exists(name) => {
                advance()
                Expr.StructTypeRef(name, List())
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
                    val cmdAttempt = try Some(parseSingleCmd()) catch { case _: RuntimeException => None }
                    cmdAttempt match {
                        case Some(cmd) if peek() == Token.Semicolon => {
                            advance()
                            cmds += cmd
                        }
                        case _ => {
                            pos = savedPos
                            val result = parseExpr()
                            expect(Token.CloseBrace)
                            return Expr.Block(cmds.toList, result)
                        }
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
                    case _ => {expect(Token.CloseBracket); left}
                }
            }
            case x => throwError(s"Unexpected '$x'")
        }
    }
}