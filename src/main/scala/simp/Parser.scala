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

    def parseProgram(): Program = {
        val commands  = List(Token.Skip, Token.If, Token.Then, Token.Else, Token.While, Token.Do, Token.Assign, Token.Print)
        val bools = List(Token.Gt, Token.Lt, Token.Gte, Token.Lte, Token.Eq, Token.And, Token.Or, Token.Not)

        if commands.exists(tokens.contains) then {
            return Program.PCmd(parseCmd())
        }
        if bools.exists(tokens.contains) then {
            return Program.PBool(parseBool())
        }

        peek() match {
            case Token.BoolLit(_) => return Program.PBool(parseBool())
            case _ =>
        }

        Program.PExpr(parseExpr())

    }

    private def parseCmd(): Cmd = {
        val left = parseSingleCmd()
        if peek() == Token.Semicolon then
            advance()
            val right = parseCmd()
            Cmd.Seq(left, right)
        else
            left
    }

    private def parseSingleCmd(): Cmd = {
        val current_token = peek()
        current_token match {
            case Token.Skip => {
                advance(); 
                Cmd.Skip
            }
            case Token.If  => {
                advance()
                val cond = parseBool()
                expect(Token.Then)
                val thenBranch = parseCmd()
                expect(Token.Else)
                val elseBranch = parseCmd()
                Cmd.If(cond, thenBranch, elseBranch)
            }
            case Token.While => {
                advance()
                val cond = parseBool()
                expect(Token.Do)
                val body = parseCmd()
                Cmd.While(cond, body)
            }
            case Token.Variable(l) => {
                val loc = l
                advance()
                expect(Token.Assign)
                val value = parseExpr()
                Cmd.Assign(loc, value)
            }
            case Token.Print => {
                advance()
                peek() match {
                    case Token.StringLit(s) => {
                        advance()
                        Cmd.Print(Printable.PrintStr(s))
                    }
                    case e => {
                        val value = parseExpr()
                        Cmd.Print(Printable.PrintExpr(value))
                    }
                }
            }
            case Token.OpenBracket => {
                advance()
                val cmd = parseCmd()
                expect(Token.CloseBracket)
                cmd
            }
            case x => throw RuntimeException(s"Unexpected '$x'")
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
        while List(Token.Mul, Token.Div).contains(peek()) do {
            val op: Op = peek() match {
                case Token.Mul => Op.Mul
                case Token.Div => Op.Div
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