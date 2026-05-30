package simp


class Parser(tokens: List[Token], structEnv : StructEnv):
    private var pos: Int = 0

    private def isAtEnd(): Boolean = pos >= tokens.length

    private def peek(): Token =  if isAtEnd() then Token.EOF else tokens(pos)

    private def peekNext(): Token = if pos+1 >= tokens.length then Token.EOF else tokens(pos+1)

    private def peekN(n: Int): Token = if pos+n >= tokens.length then Token.EOF else tokens(pos+n)

    private def advance(): Token = if isAtEnd() then Token.EOF else {val t = tokens(pos);pos+=1;t}

    private def advanceTo(n: Int): Token = if isAtEnd() then Token.EOF else {val t = tokens(pos);pos+=n;t} 

    private def expect(expected: Token): Unit = {
        if peek() != expected then throw RuntimeException(s"Expected '$expected', got '${peek()}'")
        else advance()
    }

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
                case Token.LiteralInt(_) | Token.Deref =>
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
                                case _ => throw RuntimeException("unreachable")
                            }
                            advance()
                            val right = parseExpr()
                            Program.PBool(BoolExpr.Compare(expr, bop, right))
                        case _ => Program.PExpr(expr)
                    }
                case Token.Variable(_) if peekNext() == Token.OpenBracket => Program.PExpr(parseAtomicExpr())
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
            case Token.Fn |  Token.Struct | Token.Import =>
                throw RuntimeException("Declarations must be at the top of the file")
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
                advance(); 
                advance(); 
                peek() match {
                    case Token.Variable(field) => {
                        advance()
                        expect(Token.Assign)
                        val value = parseExpr()
                        Cmd.FieldAssign(l, field, value)
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
                    case x => throw RuntimeException(s"Expected loop variable, got '$x'")
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
                    case x => throw RuntimeException(s"Expected assignment, got '$x'")
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
            
            case x => throw RuntimeException(s"Unexpected '$x'")
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
            case x => throw RuntimeException(s"Expected parameter name, got '$x'")
        }
    }

    private def parseType(): SimpType = peek() match {
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
        case x => throw RuntimeException(s"Expected type, got '$x'")
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
        var left = parseTerm()
        while List(Token.Add, Token.Sub).contains(peek()) do {
            val op: Op  = peek() match {
                case Token.Add => Op.Add
                case Token.Sub => Op.Sub
                case _ => throw RuntimeException("unreachable")
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
                case _ => throw RuntimeException("unreachable")
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
                case x => throw RuntimeException(s"Expected field name, got '$x'")
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
                    case x => throw RuntimeException(s"Expected field name after '.', got '$x'")
                }
            }
            case _ => expr
        }
    }
    private def parseAtomicExpr(): Expr = {
        peek() match {
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
                            case _ => throw RuntimeException("unreachable")
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
                    case x => throw RuntimeException(s"Expected name after '::', got '$x'")
                }
            }
            case Token.Variable(name) if peekNext() == Token.OpenBrace && structEnv.exists(name) => {
                advance()
                advance()
                val fields = parseStructLiteralFields()
                expect(Token.CloseBrace)
                Expr.StructLiteral(name, fields)
            }
            case Token.Deref => {
                advance()
                val left = peek() match {
                    case Token.Variable(l) => {
                        advance()
                        Expr.Deref(l)
                    }
                    case x => throw RuntimeException(s"Expected variable after '!', got '$x'")
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
                            case _ => throw RuntimeException("unreachable")
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
                val inside = parseExpr()
                expect(Token.CloseBracket)
                inside
            }
            case x => throw RuntimeException(s"Unexpected '$x'")
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
                case _ => throw RuntimeException("unreachable")
            }
        }
        left
    }
    private def parseStructFields(): List[(String, SimpType)] = {
        expect(Token.OpenBrace)
        if peek() == Token.CloseBrace then {
            advance()
            List()
        } else {
            val params = scala.collection.mutable.ListBuffer[(String, SimpType)]()
            params += parseParam()
            while peek() == Token.Comma do {
                advance()
                params += parseParam()
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
                case x => throw RuntimeException(s"Expected function name, got '$x'")
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
                case x => throw RuntimeException(s"Expected struct name, got '$x'")
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
                                case x => throw RuntimeException(s"Expected alias, got '$x'")
                            }
                        }
                        case _ => {
                            path.split("/").last.split("\\.").head
                        }
                    }
                    Decl.ImportDecl(path, alias)
                }
                case x => throw RuntimeException(s"Expected path as string literal, got '$x'")
            }
        }
        case x => throw RuntimeException(s"Expected declaration, got '$x'")
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
                            case x => throw RuntimeException(s"Expected boolean operator, got '${x}'")
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
            case x => throw RuntimeException(s"Expected boolean expression, got '$x'")
        }
    }