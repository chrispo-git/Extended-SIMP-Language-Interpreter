package simp
import java.io.File

class Evaluator(fnEnv: FunctionEnv, structEnv: StructEnv, cwd: String = "."):
    private val importedFiles = scala.collection.mutable.Set[String]()
    private val completedImports = scala.collection.mutable.Map[String, Set[String]]()


    def evalProgram(program: List[Program], store: Store): Unit = {
        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body, returnType)) => fnEnv.registerFn(name, Decl.FnDecl(name, params, body, returnType))
            case Program.PDecl(Decl.PdDecl(name, params, body)) => fnEnv.registerPd(name, Decl.PdDecl(name, params, body))
            case Program.PDecl(Decl.ImportDecl(path, alias)) => processImport(path, alias, cwd, store)
            case Program.PDecl(Decl.StructDecl(name, fields)) => structEnv.register(name, StructDef(fields))
            case Program.PCmd(cmd) => execCmd(cmd, store)
            case Program.PExpr(expr) => println(evalExpr(expr, store))
            case Program.PBool(b) => println(evalBool(b, store))
        })
    }

    private def processImport(path: String, alias: String, currentDir: String, store: Store ):  Unit = {
        val fullPath = java.io.File(s"$currentDir/$path").getCanonicalPath


        if importedFiles.contains(fullPath) then throw RuntimeException(s"Circular import detected: $path")


        val existingAliases = completedImports.getOrElse(fullPath, Set())
        if existingAliases.contains(alias) then return
            
        importedFiles += fullPath

        val source = try {
            scala.io.Source.fromFile(fullPath).mkString
        } catch case _ => throw RuntimeException(s"Could not find import $path")

        val tokens = Lexer(source).tokenise()
        val importStructEnv = StructEnv()
        val program = Parser(tokens, importStructEnv).parseProgram()


        val declaredNames = program.collect {
            case Program.PDecl(Decl.FnDecl(name, _, _, _)) => name
            case Program.PDecl(Decl.PdDecl(name, _, _)) => name
        }.toSet

        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body, returnType)) => {
                val qualifiedBody = qualifyBody(body, alias, declaredNames)
                fnEnv.registerFn(s"$alias::$name", Decl.FnDecl(s"$alias::$name", params, qualifiedBody, returnType))
            }
            case Program.PDecl(Decl.PdDecl(name, params, body)) => {
                val qualifiedBody = qualifyBody(body, alias, declaredNames)
                fnEnv.registerPd(s"$alias::$name", Decl.PdDecl(s"$alias::$name", params, qualifiedBody))
            }
            case Program.PDecl(Decl.ImportDecl(path, alias)) => {
                val importDir = File(fullPath).getParentFile.getAbsolutePath
                processImport(path, alias, importDir, store)
            }
            case Program.PDecl(Decl.StructDecl(name, fields)) => structEnv.register(s"$alias::$name", StructDef(fields))
            case _ => throw RuntimeException(s"Imports can only contain declarations")
        })

        importedFiles -= fullPath 
        completedImports(fullPath) = existingAliases + alias
    }


    private def qualifyBody(cmd: Cmd, alias: String, declaredNames: Set[String]): Cmd = cmd match {
        case Cmd.Seq(a, b) => Cmd.Seq(qualifyBody(a, alias, declaredNames), qualifyBody(b, alias, declaredNames))
        case Cmd.If(cond, t, e) => Cmd.If(cond, qualifyBody(t, alias, declaredNames), qualifyBody(e, alias, declaredNames))
        case Cmd.While(cond, body) => Cmd.While(cond, qualifyBody(body, alias, declaredNames))
        case Cmd.Return(expr) => Cmd.Return(qualifyExpr(expr, alias, declaredNames))
        case Cmd.Assign(loc, expr) => Cmd.Assign(loc, qualifyExpr(expr, alias, declaredNames))
        case other => other
    }

    private def qualifyExpr(expr: Expr, alias: String, declaredNames: Set[String]): Expr = expr match {
        case Expr.FnCall(name, args) if declaredNames.contains(name) =>
            Expr.FnCall(s"$alias::$name", args.map(qualifyExpr(_, alias, declaredNames)))
        case Expr.BinaryOp(l, op, r) => 
            Expr.BinaryOp(qualifyExpr(l, alias, declaredNames), op, qualifyExpr(r, alias, declaredNames))
        case other => other
    }

    private def populateStore(params: List[(String, SimpType)], args: List[Expr], callerStore: Store): Store = {
        if params.length != args.length then
            throw RuntimeException(s"Expected ${params.length} arguments, got ${args.length}")
        val localStore = Store()
        params.zip(args).foreach((param, arg) => {
            val (name, expectedType) = param
            expectedType match {
                case SimpType.TypeRef(inner) => {
                    arg match {
                        case Expr.Ref(loc) => {
                            val currentVal = callerStore.load(loc)
                            checkType(currentVal, inner, name)
                            localStore.store(name, Value.RefVal(loc, callerStore))
                        }
                        case _ => throw RuntimeException(s"Expected a variable name for ref parameter '$name', got a value. Tip: Don't use '!' ")   
                    }
                }
                case _ => {
                    val value = evalExpr(arg, callerStore)
                    checkType(value, expectedType, name)
                    localStore.store(name, value)
                }
            }
        })
        localStore
    }
    private def getType(value: Value): SimpType = {
        value match {
            case Value.IntVal(_)  => SimpType.TypeInt
            case Value.StrVal(_)  => SimpType.TypeString
            case Value.BoolVal(_) => SimpType.TypeBool
            case Value.RefVal(loc, refStore) => refStore.load(loc) match {
                case Value.IntVal(_)  => SimpType.TypeInt
                case Value.StrVal(_)  => SimpType.TypeString
                case Value.BoolVal(_) => SimpType.TypeBool
                case Value.StructVal(typeName, _) => SimpType.TypeStruct(typeName)
                case Value.RefVal(_, _) => throw RuntimeException("Nested references are not supported")
                case Value.ArrVal(elements) => {
                    if elements.isEmpty then SimpType.TypeArr(SimpType.TypeInt)
                    else elements.head match {
                        case Value.IntVal(_) => SimpType.TypeArr(SimpType.TypeInt)
                        case Value.StrVal(_) => SimpType.TypeArr(SimpType.TypeString)
                        case Value.BoolVal(_) => SimpType.TypeArr(SimpType.TypeBool)
                        case Value.ArrVal(elements) => {
                            if elements.isEmpty then SimpType.TypeArr(SimpType.TypeInt)
                            else elements.head match {
                                case Value.IntVal(_) => SimpType.TypeArr(SimpType.TypeInt)
                                case Value.StrVal(_) => SimpType.TypeArr(SimpType.TypeString)
                                case Value.BoolVal(_) => SimpType.TypeArr(SimpType.TypeBool)
                                case Value.StructVal(typeName, _) => SimpType.TypeArr(SimpType.TypeStruct(typeName))
                                case Value.ArrVal(e) => SimpType.TypeArr(getType(e.head))
                                case _ => throw RuntimeException("Type resolving failed")
                            }
                        }
                    }
                }
            }
            case Value.ArrVal(elements) => {
                if elements.isEmpty then SimpType.TypeArr(SimpType.TypeInt)
                else elements.head match {
                    case Value.IntVal(_) => SimpType.TypeArr(SimpType.TypeInt)
                    case Value.StrVal(_) => SimpType.TypeArr(SimpType.TypeString)
                    case Value.BoolVal(_) => SimpType.TypeArr(SimpType.TypeBool)
                    case Value.StructVal(typeName, _) => SimpType.TypeArr(SimpType.TypeStruct(typeName))
                    case Value.ArrVal(e) => SimpType.TypeArr(getType(e.head))
                    case _ => throw RuntimeException("Type resolving failed")
                }
            }
            case Value.StructVal(typeName, _) => SimpType.TypeStruct(typeName)
        }
    }
    private def checkType(value: Value, expected: SimpType, name: String): Unit = {
        val actual = getType(value);
        if actual != expected then {
            throw RuntimeException(s"Type mismatch for '$name': expected $expected, got $actual")
        }
    }
    
        

    private def evalBool(boolExpr: BoolExpr, store: Store): Boolean = {
        boolExpr match {
            case BoolExpr.Literal(b) => b 
            case BoolExpr.Not(inner) => {
                val result = evalBool(inner, store)
                !result
            }
            case BoolExpr.And(l, r) => {
                val left = evalBool(l, store)
                if !left then return false
                val right = evalBool(r, store)
                left && right
            }
            case BoolExpr.Or(l, r) => {
                val left = evalBool(l, store)
                if left then return true
                val right = evalBool(r, store)
                left || right
            }
            case BoolExpr.FromExpr(expr) => {
                evalExpr(expr, store) match {
                    case Value.BoolVal(b) => b
                    case x => throw RuntimeException(s"Expected boolean value, got '$x'")
                }
            }
            case BoolExpr.Compare(l,bop,r) => {
                (evalExpr(l, store), evalExpr(r, store)) match {
                    case (Value.IntVal(left), Value.IntVal(right)) => {
                        bop match {
                            case Bop.Gt => left > right
                            case Bop.Gte => left >= right 
                            case Bop.Lt => left < right 
                            case Bop.Lte => left <= right
                            case Bop.Eq => left == right 
                            case Bop.Neq => left != right 
                        }
                    }
                    case (Value.StrVal(left), Value.StrVal(right)) => {
                        bop match {
                            case Bop.Eq => left == right 
                            case Bop.Neq => left != right 
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case (Value.BoolVal(left), Value.BoolVal(right)) => {
                        bop match {
                            case Bop.Eq => left == right 
                            case Bop.Neq => left != right 
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case _ => throw RuntimeException(s"Type Mismatch")
                }
            }
        }
    }
    
    private def evalExpr(expr: Expr, store: Store): Value = {
        expr match {
            case Expr.Num(n) => Value.IntVal(n)
            case Expr.Deref(loc) => {
                store.load(loc) match {
                    case Value.RefVal(refLoc, refStore) => refStore.load(refLoc)
                    case v => v
                }
            }
            case Expr.Str(s) => Value.StrVal(s)
            case Expr.Bool(b) => Value.BoolVal(b)
            case Expr.BoolLift(b) => Value.BoolVal(evalBool(b, store))
            case Expr.Ref(loc) => store.load(loc)
            case Expr.ArrLiteral(elements) => {
                val evaluated = elements.map(evalExpr(_, store))
                Value.ArrVal(scala.collection.mutable.ArrayBuffer(evaluated*))
            }
            case Expr.ArrIndex(arr, idx) => {
                val arrVal = evalExpr(arr, store)
                val index = evalExpr(idx, store)
                (arrVal, index) match {
                    case (Value.ArrVal(elements), Value.IntVal(i)) => {
                        if i < 0 || i >= elements.length then {
                            throw RuntimeException(s"Index $i out of bounds for array of length ${elements.length}")
                        } else {
                            elements(i)
                        }
                    }
                    case _ => throw RuntimeException("Expected array and integer index")
                }
            }
            case Expr.BinaryOp(l, op, r) => {
                (evalExpr(l, store), evalExpr(r, store)) match {
                    case (Value.IntVal(left), Value.IntVal(right)) => {
                        op match {
                            case Op.Add => Value.IntVal(left + right)
                            case Op.Sub => Value.IntVal(left - right)
                            case Op.Mul => Value.IntVal(left * right)
                            case Op.Mod => Value.IntVal(left % right)
                            case Op.Div if right == 0 => throw RuntimeException(s"Division by Zero!")
                            case Op.Div => Value.IntVal(left / right)
                        }
                    }
                    case (Value.StrVal(left), Value.StrVal(right)) => {
                        op match {
                            case Op.Add => Value.StrVal(left+right)
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case (Value.StrVal(left), Value.IntVal(right)) => {
                        op match {
                            case Op.Add => Value.StrVal(left + (right.toString))
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case (Value.StrVal(left), Value.BoolVal(right)) => {
                        op match {
                            case Op.Add if right==true => Value.StrVal(left + "true")
                            case Op.Add if right==false => Value.StrVal(left + "false")
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case _ => throw RuntimeException(s"Type mismatch in binary operation")
                }
            }
            case Expr.StructLiteral(typeName, fields) => {
                val defn = structEnv.lookup(typeName)
                val fieldMap = scala.collection.mutable.Map[String, Value]()

                defn.fields.foreach((name, expectedType) => {
                    val fieldExpr = fields.find(_._1 == name).getOrElse(
                        throw RuntimeException(s"Missing field '$name' in $typeName literal")
                    )
                    val value = evalExpr(fieldExpr._2, store)
                    checkType(value, expectedType, name)
                    fieldMap(name) = value
                })
                fields.foreach((name, _) => {
                    if !defn.fields.exists(_._1 == name) then {
                        throw RuntimeException(s"Unknown field '$name' in $typeName literal")
                    }
                })
                Value.StructVal(typeName, fieldMap)
            }
            case Expr.FieldAccess(expr, field) => {
                evalExpr(expr, store) match {
                    case Value.StructVal(_, fields) => {
                        fields.getOrElse(field, throw RuntimeException(s"Unknown field '$field'"))
                    }
                    case _ => throw RuntimeException("Field access on non-struct value")
                }
            }
            case Expr.FnCall(name, args) => {
                val evaluatedArgs = args.map(evalExpr(_, store))
                fnEnv.lookupBuiltin(name) match {
                    case Some(fn) => fn(evaluatedArgs)
                    case None => {
                        val function = fnEnv.lookupFn(name)
                        val localStore = populateStore(function.params, args, store)
                        try {
                            execCmd(function.body, localStore)
                            throw RuntimeException(s"Function '$name' has no return statement")
                        } catch {
                            case ReturnException(value) => {
                                checkType(value, function.returnType, s"return value of '$name'")
                                value
                            }
                        }
                    }
                }
            }
        }
    }

    private def getPrettyPrint(value: Value): String = {
        value match {
            case Value.StrVal(s) => s
            case Value.IntVal(n) => n.toString
            case Value.BoolVal(b) => b.toString
            case Value.RefVal(name,_) => s"Ref($name)"
            case Value.StructVal(typeName, fields) => s"$typeName { ${fields.map((k,v) => s"$k: ${getPrettyPrint(v)}").mkString(", ")} }"
            case Value.ArrVal(elements) => "[" + elements.map(v => getPrettyPrint(v)).mkString(", ") + "]"
        }
    }

    private def execCmd(cmd: Cmd, store: Store): Unit = {
        cmd match {
            case Cmd.Skip => 
            case Cmd.Assign(loc, expr) => {
                val value = evalExpr(expr, store)
                try {
                    store.load(loc) match {
                        case Value.RefVal(refLoc, refStore) => {
                            refStore.store(refLoc, value)
                        }
                        case _ => store.store(loc, value)
                    }
                } catch {
                    case _: RuntimeException => store.store(loc, value)
                }
            }
            case Cmd.FieldAssign(loc, field, valueExpr) => {
                store.load(loc) match {
                    case Value.StructVal(typeName, fields) => {
                        val defn = structEnv.lookup(typeName)
                        val expectedType = defn.fields.find(_._1 == field).getOrElse(
                            throw RuntimeException(s"Unknown field '$field'")
                        )._2
                        val value = evalExpr(valueExpr, store)
                        checkType(value, expectedType, field)
                        fields(field) = value
                    }
                    case _ => throw RuntimeException(s"'$loc' is not a struct")
                }
            }
            case Cmd.Seq(fst, snd) => {
                execCmd(fst, store)
                execCmd(snd, store)
            }
            case Cmd.If(cond, t, e) => {
                val condition = evalBool(cond, store)
                if condition then {
                    execCmd(t, store)
                } else {
                    execCmd(e, store)
                }
            }
            case Cmd.While(cond, body) => {
                var running = true

                while running && evalBool(cond, store) do {
                    try {
                        execCmd(body, store)
                    } catch {
                        case _: BreakException => running = false
                        case _: ContinueException =>
                    }
                }
            }
            case Cmd.For(variable, iterable, body) => {
                val arr = evalExpr(iterable, store)
                arr match {
                    case Value.ArrVal(elements) => {
                        elements.foreach(elem => {
                            val loopStore = Store()
                            store.entries().foreach((k, v) => loopStore.store(k, v))
                            loopStore.store(variable, elem)
                            var toBreak = false
                            try {
                                execCmd(body, loopStore)
                            }catch {
                                case _: BreakException => toBreak = true
                                case _: ContinueException =>
                            }
                            if toBreak != true then {
                                loopStore.entries().foreach((k, v) =>
                                    if k != variable then store.store(k, v)
                                )
                            }
                        })
                    }
                    case _ => throw RuntimeException("for loop expects an array")
                }
            }
            case Cmd.Print(value) => {
                println(getPrettyPrint(evalExpr(value, store)))
            }
            case Cmd.PdCall(name, args) => {
                val function = fnEnv.lookupPd(name)
                val localStore = populateStore(function.params, args, store)
                execCmd(function.body, localStore)
            }
            case Cmd.Return(expr) => throw ReturnException(evalExpr(expr, store))
            case Cmd.Continue => throw ContinueException()
            case Cmd.Break => throw BreakException()

            case Cmd.ArrAssign(loc, idx, value) => {
                val arrVal = store.load(loc)
                val index = evalExpr(idx, store)
                val v = evalExpr(value, store)
                (arrVal, index) match {
                    case (Value.ArrVal(elements), Value.IntVal(i)) => {
                        if i < 0 || i >= elements.length then {
                            throw RuntimeException(s"Index $i out of bounds for array of length ${elements.length}")
                        } else {
                            elements(i) = v
                        }
                    }
                    case _ => throw RuntimeException("Expected array and integer index")
                }
            }
        }
    }
