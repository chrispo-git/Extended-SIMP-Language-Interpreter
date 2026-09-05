package simp

trait ParserCmd { self: Parser =>
    protected def parseCmd(): Cmd = {
        val left = peek() match {
            case Token.Fn |  Token.Struct | Token.Locked | Token.Import => return Cmd.Skip
            case _ => parseSingleCmd()
        }
        if peek() == Token.Semicolon then {
            advance()
            if List(Token.EOF, Token.CloseBrace).contains(peek()) then {
                left
            } else {
                val right = parseCmd()
                Cmd.Seq(left, right)
            }
        } else {
            left
        }
    }
    protected def parseFieldIndexAssign(l: String, field: String): Cmd = {
        advance()
        val firstIndex = parseExpr()
        expect(Token.CloseSquare)
        val extraIndices = scala.collection.mutable.ListBuffer[Expr]()
        while peek() == Token.OpenSquare do {
            advance()
            extraIndices += parseExpr()
            expect(Token.CloseSquare)
        }
        peek() match {
            case Token.Assign => {
                advance()
                val value = parseBoolExpr()
                if extraIndices.isEmpty then
                    Cmd.FieldIndexAssign(l, field, firstIndex, value, currentLine())
                else
                    Cmd.FieldIndexAssignNested(l, field, firstIndex :: extraIndices.toList, value, currentLine())
            }
            case Token.PlusEq => { advance(); Cmd.FieldIndexAssign(l, field, firstIndex, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), firstIndex), Op.Add, parseExpr()), currentLine()) }
            case Token.MinusEq => { advance(); Cmd.FieldIndexAssign(l, field, firstIndex, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), firstIndex), Op.Sub, parseExpr()), currentLine()) }
            case Token.MulEq => { advance(); Cmd.FieldIndexAssign(l, field, firstIndex, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), firstIndex), Op.Mul, parseExpr()), currentLine()) }
            case Token.DivEq => { advance(); Cmd.FieldIndexAssign(l, field, firstIndex, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), firstIndex), Op.Div, parseExpr()), currentLine()) }
            case x => throwError(s"Expected assignment, got '$x'")
        }
    }

    protected def parseFieldAssign(l: String, field: String): Cmd = {
        peek() match {
            case Token.Assign => { advance(); Cmd.FieldAssign(l, field, parseBoolExpr(), currentLine()) }
            case Token.PlusEq => { advance(); Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Add, parseExpr()), currentLine()) }
            case Token.MinusEq => { advance(); Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Sub, parseExpr()), currentLine()) }
            case Token.MulEq => { advance(); Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Mul, parseExpr()), currentLine()) }
            case Token.DivEq => { advance(); Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Div, parseExpr()), currentLine()) }
            case x => throwError(s"Expected assignment, got '$x'")
        }
    }

    protected def parseFieldOrIndexAssign(l: String): Cmd = {
        advance()
        peek() match {
            case Token.Variable(field) => {
                advance()
                peek() match {
                    case Token.OpenSquare => parseFieldIndexAssign(l, field)
                    case Token.OpenBracket => {
                        val args = parseArgs()
                        val receiver = if structEnv.exists(l) then Expr.StructTypeRef(l, List()) else Expr.Ref(l)
                        val expr = parsePostfix(Expr.MethodCall(receiver, field, args))
                        Cmd.Assign("_", expr, currentLine())
                    }
                    case _ => parseFieldAssign(l, field)
                }
            }
            case x => throw RuntimeException(s"[Error] Expected field name after '.', got '$x'")
        }
    }

    protected def parseVarAssign(l: String): Cmd = {
        peek() match {
            case Token.OpenSquare => {
                advance()
                val firstIndex = parseExpr()
                expect(Token.CloseSquare)
                val extraIndices = scala.collection.mutable.ListBuffer[Expr]()
                while peek() == Token.OpenSquare do {
                    advance()
                    extraIndices += parseExpr()
                    expect(Token.CloseSquare)
                }
                expect(Token.Assign)
                val value = parseBoolExpr()
                if extraIndices.isEmpty then {
                    Cmd.ArrAssign(l, firstIndex, value, currentLine())
                } else {
                    Cmd.ArrAssignNested(l, firstIndex :: extraIndices.toList, value, currentLine())
                }
            }
            case Token.Assign => { advance(); Cmd.Assign(l, parseBoolExpr(), currentLine()) }
            case Token.PlusEq => { advance(); Cmd.Assign(l, Expr.BinaryOp(Expr.Deref(l), Op.Add, parseExpr()), currentLine()) }
            case Token.MinusEq => { advance(); Cmd.Assign(l, Expr.BinaryOp(Expr.Deref(l), Op.Sub, parseExpr()), currentLine()) }
            case Token.MulEq => { advance(); Cmd.Assign(l, Expr.BinaryOp(Expr.Deref(l), Op.Mul, parseExpr()), currentLine()) }
            case Token.DivEq => { advance(); Cmd.Assign(l, Expr.BinaryOp(Expr.Deref(l), Op.Div, parseExpr()), currentLine()) }
            case x => throwError(s"Expected assignment, got '$x'")
        }
    }

    protected def parseIfBody(): (Cmd, Cmd) = {
        expect(Token.OpenBrace)
        val thenBranch = parseCmd()
        expect(Token.CloseBrace)
        val elseBranch = peek() match {
            case Token.Elif => parseSingleCmd()
            case Token.Else => {
                advance()
                expect(Token.OpenBrace)
                val e = parseCmd()
                expect(Token.CloseBrace)
                e
            }
            case _ => Cmd.Skip
        }
        (thenBranch, elseBranch)
    }

    protected def parseIfCmd(): Cmd = {
        advance()
        val cond = parseBool()
        expect(Token.Then)
        val (thenBranch, elseBranch) = parseIfBody()
        foldIf(cond, thenBranch, elseBranch)
    }

    protected def parseElifCmd(): Cmd = {
        advance()
        val cond = parseBool()
        expect(Token.Then)
        val (thenBranch, elseBranch) = parseIfBody()
        foldIf(cond, thenBranch, elseBranch)
    }

    protected def parseWhileCmd(): Cmd = {
        advance()
        val cond = parseBool()
        expect(Token.Do)
        expect(Token.OpenBrace)
        val body = parseCmd()
        expect(Token.CloseBrace)
        foldWhile(cond, body)
    }

    protected def parseForCmd(): Cmd = {
        advance()
        peek() match {
            case Token.Variable(name) => {
                advance()
                expect(Token.In)
                val iterable = parseExpr()
                expect(Token.OpenBrace)
                val body = parseCmd()
                expect(Token.CloseBrace)
                Cmd.For(name, iterable, body, currentLine())
            }
            case x => throwError(s"Expected loop variable, got '$x'")
        }
    }

    protected def parseOpenBracketCmd(): Cmd = {
        val savedPos = pos
        advance()
        try {
            val expr = parseExpr()
            peek() match {
                case Token.Comma => {
                    advance()
                    val right = parseExpr()
                    expect(Token.CloseBracket)
                    Cmd.Assign("_", Expr.Pair(expr, right), currentLine())
                }
                case Token.CloseBracket => {
                    advance()
                    Cmd.Assign("_", expr, currentLine())
                }
                case _ => {
                    pos = savedPos
                    advance()
                    val cmd = parseCmd()
                    expect(Token.CloseBracket)
                    cmd
                }
            }
        } catch {
            case _: Exception => {
                pos = savedPos
                advance()
                val cmd = parseCmd()
                expect(Token.CloseBracket)
                cmd
            }
        }
    }

    protected def parseReturnCmd(): Cmd = {
        advance()
        peek() match {
            case Token.Semicolon | Token.CloseBrace | Token.EOF => Cmd.Return(None)
            case _ => Cmd.Return(Some(parseBoolExpr()), currentLine())
        }
    }
    protected def parseConstAssign(): Cmd = {
        peek() match {
            case Token.Variable(l) => {
                advance();
                expect(Token.Assign)
                Cmd.ConstAssign(l, parseBoolExpr(), currentLine())
            }
            case x => throwError(s"Expected variable name after const, got '$x'")
        }
    }
    protected def parseErrorTypeName(): String = peek() match {
        case Token.Variable(name) if SimpError.KnownTypes.contains(name) => { advance(); name }
        case Token.Variable(name) => throwError(
            s"Unknown error type '$name'. Expected one of: ${SimpError.KnownTypes.toList.sorted.mkString(", ")}"
        )
        case x => throwError(s"Expected an error type name, got '$x'")
    }
    protected def parseCatchClause(): CatchClause = {
        expect(Token.Catch)
        val errorType = parseErrorTypeName()
        val bindVar = if peek() == Token.As then {
            advance()
            peek() match {
                case Token.Variable(name) => { advance(); Some(name) }
                case x => throwError(s"Expected variable name after 'as', got '$x'")
            }
        } else None
        expect(Token.OpenBrace)
        val body = parseScope()
        CatchClause(errorType, bindVar, body)
    }
    protected def parseTryCmd(): Cmd = {
        advance()
        expect(Token.OpenBrace)
        val tryBody = parseScope()
        val line = currentLine()
        val catches = scala.collection.mutable.ListBuffer[CatchClause]()
        catches += parseCatchClause()
        while peek() == Token.Catch do {
            catches += parseCatchClause()
        }
        Cmd.Try(tryBody, catches.toList, line)
    }
    protected def parseThrowCmd(): Cmd = {
        advance()
        val errorType = peek() match {
            case Token.Variable(name) if SimpError.KnownTypes.contains(name) && peekNext() == Token.OpenBracket => {
                advance()
                Some(name)
            }
            case _ => None
        }
        errorType match {
            case Some(t) => {
                expect(Token.OpenBracket)
                val expr = parseBoolExpr()
                expect(Token.CloseBracket)
                Cmd.Throw(Some(t), expr, currentLine())
            }
            case None => Cmd.Throw(None, parseBoolExpr(), currentLine())
        }
    }
    protected def parseScope(): Cmd = {
        if peek() == Token.CloseBrace then {
            advance();
            Cmd.Scope(Cmd.Skip)
        } else {
            val body = parseCmd()
            expect(Token.CloseBrace)
            Cmd.Scope(body)
        }
    }
    protected def parseSingleCmd(): Cmd = {
        peek() match {
            case Token.Skip     => { advance(); Cmd.Skip }
            case Token.Continue => { advance(); Cmd.Continue }
            case Token.Break    => { advance(); Cmd.Break }
            case Token.Const => {advance(); parseConstAssign() }
            case Token.Variable(l) if peekNext() == Token.Dot => { advance(); parseFieldOrIndexAssign(l) }
            case Token.Variable(l) if peekNext() == Token.Colon => {
                advance()
                advance()
                val t = parseType()
                Cmd.TypeDecl(l, t, currentLine())
            }
            case Token.If       => parseIfCmd()
            case Token.Elif     => parseElifCmd()
            case Token.While    => parseWhileCmd()
            case Token.For      => parseForCmd()
            case Token.Try      => parseTryCmd()
            case Token.Throw    => parseThrowCmd()
            case Token.Variable(l) => { advance(); parseVarAssign(l) }
            case Token.Print    => { advance(); Cmd.Print(parseExpr(), currentLine()) }
            case Token.OpenBracket => parseOpenBracketCmd()
            case Token.Return   => parseReturnCmd()
            case Token.OpenBrace => {advance(); parseScope()}
            case x => throwError(s"Unexpected '$x'")
        }
    }
}