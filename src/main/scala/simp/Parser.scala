package simp


class Parser(tokens: List[Token], structEnv : StructEnv, lines: List[Int]):
    private var pos: Int = 0

    private def isAtEnd(): Boolean = pos >= tokens.length

    private def peek(): Token =  if isAtEnd() then Token.EOF else tokens(pos)

    private def peekNext(): Token = if pos+1 >= tokens.length then Token.EOF else tokens(pos+1)

    private def peekN(n: Int): Token = if pos+n >= tokens.length then Token.EOF else tokens(pos+n)

    private def advance(): Token = if isAtEnd() then Token.EOF else {val t = tokens(pos);pos+=1;t}

    private def advanceTo(n: Int): Token = if isAtEnd() then Token.EOF else {val t = tokens(pos);pos+=n;t} 

    private def expect(expected: Token): Unit = {
        if peek() != expected then throwError(s"Expected '$expected', got '${peek()}'")
        else advance()
    }

    private def currentLine(): Int = if pos < lines.length then lines(pos) else -1
    private def throwError(msg: String): Nothing = throw RuntimeException(s"on line ${currentLine()}: $msg")


    private def preRegisterStructs(): Unit = {
        var i = 0
        while i < tokens.length do {
            tokens(i) match {
                case Token.Struct =>
                    tokens(i + 1) match {
                        case Token.Variable(name) => structEnv.preRegister(name)
                        case _ =>
                    }
                case _ =>
            }
            i += 1
        }
    }
    preRegisterStructs()
    
    def parseProgram(): List[Program] = {
        val items = scala.collection.mutable.ListBuffer[Program]()
        while !isAtEnd() && peek() != Token.EOF do {
            peek() match {
                case Token.Fn  | Token.Struct | Token.Import => items += Program.PDecl(parseDecl())
                case _ => items += Program.PCmd(parseCmd())
            }
            while peek() == Token.Semicolon do {
                advance()
            }
        }
        items.toList
    }

    def parseRepl(): List[Program] = {
        val items = scala.collection.mutable.ListBuffer[Program]()

        while !isAtEnd() && peek() != Token.EOF do {
            val item = peek() match {
                case Token.Fn |  Token.Struct | Token.Import => Program.PDecl(parseDecl())
                case Token.BoolLit(_) | Token.Not => Program.PBool(parseBool())
                case  Token.LiteralFloat(_) |Token.LiteralInt(_) | Token.Deref | Token.BitComplement =>
                    val expr = parseExpr()
                    peek() match {
                        case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq =>
                            val bop = peek() match {
                                case Token.Gt  => Bop.Gt
                                case Token.Lt  => Bop.Lt
                                case Token.Gte => Bop.Gte
                                case Token.Lte => Bop.Lte
                                case Token.Eq  => Bop.Eq
                                case Token.Neq => Bop.Neq
                                case _ => throwError("unreachable")
                            }
                            advance()
                            val right = parseExpr()
                            Program.PBool(BoolExpr.Compare(expr, bop, right))
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
    private def parseCmd(): Cmd = {
        val left = peek() match {
            case Token.Fn |  Token.Struct | Token.Import => return Cmd.Skip
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

    private def parseSingleCmd(): Cmd = {
        val current_token = peek()
        current_token match {
            case Token.Skip => {
                advance(); 
                Cmd.Skip
            }
            case Token.Continue => {
                advance(); 
                Cmd.Continue
            }
            case Token.Break => {
                advance(); 
                Cmd.Break
            }
            case Token.Variable(l) if peekNext() == Token.Dot => {
                advance() 
                advance() 
                peek() match {
                    case Token.Variable(field) => {
                        advance() 
                        peek() match {
                            case Token.OpenSquare => {
                                advance()
                                val index = parseExpr()
                                expect(Token.CloseSquare)
                                peek() match {
                                    case Token.Assign => {
                                        advance()
                                        Cmd.FieldIndexAssign(l, field, index, parseExpr())
                                    }
                                    case Token.PlusEq => {
                                        advance()
                                        val value = parseExpr()
                                        Cmd.FieldIndexAssign(l, field, index, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), index), Op.Add, value))
                                    }
                                    case Token.MinusEq => {
                                        advance()
                                        val value = parseExpr()
                                        Cmd.FieldIndexAssign(l, field, index, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), index), Op.Sub, value))
                                    }
                                    case Token.MulEq => {
                                        advance()
                                        val value = parseExpr()
                                        Cmd.FieldIndexAssign(l, field, index, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), index), Op.Mul, value))
                                    }
                                    case Token.DivEq => {
                                        advance()
                                        val value = parseExpr()
                                        Cmd.FieldIndexAssign(l, field, index, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), index), Op.Div, value))
                                    }
                                    case x => throw RuntimeException(s"Expected assignment, got '$x'")
                                }
                            }
                            case Token.Assign => {
                                advance()
                                Cmd.FieldAssign(l, field, parseExpr())
                            }
                            case Token.PlusEq => {
                                advance()
                                val value = parseExpr()
                                Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Add, value))
                            }
                            case Token.MinusEq => {
                                advance()
                                val value = parseExpr()
                                Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Sub, value))
                            }
                            case Token.MulEq => {
                                advance()
                                val value = parseExpr()
                                Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Mul, value))
                            }
                            case Token.DivEq => {
                                advance()
                                val value = parseExpr()
                                Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Div, value))
                            }
                            case x => throw RuntimeException(s"Expected assignment, got '$x'")
                        }
                    }
                    case x => throw RuntimeException(s"Expected field name after '.', got '$x'")
                }
            }
            case Token.If => {
                advance()
                val cond = parseBool()
                expect(Token.Then)
                expect(Token.OpenBrace)
                val thenBranch = parseCmd()
                expect(Token.CloseBrace)
                val elseBranch = peek() match {
                    case Token.Elif => {
                        parseSingleCmd()
                    }
                    case Token.Else => {
                        advance()
                        expect(Token.OpenBrace)
                        val e = parseCmd()
                        expect(Token.CloseBrace)
                        e
                    }
                    case _ => Cmd.Skip
                }
                Cmd.If(cond, thenBranch, elseBranch)
            }
            case Token.Elif => {
                advance()
                val cond = parseBool()
                expect(Token.Then)
                expect(Token.OpenBrace)
                val thenBranch = parseCmd()
                expect(Token.CloseBrace)
                val elseBranch = peek() match {
                    case Token.Elif => {
                        parseSingleCmd()
                    }
                    case Token.Else => {
                        advance()
                        expect(Token.OpenBrace)
                        val e = parseCmd()
                        expect(Token.CloseBrace)
                        e
                    }
                    case _ => Cmd.Skip
                }
                Cmd.If(cond, thenBranch, elseBranch)
            }
            case Token.While => {
                advance()
                val cond = parseBool()
                expect(Token.Do)
                expect(Token.OpenBrace)
                val body = parseCmd()
                expect(Token.CloseBrace)
                Cmd.While(cond, body)
            }
            case Token.For => {
                advance()
                peek() match {
                    case Token.Variable(name) => {
                        advance()
                        expect(Token.In)
                        val iterable = parseExpr()
                        expect(Token.OpenBrace)
                        val body = parseCmd()
                        expect(Token.CloseBrace)
                        Cmd.For(name, iterable, body)
                    }
                    case x => throwError(s"Expected loop variable, got '$x'")
                }
            }
            case Token.Variable(l) => {
                val loc = l
                advance()
                peek() match {
                    case Token.OpenSquare => {
                        advance()
                        val index = parseExpr()
                        expect(Token.CloseSquare)
                        expect(Token.Assign)
                        val value = parseExpr()
                        Cmd.ArrAssign(l, index, value)
                    }
                    case Token.Assign => {
                        advance()
                        val value = parseExpr()
                        Cmd.Assign(loc, value)
                    }
                    case Token.PlusEq => {
                        advance()
                        val value = parseExpr()
                        Cmd.Assign(loc, Expr.BinaryOp(Expr.Deref(loc), Op.Add, value))
                    }
                    case Token.MinusEq => {
                        advance()
                        val value = parseExpr()
                        Cmd.Assign(loc, Expr.BinaryOp(Expr.Deref(loc), Op.Sub, value))
                    }
                    case Token.MulEq => {
                        advance()
                        val value = parseExpr()
                        Cmd.Assign(loc, Expr.BinaryOp(Expr.Deref(loc), Op.Mul, value))
                    }
                    case Token.DivEq => {
                        advance()
                        val value = parseExpr()
                        Cmd.Assign(loc, Expr.BinaryOp(Expr.Deref(loc), Op.Div, value))
                    }
                    case x => throwError(s"Expected assignment, got '$x'")
                }
                
            }
            case Token.Print => {
                advance()
                Cmd.Print(parseExpr())
            }
            case Token.OpenBracket => {
                advance()
                val cmd = parseCmd()
                expect(Token.CloseBracket)
                cmd
            }
            case Token.Return => {
                advance()
                peek() match {
                    case Token.Semicolon | Token.CloseBrace | Token.EOF => Cmd.Return(None)
                    case _ => Cmd.Return(Some(parseExpr()))
                }
            }
            
            case x => throwError(s"Unexpected '$x'")
        }
    }
    private def parseArgs(): List[Expr] = {
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
    private def parseParam(): (String, SimpType) = {
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

    private def parseType(): SimpType = peek() match {
        case Token.OpenBracket => {
            advance()
            val fst = parseType()
            expect(Token.Comma)
            val snd = parseType()
            expect(Token.CloseBracket)
            var t: SimpType = SimpType.TypePair(fst, snd)
            while peek() == Token.OpenSquare do {
                expect(Token.OpenSquare)
                expect(Token.CloseSquare)
                t = SimpType.TypeArr(t)
            }
            t
        }
        case Token.TypeInt  => { 
            advance(); 
            var t: SimpType = SimpType.TypeInt
            while peek() == Token.OpenSquare do {
                expect(Token.OpenSquare)
                expect(Token.CloseSquare)
                t = SimpType.TypeArr(t)
            }
            t
        }
        case Token.TypeFloat  => { 
            advance(); 
            var t: SimpType = SimpType.TypeFloat
            while peek() == Token.OpenSquare do {
                expect(Token.OpenSquare)
                expect(Token.CloseSquare)
                t = SimpType.TypeArr(t)
            }
            t
        }
        case Token.TypeString  => { 
            advance(); 
            var t: SimpType = SimpType.TypeString
            while peek() == Token.OpenSquare do {
                expect(Token.OpenSquare)
                expect(Token.CloseSquare)
                t = SimpType.TypeArr(t)
            }
            t
        }
        case Token.TypeBool  => { 
            advance(); 
            var t: SimpType = SimpType.TypeBool
            while peek() == Token.OpenSquare do {
                expect(Token.OpenSquare)
                expect(Token.CloseSquare)
                t = SimpType.TypeArr(t)
            }
            t
        }
        case Token.TypeMap => {
            advance()
            expect(Token.OpenBracket)
            val keyType = parseType()
            expect(Token.Comma)
            val valueType = parseType()
            expect(Token.CloseBracket)
            SimpType.TypeMap(keyType, valueType)
        }
        case Token.TypeNull => {advance(); SimpType.TypeNull}
        case Token.Ref => {
            advance();
            val inner = parseType()
            SimpType.TypeRef(inner)
        }
        case Token.Variable(name) => { 
            advance(); 
            var t: SimpType = SimpType.TypeStruct(name) 
            while peek() == Token.OpenSquare do {
                expect(Token.OpenSquare)
                expect(Token.CloseSquare)
                t = SimpType.TypeArr(t)
            }
            t
        }
        case x => throwError(s"Expected type, got '$x'")
    }
    private def parseParams(): List[(String, SimpType)] = {
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
    private def parseExpr(): Expr = {
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
            left = Expr.BinaryOp(left, op, right)
        }
        peek() match {
            case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq => {
                val bop = peek() match {
                    case Token.Gt  => Bop.Gt
                    case Token.Lt  => Bop.Lt
                    case Token.Gte => Bop.Gte
                    case Token.Lte => Bop.Lte
                    case Token.Eq  => Bop.Eq
                    case Token.Neq => Bop.Neq
                    case _ => throw RuntimeException("unreachable")
                }
                advance()
                val right = parseExpr()
                Expr.BoolLift(BoolExpr.Compare(left, bop, right))
            }
            case _ => left
        }
        left
    }
    private def parseAddSub(): Expr = {
        var left = parseTerm()
        while List(Token.Add, Token.Sub).contains(peek()) do {
            val op: Op = peek() match {
                case Token.Add => Op.Add
                case Token.Sub => Op.Sub
                case _ => throwError("unreachable")
            }
            advance()
            val right = parseTerm()
            left = Expr.BinaryOp(left, op, right)
        }
        left
    }
    private def parseTerm(): Expr = {
        var left = parsePostfix(parseAtomicExpr())
        while List(Token.Mul, Token.Div, Token.Mod).contains(peek()) do {
            val op: Op = peek() match {
                case Token.Mul => Op.Mul
                case Token.Div => Op.Div
                case Token.Mod => Op.Mod
                case _ => throwError("unreachable")
            }
            advance()
            val right = parseAtomicExpr()
            left = Expr.BinaryOp(left, op, right)
        }
        left
    }
    private def parseStructLiteralFields(): List[(String, Expr)] = {
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

    private def parsePostfix(expr: Expr): Expr = {
        peek() match {
            case Token.OpenSquare => {
                advance()
                val index = parseExpr()
                expect(Token.CloseSquare)
                parsePostfix(Expr.ArrIndex(expr, index))
            }
            case Token.Dot => {
                advance()
                peek() match {
                    case Token.Variable(field) => {
                        advance()
                        parsePostfix(Expr.FieldAccess(expr, field))
                    }
                    case x => throwError(s"Expected field name after '.', got '$x'")
                }
            }
            case _ => expr
        }
    }
    private def parseAtomicExpr(): Expr = {
        peek() match {
            case Token.BitComplement => {
                advance()
                val left = Expr.UnaryOp(parseAtomicExpr(), Op.BitComplement)
                peek() match {
                    case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq =>
                        val bop = peek() match {
                            case Token.Gt  => Bop.Gt
                            case Token.Lt  => Bop.Lt
                            case Token.Gte => Bop.Gte
                            case Token.Lte => Bop.Lte
                            case Token.Eq  => Bop.Eq
                            case Token.Neq => Bop.Neq
                            case _ => throwError("unreachable")
                        }
                        advance()
                        val right = parseExpr()
                        Expr.BoolLift(BoolExpr.Compare(left, bop, right))
                    case _ => left
                }
            }
            case Token.LiteralInt(n) => {
                advance()
                val left = Expr.Num(n)
                peek() match {
                    case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq =>
                        val bop = peek() match {
                            case Token.Gt  => Bop.Gt
                            case Token.Lt  => Bop.Lt
                            case Token.Gte => Bop.Gte
                            case Token.Lte => Bop.Lte
                            case Token.Eq  => Bop.Eq
                            case Token.Neq => Bop.Neq
                            case _ => throwError("unreachable")
                        }
                        advance()
                        val right = parseExpr()
                        Expr.BoolLift(BoolExpr.Compare(left, bop, right))
                    case _ => left
                }
            }
            case Token.LiteralFloat(n) => {
                advance()
                val left = Expr.Flt(n)
                peek() match {
                    case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq =>
                        val bop = peek() match {
                            case Token.Gt  => Bop.Gt
                            case Token.Lt  => Bop.Lt
                            case Token.Gte => Bop.Gte
                            case Token.Lte => Bop.Lte
                            case Token.Eq  => Bop.Eq
                            case Token.Neq => Bop.Neq
                            case _ => throwError("unreachable")
                        }
                        advance()
                        val right = parseExpr()
                        Expr.BoolLift(BoolExpr.Compare(left, bop, right))
                    case _ => left
                }
            }
            case Token.StringLit(s) => {
                advance()
                Expr.Str(s)
            }
            case Token.OpenSquare => {
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
            case Token.TypeInt    => { advance(); Expr.TypeLiteral(SimpType.TypeInt) }
            case Token.TypeString => { advance(); Expr.TypeLiteral(SimpType.TypeString) }
            case Token.TypeBool   => { advance(); Expr.TypeLiteral(SimpType.TypeBool) }
            case Token.TypeFloat  => { advance(); Expr.TypeLiteral(SimpType.TypeFloat) }

            case Token.Variable(namespace) if peekNext() == Token.DoubleColon => {
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
                peek() match {
                    case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq =>
                        val bop = peek() match {
                            case Token.Gt  => Bop.Gt
                            case Token.Lt  => Bop.Lt
                            case Token.Gte => Bop.Gte
                            case Token.Lte => Bop.Lte
                            case Token.Eq  => Bop.Eq
                            case Token.Neq => Bop.Neq
                            case _ => throwError("unreachable")
                        }
                        advance()
                        val right = parseExpr()
                        Expr.BoolLift(BoolExpr.Compare(left, bop, right))
                    case _ => left
                }
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
                        val bop = peek() match {
                            case Token.Gt  => Bop.Gt
                            case Token.Lt  => Bop.Lt
                            case Token.Gte => Bop.Gte
                            case Token.Lte => Bop.Lte
                            case Token.Eq  => Bop.Eq
                            case Token.Neq => Bop.Neq
                            case _ => throwError("unreachable")
                        }
                        advance()
                        val right = parseExpr()
                        Expr.BoolLift(BoolExpr.Compare(left, bop, right))
                    }
                    case _ => {expect(Token.CloseBracket); left}
                }
            }
            case x => throwError(s"Unexpected '$x'")
        }
    }
    private def parseBool(): BoolExpr = {
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
    private def parseStructField(): (String, SimpType, Option[Expr]) = {
        peek() match {
            case Token.Variable(name) => {
                advance()
                expect(Token.Colon)
                val t = parseType()
                val default = if peek() == Token.Assign then {
                    advance()
                    Some(parseExpr())
                } else None
                (name, t, default)
            }
            case x => throwError(s"Expected field name, got '$x'")
        }
    }
    private def parseStructFields(): List[(String, SimpType, Option[Expr])] = {
        expect(Token.OpenBrace)
        if peek() == Token.CloseBrace then {
            advance()
            List()
        } else {
            val params = scala.collection.mutable.ListBuffer[(String, SimpType, Option[Expr])]()
            params += parseStructField()
            while peek() == Token.Comma do {
                advance()
                params += parseStructField()
            }
            expect(Token.CloseBrace)
            params.toList
        }
    }
    private def parseDecl(): Decl = peek() match {
        case Token.Fn => {
            advance()
            peek() match {
                case Token.Variable(name) => {
                    advance()
                    val params = parseParams()
                    expect(Token.Arrow)
                    val returnType = parseType()
                    expect(Token.OpenBrace)
                    val body = parseCmd()
                    expect(Token.CloseBrace)
                    Decl.FnDecl(name, params, body, returnType)
                }
                case x => throwError(s"Expected function name, got '$x'")
            }
        }
        case Token.Struct => {
            advance()
            peek() match {
                case Token.Variable(name) => {
                    advance()
                    val fields = parseStructFields()
                    Decl.StructDecl(name, fields)
                }
                case x => throwError(s"Expected struct name, got '$x'")
            }
        }
        case Token.Import => {
            advance()
            peek() match {
                case Token.StringLit(path) => {
                    advance()
                    val alias = peek() match {
                        case Token.As => {
                            advance()
                            peek() match {
                                case Token.Variable(name) => { advance(); name }
                                case x => throwError(s"Expected alias, got '$x'")
                            }
                        }
                        case _ => {
                            path.split("/").last.split("\\.").head
                        }
                    }
                    Decl.ImportDecl(path, alias)
                }
                case x => throwError(s"Expected path as string literal, got '$x'")
            }
        }
        case x => throwError(s"Expected declaration, got '$x'")
    }
    private def parseAtomicBool(): BoolExpr = {
        peek() match {
            case Token.BoolLit(b) => {
                advance()
                BoolExpr.Literal(b)
            }
            case Token.Not => {
                advance()
                val inside = parseBool()
                BoolExpr.Not(inside)
            }
            case Token.Deref | Token.LiteralInt(_)  => {
                val left = parseExpr()
                peek() match {
                    case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq => {
                        val bop = peek() match {
                            case Token.Gt => Bop.Gt
                            case Token.Gte => Bop.Gte
                            case Token.Lt => Bop.Lt
                            case Token.Lte => Bop.Lte
                            case Token.Eq => Bop.Eq
                            case Token.Neq => Bop.Neq
                            case x => throwError(s"Expected boolean operator, got '${x}'")
                        }
                        advance()
                        val right = parseExpr()
                        BoolExpr.Compare(left, bop, right)
                    }
                    case _ => BoolExpr.FromExpr(left)
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
                        val bop = peek() match {
                            case Token.Gt  => Bop.Gt
                            case Token.Lt  => Bop.Lt
                            case Token.Gte => Bop.Gte
                            case Token.Lte => Bop.Lte
                            case Token.Eq  => Bop.Eq
                            case Token.Neq => Bop.Neq
                            case _ => throwError("unreachable")
                        }
                        advance()
                        val right = parseExpr()
                        BoolExpr.Compare(expr, bop, right)
                    case _ => BoolExpr.FromExpr(expr)
                }
            }
            case Token.Variable(_) if peekNext() == Token.Dot => {
                val expr = parsePostfix(parseAtomicExpr())
                peek() match {
                    case Token.Gt | Token.Lt | Token.Gte | Token.Lte | Token.Eq | Token.Neq =>
                        val bop = peek() match {
                            case Token.Gt  => Bop.Gt
                            case Token.Lt  => Bop.Lt
                            case Token.Gte => Bop.Gte
                            case Token.Lte => Bop.Lte
                            case Token.Eq  => Bop.Eq
                            case Token.Neq => Bop.Neq
                            case _ => throwError("unreachable")
                        }
                        advance()
                        val right = parseExpr()
                        BoolExpr.Compare(expr, bop, right)
                    case _ => BoolExpr.FromExpr(expr)
                }
            }
            case x => throwError(s"Expected boolean expression, got '$x'")
        }
    }