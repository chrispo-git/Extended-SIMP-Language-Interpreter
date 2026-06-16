package simp

trait ParserDecl { self: Parser =>
    protected def parseStructField(): (String, SimpType, Option[Expr]) = {
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
    protected def preRegisterStructs(): Unit = {
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
    protected def parseStructFields(): List[(String, SimpType, Option[Expr])] = {
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
    protected def parseDecl(): Decl = peek() match {
        case Token.Fn => {
            advance()
            peek() match {
                case Token.Variable(name) => {
                    advance()
                    val params = parseParams()
                    //println(s"parseDecl: fn $name params=$params, peek=${peek()}")
                    expect(Token.Arrow)
                    val returnType = parseType()
                    //println(s"parseDecl: fn $name returnType=$returnType, peek=${peek()}")
                    expect(Token.OpenBrace)
                    //println(s"parseDecl: fn $name body starting, peek=${peek()}")
                    val body = parseCmd()
                    //println(s"parseDecl: fn $name body done, peek=${peek()}")
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
    def parseImpl(): Program.PImpl = {

        advance()
        val structName = peek() match {
            case Token.Variable(name) => { advance(); name }
            case x => throwError(s"Expected struct name after 'impl', got '$x'")
        }
        expect(Token.OpenBrace)
        val methods = scala.collection.mutable.ListBuffer[Decl.FnDecl]()
        while peek() != Token.CloseBrace do {
            val out = parseDecl() match {
                case f: Decl.FnDecl => f
                case x => throwError(s"Expected function, got $x")
            }
            methods += out
        }
        expect(Token.CloseBrace)
        Program.PImpl(structName, methods.toList)
    }
    protected def parseArrType(t: SimpType): SimpType = {
        var out = t
        while peek() == Token.OpenSquare do {
            expect(Token.OpenSquare)
            expect(Token.CloseSquare)
            out = SimpType.TypeArr(out)
        }
        out
    }
    protected def parseType(): SimpType = peek() match {
        case Token.OpenBracket => {
            advance()
            val fst = parseType()
            expect(Token.Comma)
            val snd = parseType()
            expect(Token.CloseBracket)
            var t: SimpType = SimpType.TypePair(fst, snd)
            parseArrType(t)
        }
        case Token.TypeInt  => { 
            advance(); 
            var t: SimpType = SimpType.TypeInt
            parseArrType(t)
        }
        case Token.TypeFloat  => { 
            advance(); 
            var t: SimpType = SimpType.TypeFloat
            parseArrType(t)
        }
        case Token.TypeString  => { 
            advance(); 
            var t: SimpType = SimpType.TypeString
            parseArrType(t)
        }
        case Token.TypeBool  => { 
            advance(); 
            var t: SimpType = SimpType.TypeBool
            parseArrType(t)
        }
        case Token.Variable(name) => { 
            advance(); 
            var t: SimpType = SimpType.TypeStruct(name) 
            parseArrType(t)
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
        case x => throwError(s"Expected type, got '$x'")
    }
}