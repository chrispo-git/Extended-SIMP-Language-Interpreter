package simp

trait ParserDecl { self: Parser =>
    // Parses an optional `<T, U, ...>` type-parameter declaration list (e.g. after
    // `struct Stack` or `impl Stack`), returning the declared names. Returns an
    // empty list if no `<` follows.
    protected def parseTypeParamDecls(): List[String] = {
        if peek() != Token.Lt then {
            List()
        } else {
            advance()
            val params = scala.collection.mutable.ListBuffer[String]()
            def parseOne(): Unit = peek() match {
                case Token.Variable(name) => { advance(); params += name }
                case x => throwError(s"Expected type parameter name, got '$x'")
            }
            parseOne()
            while peek() == Token.Comma do {
                advance()
                parseOne()
            }
            expect(Token.Gt)
            params.toList
        }
    }
    protected def parseStructField(): (String, SimpType, Option[Expr], Boolean) = {
        val isPrivate = peek() match {
            case Token.Priv => {advance(); true}
            case _ => false
        };
        peek() match {
            case Token.Variable(name) => {
                advance()
                expect(Token.Colon)
                val t = parseType()
                val default = if peek() == Token.Assign then {
                    advance()
                    Some(parseExpr())
                } else None
                (name, t, default, isPrivate)
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
    protected def parseStructFields(): List[(String, SimpType, Option[Expr], Boolean)] = {
        expect(Token.OpenBrace)
        if peek() == Token.CloseBrace then {
            advance()
            List()
        } else {
            val params = scala.collection.mutable.ListBuffer[(String, SimpType, Option[Expr], Boolean)]()
            params += parseStructField()
            while peek() == Token.Comma do {
                advance()
                params += parseStructField()
            }
            expect(Token.CloseBrace)
            params.toList
        }
    }
    protected def parseStructDecl(isLocked: Boolean): Decl = {
        advance()
        peek() match {
            case Token.Variable(name) => {
                advance()
                val typeParams = parseTypeParamDecls()
                val savedTypeParams = activeTypeParams
                activeTypeParams = typeParams.toSet
                val fields = try parseStructFields() finally activeTypeParams = savedTypeParams
                Decl.StructDecl(name, fields, isLocked, typeParams)
            }
            case x => throwError(s"Expected struct name, got '$x'")
        }
    }
    protected def parseDecl(): Decl = peek() match {
        case Token.Fn => {
            advance()
            var isPrivate = false
            var isStatic = false
            var parsingModifiers = true
            while parsingModifiers do {
                peek() match {
                    case Token.Priv => { advance(); isPrivate = true }
                    case Token.Static => { advance(); isStatic = true }
                    case _ => parsingModifiers = false
                }
            }
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
                    Decl.FnDecl(name, params, body, returnType, isPrivate, isStatic)
                }
                case x => throwError(s"Expected function name, got '$x'")
            }
        }
        case Token.Struct => parseStructDecl(isLocked = false)
        case Token.Locked => {
            advance()
            peek() match {
                case Token.Struct => parseStructDecl(isLocked = true)
                case x => throwError(s"Expected 'struct' after 'locked', got '$x'")
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
        val typeParams = parseTypeParamDecls()
        val savedTypeParams = activeTypeParams
        activeTypeParams = typeParams.toSet
        expect(Token.OpenBrace)
        val methods = scala.collection.mutable.ListBuffer[Decl.FnDecl]()
        try {
            while peek() != Token.CloseBrace do {
                val out = parseDecl() match {
                    case f: Decl.FnDecl => f
                    case x => throwError(s"Expected function, got $x")
                }
                methods += out
            }
            expect(Token.CloseBrace)
        } finally {
            activeTypeParams = savedTypeParams
        }
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
    protected def parseParams(): List[(String, SimpType)] = {
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
    protected def parseParam(): (String, SimpType) = {
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
            var t: SimpType = if activeTypeParams.contains(name) then SimpType.TypeParam(name) else SimpType.TypeStruct(name)
            if peek() == Token.Lt then {
                // Generic type arguments (e.g. Stack<Int>) are parsed and discarded:
                // generics are erased at runtime, so no type substitution happens here.
                advance()
                parseType()
                while peek() == Token.Comma do {
                    advance()
                    parseType()
                }
                expect(Token.Gt)
            }
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