package simp

import SimpUtils.*

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
    private def parseFieldIndexAssign(l: String, field: String): Cmd = {
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
                    Cmd.FieldIndexAssign(l, field, firstIndex, value)
                else
                    Cmd.FieldIndexAssignNested(l, field, firstIndex :: extraIndices.toList, value)
            }
            case Token.PlusEq => { advance(); Cmd.FieldIndexAssign(l, field, firstIndex, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), firstIndex), Op.Add, parseExpr())) }
            case Token.MinusEq => { advance(); Cmd.FieldIndexAssign(l, field, firstIndex, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), firstIndex), Op.Sub, parseExpr())) }
            case Token.MulEq => { advance(); Cmd.FieldIndexAssign(l, field, firstIndex, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), firstIndex), Op.Mul, parseExpr())) }
            case Token.DivEq => { advance(); Cmd.FieldIndexAssign(l, field, firstIndex, Expr.BinaryOp(Expr.ArrIndex(Expr.FieldAccess(Expr.Ref(l), field), firstIndex), Op.Div, parseExpr())) }
            case x => throwError(s"Expected assignment, got '$x'")
        }
    }

    private def parseFieldAssign(l: String, field: String): Cmd = {
        peek() match {
            case Token.Assign => { advance(); Cmd.FieldAssign(l, field, parseBoolExpr()) }
            case Token.PlusEq => { advance(); Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Add, parseExpr())) }
            case Token.MinusEq => { advance(); Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Sub, parseExpr())) }
            case Token.MulEq => { advance(); Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Mul, parseExpr())) }
            case Token.DivEq => { advance(); Cmd.FieldAssign(l, field, Expr.BinaryOp(Expr.FieldAccess(Expr.Ref(l), field), Op.Div, parseExpr())) }
            case x => throwError(s"Expected assignment, got '$x'")
        }
    }

    private def parseFieldOrIndexAssign(l: String): Cmd = {
        advance()
        peek() match {
            case Token.Variable(field) => {
                advance()
                peek() match {
                    case Token.OpenSquare => parseFieldIndexAssign(l, field)
                    case _ => parseFieldAssign(l, field)
                }
            }
            case x => throw RuntimeException(s"[Error] Expected field name after '.', got '$x'")
        }
    }

    private def parseVarAssign(l: String): Cmd = {
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
                    Cmd.ArrAssign(l, firstIndex, value)
                } else {
                    Cmd.ArrAssignNested(l, firstIndex :: extraIndices.toList, value)
                }
            }
            case Token.Assign => { advance(); Cmd.Assign(l, parseBoolExpr()) }
            case Token.PlusEq => { advance(); Cmd.Assign(l, Expr.BinaryOp(Expr.Deref(l), Op.Add, parseExpr())) }
            case Token.MinusEq => { advance(); Cmd.Assign(l, Expr.BinaryOp(Expr.Deref(l), Op.Sub, parseExpr())) }
            case Token.MulEq => { advance(); Cmd.Assign(l, Expr.BinaryOp(Expr.Deref(l), Op.Mul, parseExpr())) }
            case Token.DivEq => { advance(); Cmd.Assign(l, Expr.BinaryOp(Expr.Deref(l), Op.Div, parseExpr())) }
            case x => throwError(s"Expected assignment, got '$x'")
        }
    }
    private def parseBoolExpr(): Expr = {
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
    private def foldBinary(left: Expr, op: Op, right: Expr): Expr = (left, op, right) match {

        // Just Ints...
        case (Expr.Num(l),Op.Add, Expr.Num(r)) => Expr.Num(l + r)
        case (Expr.Num(l),Op.Sub, Expr.Num(r)) => Expr.Num(l - r)
        case (Expr.Num(l),Op.Mul, Expr.Num(r)) => Expr.Num(l * r)
        case (Expr.Num(l),Op.Div, Expr.Num(r)) if r != 0 => Expr.Num(l / r)
        case (Expr.Num(l),Op.Mod, Expr.Num(r)) if r != 0 => Expr.Num(l % r)

        // Just Floats...
        case (Expr.Flt(l),Op.Add, Expr.Flt(r)) => Expr.Flt(l + r)
        case (Expr.Flt(l),Op.Sub, Expr.Flt(r)) => Expr.Flt(l - r)
        case (Expr.Flt(l),Op.Mul, Expr.Flt(r)) => Expr.Flt(l * r)
        case (Expr.Flt(l),Op.Div, Expr.Flt(r)) if r != 0 => Expr.Flt(l / r)

        // Float + Int Mixed
        case (Expr.Num(l),Op.Add, Expr.Flt(r)) => Expr.Flt(l + r)
        case (Expr.Num(l),Op.Sub, Expr.Flt(r)) => Expr.Flt(l - r)
        case (Expr.Num(l),Op.Mul, Expr.Flt(r)) => Expr.Flt(l * r)
        case (Expr.Num(l),Op.Div, Expr.Flt(r)) if r != 0 => Expr.Flt(l / r)
        case (Expr.Flt(l),Op.Add, Expr.Num(r)) => Expr.Flt(l + r)
        case (Expr.Flt(l),Op.Sub, Expr.Num(r)) => Expr.Flt(l - r)
        case (Expr.Flt(l),Op.Mul, Expr.Num(r)) => Expr.Flt(l * r)
        case (Expr.Flt(l),Op.Div, Expr.Num(r)) if r != 0 => Expr.Flt(l / r)

        // Concatenation of literals
        case (Expr.Str(l), Op.Add, Expr.Str(r)) => Expr.Str(l + r)

        // Bitwise shit
        case (Expr.Num(l), Op.BitAnd,      Expr.Num(r)) => Expr.Num(l & r)
        case (Expr.Num(l), Op.BitOr,       Expr.Num(r)) => Expr.Num(l | r)
        case (Expr.Num(l), Op.BitXor,      Expr.Num(r)) => Expr.Num(l ^ r)
        case (Expr.Num(l), Op.BitLeft,     Expr.Num(r)) => Expr.Num(l << r)
        case (Expr.Num(l), Op.BitRight,    Expr.Num(r)) => Expr.Num(l >> r)
        case (Expr.Num(l), Op.BitRightFill, Expr.Num(r)) => Expr.Num(l >>> r)
        
        case _ => Expr.BinaryOp(left, op, right)
    }

    private def foldUnary(expr: Expr, op: Op): Expr = (expr, op) match {
        case (Expr.Num(n), Op.BitComplement) => Expr.Num(~n)
        case _ => Expr.UnaryOp(expr, op)
    }
    private def parseIfBody(): (Cmd, Cmd) = {
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

    private def parseIfCmd(): Cmd = {
        advance()
        val cond = parseBool()
        expect(Token.Then)
        val (thenBranch, elseBranch) = parseIfBody()
        Cmd.If(cond, thenBranch, elseBranch)
    }

    private def parseElifCmd(): Cmd = {
        advance()
        val cond = parseBool()
        expect(Token.Then)
        val (thenBranch, elseBranch) = parseIfBody()
        Cmd.If(cond, thenBranch, elseBranch)
    }

    private def parseWhileCmd(): Cmd = {
        advance()
        val cond = parseBool()
        expect(Token.Do)
        expect(Token.OpenBrace)
        val body = parseCmd()
        expect(Token.CloseBrace)
        Cmd.While(cond, body)
    }

    private def parseForCmd(): Cmd = {
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

    private def parseOpenBracketCmd(): Cmd = {
        val savedPos = pos
        advance()
        try {
            val expr = parseExpr()
            peek() match {
                case Token.Comma => {
                    advance()
                    val right = parseExpr()
                    expect(Token.CloseBracket)
                    Cmd.Assign("_", Expr.Pair(expr, right))
                }
                case Token.CloseBracket => {
                    advance()
                    Cmd.Assign("_", expr)
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

    private def parseReturnCmd(): Cmd = {
        advance()
        peek() match {
            case Token.Semicolon | Token.CloseBrace | Token.EOF => Cmd.Return(None)
            case _ => Cmd.Return(Some(parseBoolExpr()))
        }
    }
    private def parseConstAssign(): Cmd = {
        peek() match {
            case Token.Variable(l) => {
                advance();
                expect(Token.Assign)
                Cmd.ConstAssign(l, parseBoolExpr())
            }
            case x => throwError(s"Expected variable name after const, got '$x'")
        }
    }
    private def parseScope(): Cmd = {
        if peek() == Token.CloseBrace then {
            advance();
            Cmd.Scope(Cmd.Skip)
        } else {
            val body = parseCmd()
            expect(Token.CloseBrace)
            Cmd.Scope(body)
        }
    }
    private def parseSingleCmd(): Cmd = {
        peek() match {
            case Token.Skip     => { advance(); Cmd.Skip }
            case Token.Continue => { advance(); Cmd.Continue }
            case Token.Break    => { advance(); Cmd.Break }
            case Token.Const => {advance(); parseConstAssign() }
            case Token.Variable(l) if peekNext() == Token.Dot => { advance(); parseFieldOrIndexAssign(l) }
            case Token.If       => parseIfCmd()
            case Token.Elif     => parseElifCmd()
            case Token.While    => parseWhileCmd()
            case Token.For      => parseForCmd()
            case Token.Variable(l) => { advance(); parseVarAssign(l) }
            case Token.Print    => { advance(); Cmd.Print(parseExpr()) }
            case Token.OpenBracket => parseOpenBracketCmd()
            case Token.Return   => parseReturnCmd()
            case Token.OpenBrace => {advance(); parseScope()}
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
            left = foldBinary(left, op, right)
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
            left = foldBinary(left, op, right)
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
            left = foldBinary(left, op, right)
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
    private def parseMatch(): Expr = {
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

    private def parsePattern(): Pattern = peek() match {
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

    private def parsePatternFields(): List[(String, Pattern)] = {
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

    private def parseAtomicExpr(): Expr = {
        peek() match {
            case Token.BitComplement => {
                advance()
                val left = foldUnary(parseAtomicExpr(), Op.BitComplement)
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
            case Token.Match => parseMatch()
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
    private def makeFromExpr(expr: Expr): BoolExpr = expr match {
        case Expr.BoolLift(inner) => inner
        case _ => BoolExpr.FromExpr(expr)
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
                    case _ => makeFromExpr(expr)
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
                    case _ => makeFromExpr(expr)
                }
            }
            case x => throwError(s"Expected boolean expression, got '$x'")
        }
    }