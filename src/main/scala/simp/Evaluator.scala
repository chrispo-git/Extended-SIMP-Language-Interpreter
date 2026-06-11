package simp
import java.io.File
import SimpUtils.*

class Evaluator(fnEnv: FunctionEnv, structEnv: StructEnv, cwd: String = "."):
    private val importedFiles = scala.collection.mutable.Set[String]()
    private val completedImports = scala.collection.mutable.Map[String, Set[String]]()


    def evalProgram(program: List[Program], store: Store): Unit = {
        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body, returnType)) => fnEnv.registerFn(name, Decl.FnDecl(name, params, body, returnType))
            case Program.PDecl(Decl.ImportDecl(path, alias)) => processImport(path, alias, cwd, store)
            case Program.PDecl(Decl.StructDecl(name, fields)) => structEnv.register(name, StructDef(fields))
            case Program.PCmd(cmd) => execCmd(cmd, store)
            case Program.PExpr(expr) => println(getPrettyPrint(evalExpr(expr, store)))
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
        val program = Parser(tokens._1, importStructEnv, tokens._2).parseProgram()


        val declaredNames = program.collect {
            case Program.PDecl(Decl.FnDecl(name, _, _, _)) => name
        }.toSet

        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body, returnType)) => {
                val qualifiedBody = qualifyBody(body, alias, declaredNames)
                fnEnv.registerFn(s"$alias::$name", Decl.FnDecl(s"$alias::$name", params, qualifiedBody, returnType))
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
        case Cmd.Return(Some(expr)) => Cmd.Return(Some(qualifyExpr(expr, alias, declaredNames)))
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
                val v = evalExpr(expr, store)
                v match {
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
                    case (Value.FloatVal(left), Value.FloatVal(right)) => {
                        bop match {
                            case Bop.Gt => left > right
                            case Bop.Gte => left >= right 
                            case Bop.Lt => left < right 
                            case Bop.Lte => left <= right
                            case Bop.Eq => left == right 
                            case Bop.Neq => left != right 
                        }
                    }
                    case (Value.IntVal(left), Value.FloatVal(right)) => {
                        bop match {
                            case Bop.Gt => left > right
                            case Bop.Gte => left >= right 
                            case Bop.Lt => left < right 
                            case Bop.Lte => left <= right
                            case Bop.Eq => left == right 
                            case Bop.Neq => left != right 
                        }
                    }
                    case (Value.FloatVal(left), Value.IntVal(right)) => {
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
                    case (Value.ArrVal(left), Value.ArrVal(right)) => {
                        bop match {
                            case Bop.Eq => left == right 
                            case Bop.Neq => left != right 
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case (Value.StructVal(t1, f1), Value.StructVal(t2, f2)) => {
                        bop match {
                            case Bop.Eq => t1 == t2 && structsEqual(f1, f2)
                            case Bop.Neq => t1 != t2 || !structsEqual(f1, f2)
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case _ => throw RuntimeException(s"Type Mismatch")
                }
            }
        }
    }
    private def structsEqual(f1: scala.collection.mutable.Map[String, Value], f2: scala.collection.mutable.Map[String, Value], visited: Set[(AnyRef, AnyRef)] = Set()): Boolean = {
        val id1 = System.identityHashCode(f1)
        val id2 = System.identityHashCode(f2)
        val pair = if id1 <= id2 then (f1, f2) else (f2, f1)
        if visited.contains(pair) then return true
        if f1.keySet != f2.keySet then return false
        val newVisited = visited + pair
        f1.forall((k, v1) => 
            f2.get(k).exists(v2 => valuesEqual(v1, v2, newVisited))
        )
    }

    private def valuesEqual(v1: Value, v2: Value, visited: Set[(AnyRef, AnyRef)]): Boolean = (v1, v2) match {
        case (Value.IntVal(a), Value.IntVal(b))   => a == b
        case (Value.FloatVal(a), Value.FloatVal(b))   => a == b
        case (Value.StrVal(a), Value.StrVal(b))   => a == b
        case (Value.BoolVal(a), Value.BoolVal(b)) => a == b
        case (Value.NullVal, Value.NullVal)        => true
        case (Value.ArrVal(a), Value.ArrVal(b))   => a.length == b.length && a.zip(b).forall((x, y) => valuesEqual(x, y, visited))
        case (Value.StructVal(t1, f1), Value.StructVal(t2, f2)) => t1 == t2 && structsEqual(f1, f2, visited)
        case _ => false
    }
    private def matchPattern(pattern: Pattern, value: Value, store: Store): Option[Map[String, Value]] = {
        pattern match {
            case Pattern.PWild => Some(Map())

            case Pattern.PLit(expr) => {
                val lit = evalExpr(expr, store)
                if lit == value then {
                    Some(Map())
                } else {
                    None
                }
            }

            case Pattern.PVar(name) => Some(Map(name -> value))

            case Pattern.PPair(fstPat, sndPat) => {
                value match {
                    case Value.PairVal(fst, snd) => {
                        for
                            fstBindings <- matchPattern(fstPat, fst, store)
                            sndBindings <- matchPattern(sndPat, snd, store)
                        yield fstBindings ++ sndBindings
                    }
                    case _ => None
                }
            }

            case Pattern.PStruct(typeName, fieldPats) => {
                value match {
                    case Value.StructVal(vTypeName, fields) if vTypeName == typeName => {
                        val bindings = scala.collection.mutable.Map[String, Value]()
                        val allMatch = fieldPats.forall((fieldName, fieldPat) =>
                            fields.get(fieldName) match {
                                case None => false
                                case Some(fieldVal) =>
                                    matchPattern(fieldPat, fieldVal, store) match {
                                        case None => false
                                        case Some(b) => bindings ++= b; true
                                    }
                            }
                        )
                        if allMatch then Some(bindings.toMap) else None
                    }
                    case _ => None
                }
            }
        }
    }
    private def evalExpr(expr: Expr, store: Store): Value = {
        expr match {
            case Expr.Num(n) => Value.IntVal(n)
            case Expr.Flt(n) => Value.FloatVal(n)
            case Expr.TypeLiteral(t) => Value.TypeVal(t)
            case Expr.Deref(loc) => {
                store.load(loc) match {
                    case Value.RefVal(refLoc, refStore) => refStore.load(refLoc)
                    case v => v
                }
            }
            case Expr.Block(cmds, result) => {
                cmds.foreach(cmd => execCmd(cmd, store))
                evalExpr(result, store)
            }
            case Expr.Match(expr, arms) => {
                val value = evalExpr(expr, store)
                val matched = arms.find(arm =>
                    matchPattern(arm.pattern, value, store) match {
                        case Some(bindings) => {
                            arm.guard match {
                                case None => true
                                case Some(guard) => {
                                    val guardStore = Store()
                                    store.entries().foreach((k,v) => guardStore.store(k,v))
                                    bindings.foreach((k,v) => guardStore.store(k,v))
                                    evalBool(BoolExpr.FromExpr(guard), guardStore)
                                }
                            }
                        }
                        case None => false
                    }
                )
                matched match {
                    case None => throw RuntimeException("No matching pattern found, pattern non-exhaustive!")
                    case Some(arm) => {
                        val matchStore = Store()
                        store.entries().foreach((k, v) => matchStore.store(k, v))
                        matchPattern(arm.pattern, value, store).get.foreach((k, v) => matchStore.store(k, v))
                        evalExpr(arm.body, matchStore)
                    }
                }
            }
            case Expr.Null => Value.NullVal
            case Expr.Str(s) => Value.StrVal(s)
            case Expr.Bool(b) => Value.BoolVal(b)
            case Expr.BoolLift(b) => Value.BoolVal(evalBool(b, store))
            case Expr.Ref(loc) => {
                store.load(loc)
            }
            case Expr.ArrLiteral(elements) => {
                val evaluated = elements.map(evalExpr(_, store))
                Value.ArrVal(scala.collection.mutable.ArrayBuffer(evaluated*))
            }
            case Expr.Pair(l, r) => {
                val fst = evalExpr(l, store);
                val snd = evalExpr(r, store);
                Value.PairVal(fst, snd)
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
            case Expr.UnaryOp(l, op) => {
                evalExpr(l, store) match {
                    case Value.IntVal(left) => {
                        op match {
                            case Op.BitComplement => Value.IntVal(~left)
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case _ => throw RuntimeException(s"Type mismatch in unary operation")
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
                            case Op.BitAnd => Value.IntVal(left & right)
                            case Op.BitOr => Value.IntVal(left | right)
                            case Op.BitXor => Value.IntVal(left ^ right)
                            case Op.BitLeft => Value.IntVal(left << right)
                            case Op.BitRight => Value.IntVal(left >> right)
                            case Op.BitRightFill => Value.IntVal(left >>> right)
                            case x => throw RuntimeException(s"Unsupported operation '$x'") 
                        }
                    }
                    case (Value.IntVal(left), Value.FloatVal(right)) => {
                        op match {
                            case Op.Add => Value.FloatVal(left + right)
                            case Op.Sub => Value.FloatVal(left - right)
                            case Op.Mul => Value.FloatVal(left * right)
                            case Op.Div if right == 0 => throw RuntimeException(s"Division by Zero!")
                            case Op.Div => Value.FloatVal(left / right)
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case (Value.FloatVal(left), Value.IntVal(right)) => {
                        op match {
                            case Op.Add => Value.FloatVal(left + right)
                            case Op.Sub => Value.FloatVal(left - right)
                            case Op.Mul => Value.FloatVal(left * right)
                            case Op.Div if right == 0 => throw RuntimeException(s"Division by Zero!")
                            case Op.Div => Value.FloatVal(left / right)
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case (Value.FloatVal(left), Value.FloatVal(right)) => {
                        op match {
                            case Op.Add => Value.FloatVal(left + right)
                            case Op.Sub => Value.FloatVal(left - right)
                            case Op.Mul => Value.FloatVal(left * right)
                            case Op.Div if right == 0 => throw RuntimeException(s"Division by Zero!")
                            case Op.Div => Value.FloatVal(left / right)
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case (Value.StrVal(left),right) => {
                        op match {
                            case Op.Add => Value.StrVal(left + getPrettyPrint(right))
                            case x => throw RuntimeException(s"Unsupported operation '$x'")
                        }
                    }
                    case _ => throw RuntimeException(s"Type mismatch in binary operation")
                }
            }
            case Expr.StructLiteral(typeName, fields) => {
                val defn = structEnv.lookup(typeName)
                val fieldMap = scala.collection.mutable.Map[String, Value]()
                defn.fields.foreach((name, expectedType, default) => {
                    val fieldExpr = fields.find(_._1 == name)
                    val value = fieldExpr match {
                        case Some((_, expr)) => evalExpr(expr, store)
                        case None => default match {
                            case Some(expr) => evalExpr(expr, store)
                            case None => throw RuntimeException(s"Missing field '$name' in $typeName literal and no default value provided")
                        }
                    }
                    checkType(value, expectedType, name)
                    fieldMap(name) = value
                })
                Value.StructVal(typeName, fieldMap)
            }
            case Expr.FieldAccess(expr, field) => {
                evalExpr(expr, store) match {
                    case Value.PairVal(fst, snd) => field match {
                        case "fst" => fst
                        case "snd" => snd
                        case _ => throw RuntimeException(s"Pairs only have 'fst' and 'snd' fields")
                    }
                    case Value.StructVal(_, fields) => {
                        fields.getOrElse(field, throw RuntimeException(s"Unknown field '$field'"))
                    }
                    case _ => throw RuntimeException("Field access on non-struct or pair value")
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
                            if function.returnType != SimpType.TypeNull then {
                                throw RuntimeException(s"Function '$name' has no return statement")
                            } else {
                                Value.NullVal
                            }
                        } catch {
                            case ReturnException(Some(value)) => {
                                if function.returnType != SimpType.TypeNull then {
                                    checkType(value, function.returnType, s"return value of '$name'")
                                    value
                                } else {
                                    throw RuntimeException(s"Function '$name' has invalid return statement")
                                }
                            }
                            case ReturnException(None) => {
                                if function.returnType == SimpType.TypeNull then {
                                    Value.NullVal
                                } else {
                                    throw RuntimeException(s"Function '$name' has invalid return statement")
                                }
                            }
                        }
                    }
                }
            }
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
            case Cmd.FieldIndexAssign(loc, field, index, valueExpr) => {
                store.load(loc) match {
                    case Value.StructVal(_, fields) => {
                        fields.get(field) match {
                            case Some(Value.ArrVal(elements)) => {
                                val idx = evalExpr(index, store) match {
                                    case Value.IntVal(i) => i
                                    case _ => throw RuntimeException("Array index must be an integer")
                                }
                                if idx < 0 || idx >= elements.length then
                                    throw RuntimeException(s"Index $idx out of bounds")
                                elements(idx) = evalExpr(valueExpr, store)
                            }
                            case _ => throw RuntimeException(s"'$field' is not an array")
                        }
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
            case Cmd.Return(None) => throw ReturnException(None)
            case Cmd.Return(Some(expr)) => throw ReturnException(Some(evalExpr(expr, store)))
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
            case Cmd.ArrAssignNested(loc, indices, value) => {
                val v = evalExpr(value, store)
                var current = store.load(loc) match {
                    case Value.ArrVal(elements) => elements
                    case _ => throw RuntimeException(s"'$loc' is not an array")
                }
                var i = 0
                while i < indices.length - 1 do {
                    val idx = evalExpr(indices(i), store) match {
                        case Value.IntVal(n) => n
                        case _ => throw RuntimeException("Array index must be an integer")
                    }
                    current = current(idx) match {
                        case Value.ArrVal(elements) => elements
                        case _ => throw RuntimeException(s"Not an array at index $idx")
                    }
                    i += 1
                }
                val lastIdx = evalExpr(indices.last, store) match {
                    case Value.IntVal(n) => n
                    case _ => throw RuntimeException("Array index must be an integer")
                }
                current(lastIdx) = v
            }
            case Cmd.FieldIndexAssignNested(loc, field, indices, value) => {
                val v = evalExpr(value, store)
                val struct = store.load(loc) match {
                    case Value.StructVal(_, fields) => fields
                    case _ => throw RuntimeException(s"[Error] '$loc' is not a struct")
                }
                var current = struct(field) match {
                    case Value.ArrVal(elements) => elements
                    case _ => throw RuntimeException(s"[Error] '$loc.$field' is not an array")
                }
                var i = 0
                while i < indices.length - 1 do {
                    val idx = evalExpr(indices(i), store) match {
                        case Value.IntVal(n) => n
                        case _ => throw RuntimeException("[Error] Array index must be an integer")
                    }
                    current = current(idx) match {
                        case Value.ArrVal(elements) => elements
                        case _ => throw RuntimeException(s"[Error] Not an array at index $idx")
                    }
                    i += 1
                }
                val lastIdx = evalExpr(indices.last, store) match {
                    case Value.IntVal(n) => n
                    case _ => throw RuntimeException("[Error] Array index must be an integer")
                }
                current(lastIdx) = v
            }
        }
    }
