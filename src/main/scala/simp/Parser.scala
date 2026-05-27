package simp


class Parser(tokens: List[Token]):
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

    def parseProgram(): List[Program] = {
        val items = scala.collection.mutable.ListBuffer[Program]()
        while !isAtEnd() && peek() != Token.EOF do {
            peek() match {
                case Token.Fn | Token.Pd => items += Program.PDecl(parseDecl())
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
                case Token.Fn | Token.Pd => Program.PDecl(parseDecl())
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
                case _ => Program.PCmd(parseCmd())
            }
            items += item
            while peek() == Token.Semicolon do advance()
        }
        items.toList
    }
    private def parseCmd(): Cmd = {
        val left = parseSingleCmd()
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
            case Token.Variable(l) => {
                val loc = l
                advance()
                peek() match {
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
                val expr = parseExpr()
                Cmd.Return(expr)
            }
            case Token.Call => {
                advance()
                peek() match {
                    case Token.Variable(name) => {
                        advance()
                        val args = parseArgs()
                        Cmd.PdCall(name, args)
                    }
                    case x => throw RuntimeException(s"Expected procedure name, got '$x'")
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
    private def parseParams(): List[String] = {
        expect(Token.OpenBracket)
        if peek() == Token.CloseBracket then {
            advance()
            List()
        } else {
            val params = scala.collection.mutable.ListBuffer[String]()
            peek() match {
                case Token.Variable(name) => {advance(); params += name}
                case x => throw RuntimeException(s"Expected parameter name, got '$x'")
            }
            while peek() == Token.Comma do {
                advance()
                peek() match {
                    case Token.Variable(name) => {advance(); params += name}
                    case x => throw RuntimeException(s"Expected parameter name, got '$x'")
                }
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
        var left = parseAtomicExpr()
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
    private def parseAtomicExpr(): Expr = {
        peek() match {
            case Token.LiteralInt(n) => {
                advance()
                Expr.Num(n)
            }
            case Token.StringLit(s) => {
                advance()
                Expr.Str(s)
            }
            case Token.Deref => {
                advance()
                peek() match {
                    case Token.Variable(l) => {
                        advance()
                        Expr.Deref(l)
                    }
                    case x => throw RuntimeException(s"Expected variable after '!', got '$x'")
                }
            }
            case Token.Variable(name) if peekNext() == Token.OpenBracket => {
                advance()
                val args = parseArgs()
                Expr.FnCall(name, args)
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
    private def parseDecl(): Decl = peek() match {
        case Token.Fn => {
            advance()
            peek() match {
                case Token.Variable(name) => {
                    advance()
                    val params = parseParams()
                    expect(Token.OpenBrace)
                    val body = parseCmd()
                    expect(Token.CloseBrace)
                    Decl.FnDecl(name, params, body)
                }
                case x => throw RuntimeException(s"Expected function name, got '$x'")
            }
        }
        case Token.Pd => {
            advance()
            peek() match {
                case Token.Variable(name) => {
                    advance()
                    val params = parseParams()
                    expect(Token.OpenBrace)
                    val body = parseCmd()
                    expect(Token.CloseBrace)
                    Decl.PdDecl(name, params, body)
                }
                case x => throw RuntimeException(s"Expected procedure name, got '$x'")
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
            case Token.OpenBracket => {
                advance()
                val inside = parseBool()
                expect(Token.CloseBracket)
                inside
            }
            case x => throw RuntimeException(s"Expected boolean expression, got '$x'")
        }
    }