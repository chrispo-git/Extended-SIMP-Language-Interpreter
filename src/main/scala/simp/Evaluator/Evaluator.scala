package simp
import java.io.File
import SimpUtils.*

class Evaluator(fnEnv: FunctionEnv, structEnv: StructEnv, sourceLines: List[String], cwd: String = "."):
    private val importedFiles = scala.collection.mutable.Set[String]()
    private val completedImports = scala.collection.mutable.Map[String, Set[String]]()


    private var pos: Int = 0

    private def currentLine(): Int = pos

    def evalProgram(program: List[Program], store: Store): Unit = {
        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body, returnType)) => fnEnv.registerFn(name, Decl.FnDecl(name, params, body, returnType))
            case Program.PDecl(Decl.ImportDecl(path, alias)) => processImport(path, alias, cwd, store)
            case Program.PDecl(Decl.StructDecl(name, fields)) => structEnv.register(name, StructDef(fields))
            case Program.PCmd(cmd) => execCmd(cmd, store)
            case Program.PExpr(expr) => println(getPrettyPrint(evalExpr(expr, store)))
            case Program.PBool(b) => println(evalBool(b, store))
            case Program.PImpl(structName, methods) => methods.foreach(m => fnEnv.methodTable((structName, m.name)) = m)
        })
    }
    private def currentLineSource(): String = sourceLines(currentLine()-1).trim
    private def throwError(msg: String): Nothing = {
        throw RuntimeException(s"on line ${currentLine()}\n${currentLineSource()}\n\u001b[31m$msg\u001b[0m")
    }

    private def processImport(path: String, alias: String, currentDir: String, store: Store ):  Unit = {
        val fullPath = java.io.File(s"$currentDir/$path").getCanonicalPath


        if importedFiles.contains(fullPath) then throwError(s"Circular import detected: $path")


        val existingAliases = completedImports.getOrElse(fullPath, Set())
        if existingAliases.contains(alias) then return
            
        importedFiles += fullPath

        val source = try {
            scala.io.Source.fromFile(fullPath).mkString
        } catch case _ => throwError(s"Could not find import $path")

        val newSource = source.split('\n').toList
        val tokens = Lexer(source, newSource).tokenise()
        val importStructEnv = StructEnv()
        val program = Parser(tokens._1, importStructEnv, tokens._2, newSource).parseProgram()


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
            case _ => throwError(s"Imports can only contain declarations")
        })

        importedFiles -= fullPath 
        completedImports(fullPath) = existingAliases + alias
    }


    private def qualifyBody(cmd: Cmd, alias: String, declaredNames: Set[String]): Cmd = cmd match {
        case Cmd.Seq(a, b) => Cmd.Seq(qualifyBody(a, alias, declaredNames), qualifyBody(b, alias, declaredNames))
        case Cmd.If(cond, t, e, line) => Cmd.If(cond, qualifyBody(t, alias, declaredNames), qualifyBody(e, alias, declaredNames), line)
        case Cmd.While(cond, body, line) => Cmd.While(cond, qualifyBody(body, alias, declaredNames), line)
        case Cmd.Return(Some(expr), line) => Cmd.Return(Some(qualifyExpr(expr, alias, declaredNames)), line)
        case Cmd.Assign(loc, expr, line) => Cmd.Assign(loc, qualifyExpr(expr, alias, declaredNames), line)
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
            throwError(s"Expected ${params.length} arguments, got ${args.length}")
        val localStore = Store()
        params.zip(args).foreach((param, arg) => {
            val (name, expectedType) = param
            expectedType match {
                case SimpType.TypeRef(inner) => {
                    arg match {
                        case Expr.Ref(loc) => {
                            try {
                                val currentVal = callerStore.load(loc)
                                checkType(currentVal, inner, name)
                                localStore.store(name, Value.RefVal(loc, callerStore))
                            } catch case e : RuntimeException => {
                                throwError(s"${e.getMessage}")
                            }
                        }
                        case _ => throwError(s"Expected a variable name for ref parameter '$name', got a value. Tip: Don't use '!' ")   
                    }
                }
                case _ => {
                    val value = evalExpr(arg, callerStore)
                    checkType(value, expectedType, name)
                    try {
                        localStore.store(name, value)
                    } catch case e : RuntimeException => {
                        throwError(s"${e.getMessage}")
                    }
                }
            }
        })
        localStore
    }
    

    private def populateStoreFromValues(params: List[(String, SimpType)], argVals: List[Value], callerStore: Store): Store = {
        if params.length != argVals.length then
            throwError(s"Expected ${params.length} arguments, got ${argVals.length}")
        val localStore = Store()
        params.zip(argVals).foreach((param, value) => {
            val (name, expectedType) = param
            expectedType match {
                case SimpType.TypeRef(_) =>
                    throwError(s"Method parameter '$name' cannot be a reference type")
                case _ => {
                    checkType(value, expectedType, name)
                    try {
                        localStore.store(name, value)
                    } catch case e : RuntimeException => {
                        throwError(s"${e.getMessage}")
                    }
                }
            }
        })
        localStore
    }
    private def compare(left: Int | Double, right: Int | Double): Int = {
        (left, right) match {
            case (l: Int, r: Int)       => l.compareTo(r)
            case (l: Double, r: Double) => l.compareTo(r)
            case (l: Int, r: Double)    => l.toDouble.compareTo(r)
            case (l: Double, r: Int)    => l.compareTo(r.toDouble)
        }
    }

    private def singleCompare(left: Int | Double, bop: Bop, right: Int | Double): Boolean = {
        bop match {
            case Bop.Gt => compare(left, right) > 0
            case Bop.Gte => compare(left, right) >= 0 
            case Bop.Lt => compare(left, right) < 0 
            case Bop.Lte => compare(left, right) <= 0
            case Bop.Eq => compare(left, right) == 0 
            case Bop.Neq => compare(left, right) != 0 
        }
    }
    private def evalCompare(l: Expr, bop: Bop, r: Expr, store: Store): Boolean = {
        (evalExpr(l, store), evalExpr(r, store)) match {
            case (Value.IntVal(left), Value.IntVal(right)) => singleCompare(left, bop, right)
            case (Value.FloatVal(left), Value.FloatVal(right))  => singleCompare(left, bop, right)
            case (Value.IntVal(left), Value.FloatVal(right))  => singleCompare(left, bop, right)
            case (Value.FloatVal(left), Value.IntVal(right))  => singleCompare(left, bop, right)
            case (Value.StrVal(left), Value.StrVal(right)) => {
                bop match {
                    case Bop.Eq => left == right 
                    case Bop.Neq => left != right 
                    case x => throwError(s"Unsupported operation '$x'")
                }
            }
            case (Value.BoolVal(left), Value.BoolVal(right)) => {
                bop match {
                    case Bop.Eq => left == right 
                    case Bop.Neq => left != right 
                    case x => throwError(s"Unsupported operation '$x'")
                }
            }
            case (Value.ArrVal(left), Value.ArrVal(right)) => {
                bop match {
                    case Bop.Eq => left == right 
                    case Bop.Neq => left != right 
                    case x => throwError(s"Unsupported operation '$x'")
                }
            }
            case (Value.StructVal(t1, f1), Value.StructVal(t2, f2)) => {
                bop match {
                    case Bop.Eq => t1 == t2 && structsEqual(f1, f2)
                    case Bop.Neq => t1 != t2 || !structsEqual(f1, f2)
                    case x => throwError(s"Unsupported operation '$x'")
                }
            }
            case _ => throwError(s"Type Mismatch")
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
                val v = evalExpr(expr, store)
                v match {
                    case Value.BoolVal(b) => b
                    case x => throwError(s"Expected boolean value, got '$x'")
                }
            }
            case BoolExpr.Compare(l,bop,r) => evalCompare(l, bop, r, store)
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
    private def evalDeref(loc: String, store: Store): Value = {
        try {
            store.load(loc) match {
                case Value.RefVal(refLoc, refStore) => refStore.load(refLoc)
                case v => v
            }
        } catch case e : RuntimeException => {
            throwError(s"${e.getMessage}")
        }
    }
    private def evalBlock(cmds: List[Cmd], result: Expr, store: Store): Value = {
        val childStore = store.child()
        cmds.foreach(cmd => execCmd(cmd, childStore))
        evalExpr(result, childStore)
    }
    private def evalMatch(expr: Expr, arms: List[MatchArm], store: Store): Value = {
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
            case None => throwError("No matching pattern found, pattern non-exhaustive!")
            case Some(arm) => {
                val matchStore = Store()
                store.entries().foreach((k, v) => matchStore.store(k, v))
                matchPattern(arm.pattern, value, store).get.foreach((k, v) => matchStore.store(k, v))
                evalExpr(arm.body, matchStore)
            }
        }
    }
    private def evalRef(loc: String, store: Store): Value = {
        try {
            store.load(loc)
        } catch case e : RuntimeException => {
            throwError(s"${e.getMessage}")
        }
    }
    private def evalArrIndex(arr: Expr, idx: Expr, store: Store): Value = {
        val arrVal = evalExpr(arr, store)
        val index = evalExpr(idx, store)
        (arrVal, index) match {
            case (Value.ArrVal(elements), Value.IntVal(i)) => {
                if i < 0 || i >= elements.length then {
                    throwError(s"Index $i out of bounds for array of length ${elements.length}")
                } else {
                    elements(i)
                }
            }
            case _ => throwError("Expected array and integer index")
        }
    }
    private def evalUnaryOp(l: Expr, op: Op, store: Store): Value = {
        evalExpr(l, store) match {
            case Value.IntVal(left) => {
                op match {
                    case Op.BitComplement => Value.IntVal(~left)
                    case x => throwError(s"Unsupported operation '$x'")
                }
            }
            case _ => throwError(s"Type mismatch in unary operation")
        }
    }
    private def evalBinarySingleNormal(l: Int | Double, op: Op, r: Int | Double): Value = {
        val left = l match { case i: Int => i.toDouble; case d: Double => d }
        val right = r match { case i: Int => i.toDouble; case d: Double => d }
        op match {
            case Op.Add => Value.FloatVal(left + right)
            case Op.Sub => Value.FloatVal(left - right)
            case Op.Mul => Value.FloatVal(left * right)
            case Op.Div if right == 0 => throwError(s"Division by Zero!")
            case Op.Div => Value.FloatVal(left / right)
            case x => throwError(s"Unsupported operation '$x'")
        }
    }
    private def evalBinaryOp(l: Expr, op: Op, r: Expr, store: Store): Value = {
        (evalExpr(l, store), evalExpr(r, store)) match {
            case (Value.IntVal(left), Value.IntVal(right)) => {
                op match {
                    case Op.Add => Value.IntVal(left + right)
                    case Op.Sub => Value.IntVal(left - right)
                    case Op.Mul => Value.IntVal(left * right)
                    case Op.Mod => Value.IntVal(left % right)
                    case Op.Div if right == 0 => throwError(s"Division by Zero!")
                    case Op.Div => Value.IntVal(left / right)
                    case Op.BitAnd => Value.IntVal(left & right)
                    case Op.BitOr => Value.IntVal(left | right)
                    case Op.BitXor => Value.IntVal(left ^ right)
                    case Op.BitLeft => Value.IntVal(left << right)
                    case Op.BitRight => Value.IntVal(left >> right)
                    case Op.BitRightFill => Value.IntVal(left >>> right)
                    case x => throwError(s"Unsupported operation '$x'") 
                }
            }
            case (Value.IntVal(left), Value.FloatVal(right)) => evalBinarySingleNormal(left, op, right)
            case (Value.FloatVal(left), Value.IntVal(right)) => evalBinarySingleNormal(left, op, right)
            case (Value.FloatVal(left), Value.FloatVal(right)) => evalBinarySingleNormal(left, op, right)
            case (Value.StrVal(left),right) => {
                op match {
                    case Op.Add => Value.StrVal(left + getPrettyPrint(right))
                    case x => throwError(s"Unsupported operation '$x'")
                }
            }
            case _ => throwError(s"Type mismatch in binary operation")
        }
    }
    private def evalStructLiteral(typeName: String, fields: List[(String, Expr)], store: Store): Value = {
        val defn = structEnv.lookup(typeName)
        val fieldMap = scala.collection.mutable.Map[String, Value]()
        defn.fields.foreach((name, expectedType, default) => {
            val fieldExpr = fields.find(_._1 == name)
            val value = fieldExpr match {
                case Some((_, expr)) => evalExpr(expr, store)
                case None => default match {
                    case Some(expr) => evalExpr(expr, store)
                    case None => throwError(s"Missing field '$name' in $typeName literal and no default value provided")
                }
            }
            checkType(value, expectedType, name)
            fieldMap(name) = value
        })
        Value.StructVal(typeName, fieldMap)
    }
    private def evalFieldAccess(expr: Expr, field: String, store: Store): Value = {
        evalExpr(expr, store) match {
            case Value.PairVal(fst, snd) => field match {
                case "fst" => fst
                case "snd" => snd
                case _ => throwError(s"Pairs only have 'fst' and 'snd' fields")
            }
            case Value.StructVal(_, fields) => {
                fields.getOrElse(field, throwError(s"Unknown field '$field'"))
            }
            case _ => throwError("Field access on non-struct or pair value")
        }
    }
    private def evalMethodCall(receiver: Expr, methodName: String, args: List[Expr], store: Store): Value = {
        val receiverVal = evalExpr(receiver, store)
        val typeName = receiverVal match {
            case Value.StructVal(name, _) => name
            case _ => throwError(s"Can't call method '$methodName' on a non-struct value")
        }
        val fnDecl = fnEnv.methodTable.getOrElse((typeName, methodName), throwError(s"No method '$methodName' found for struct '$typeName'"))
        val argVals = receiverVal :: args.map(evalExpr(_, store))
        callFunctionWithValues(methodName, fnDecl, argVals, store)
    }
    private def evalFnCall(name: String, args: List[Expr], store: Store): Value = {
        val evaluatedArgs = args.map(evalExpr(_, store))
        fnEnv.lookupBuiltin(name) match {
            case Some(fn) => fn(evaluatedArgs)
            case None => {
                val function = fnEnv.lookupFn(name)
                callFunction(name, function, args, store)
            }
        }
    }
    private def evalExpr(expr: Expr, store: Store): Value = {
        expr match {
            case Expr.Num(n) => Value.IntVal(n)
            case Expr.Flt(n) => Value.FloatVal(n)
            case Expr.TypeLiteral(t) => Value.TypeVal(t)
            case Expr.Deref(loc) => evalDeref(loc, store)
            case Expr.Block(cmds, result) => evalBlock(cmds, result, store)
            case Expr.Match(expr, arms) => evalMatch(expr, arms, store)
            case Expr.Null => Value.NullVal
            case Expr.Str(s) => Value.StrVal(s)
            case Expr.Bool(b) => Value.BoolVal(b)
            case Expr.BoolLift(b) => Value.BoolVal(evalBool(b, store))
            case Expr.Ref(loc) => evalRef(loc, store)
            case Expr.ArrLiteral(elements) => {
                val evaluated = elements.map(evalExpr(_, store))
                Value.ArrVal(scala.collection.mutable.ArrayBuffer(evaluated*))
            }
            case Expr.Pair(l, r) => {
                val fst = evalExpr(l, store);
                val snd = evalExpr(r, store);
                Value.PairVal(fst, snd)
            }
            case Expr.ArrIndex(arr, idx) => evalArrIndex(arr, idx, store)
            case Expr.UnaryOp(l, op) => evalUnaryOp(l, op, store)
            case Expr.BinaryOp(l, op, r) => evalBinaryOp(l, op, r, store)
            case Expr.StructLiteral(typeName, fields) => evalStructLiteral(typeName, fields, store)
            case Expr.FieldAccess(expr, field) => evalFieldAccess(expr, field, store)
            case Expr.MethodCall(receiver, methodName, args) => evalMethodCall(receiver, methodName, args, store)
            case Expr.FnCall(name, args) => evalFnCall(name, args, store)
        }
    }

    private def callFunction(name: String, function: Decl.FnDecl, args: List[Expr], store: Store): Value = {
        val localStore = populateStore(function.params, args, store)
        try {
            execCmd(function.body, localStore)
            if function.returnType != SimpType.TypeNull then {
                throwError(s"Function '$name' has no return statement")
            } else {
                Value.NullVal
            }
        } catch {
            case ReturnException(Some(value)) => {
                if function.returnType != SimpType.TypeNull then {
                    checkType(value, function.returnType, s"return value of '$name'")
                    value
                } else {
                    throwError(s"Function '$name' has invalid return statement")
                }
            }
            case ReturnException(None) => {
                if function.returnType == SimpType.TypeNull then {
                    Value.NullVal
                } else {
                    throwError(s"Function '$name' has invalid return statement")
                }
            }
        }
    }
    private def callFunctionWithValues(name: String, function: Decl.FnDecl, argVals: List[Value], store: Store): Value = {
        val localStore = populateStoreFromValues(function.params, argVals, store)
        try {
            execCmd(function.body, localStore)
            if function.returnType != SimpType.TypeNull then {
                throwError(s"Function '$name' has no return statement")
            } else {
                Value.NullVal
            }
        } catch {
            case ReturnException(Some(value)) => {
                if function.returnType != SimpType.TypeNull then {
                    checkType(value, function.returnType, s"return value of '$name'")
                    value
                } else {
                    throwError(s"Function '$name' has invalid return statement")
                }
            }
            case ReturnException(None) => {
                if function.returnType == SimpType.TypeNull then {
                    Value.NullVal
                } else {
                    throwError(s"Function '$name' has invalid return statement")
                }
            }
        }
    }
    private def execAssign(loc: String, expr: Expr, line: Int, store: Store): Unit = {
        pos = line
        val value = evalExpr(expr, store)
        try {
            store.load(loc) match {
                case Value.RefVal(refLoc, refStore) => {
                    refStore.store(refLoc, value)
                }
                case _ => {
                    store.store(loc, value)
                }
            }
        } catch {
            case _: RuntimeException =>  {
                try {
                    store.store(loc, value)
                } catch case e : RuntimeException => {
                    throwError(s"${e.getMessage}")
                }
            }
        }
    }
    private def execConstAssign(loc: String, valueExpr: Expr, line: Int, store: Store): Unit = {
        pos = line
        val value = evalExpr(valueExpr, store)
        store.declareConst(loc, value)
    }
    private def execFieldAssign(loc: String, field: String, valueExpr: Expr, line: Int, store: Store): Unit = {
        pos = line
        try {
            store.load(loc) match {
                case Value.StructVal(typeName, fields) => {
                    val defn = structEnv.lookup(typeName)
                    val expectedType = defn.fields.find(_._1 == field).getOrElse(
                        throwError(s"Unknown field '$field'")
                    )._2
                    val value = evalExpr(valueExpr, store)
                    checkType(value, expectedType, field)
                    fields(field) = value
                }
                case _ => throwError(s"'$loc' is not a struct")
            }
        } catch case e : RuntimeException => {
            throwError(s"${e.getMessage}")
        }
    }
    private def execFieldIndexAssign(loc: String, field: String, index: Expr, valueExpr: Expr, line: Int, store: Store): Unit = {
        pos = line
        try {
            store.load(loc) match {
                case Value.StructVal(_, fields) => {
                    fields.get(field) match {
                        case Some(Value.ArrVal(elements)) => {
                            val idx = evalExpr(index, store) match {
                                case Value.IntVal(i) => i
                                case _ => throwError("Array index must be an integer")
                            }
                            if idx < 0 || idx >= elements.length then
                                throwError(s"Index $idx out of bounds")
                            elements(idx) = evalExpr(valueExpr, store)
                        }
                        case _ => throwError(s"'$field' is not an array")
                    }
                }
                case _ => throwError(s"'$loc' is not a struct")
            }
        } catch case e : RuntimeException => {
            throwError(s"${e.getMessage}")
        }
    }
    private def execIf(cond: BoolExpr, thenBranch: Cmd, elseBranch: Cmd, line: Int, store: Store): Unit = {
        pos = line
        val condition = evalBool(cond, store)
        if condition then {
            execCmd(thenBranch, store.child())
        } else {
            execCmd(elseBranch, store.child())
        }
    }
    private def execWhile(cond: BoolExpr, body: Cmd, line: Int, store: Store): Unit = {
        pos = line
        var running = true

        while running && evalBool(cond, store) do {
            try {
                execCmd(body, store.child())
            } catch {
                case _: BreakException => running = false
                case _: ContinueException =>
            }
        }
    }
    private def execFor(variable: String, iterable: Expr, body: Cmd, line: Int, store: Store): Unit = {
        pos = line
        evalExpr(iterable, store) match {
            case Value.ArrVal(elements) => {
                var i = 0
                var running = true
                while running && i < elements.length do {
                    val childStore = store.child()
                    childStore.declareConst(variable, elements(i))
                    try {
                        execCmd(body, childStore)
                    } catch {
                        case _: BreakException => running = false
                        case _: ContinueException =>
                    }
                    i += 1
                }
            }
            case _ => throwError("for loop expects an array")
        }
    }
    private def execArrAssign(loc: String, idx: Expr, value: Expr, line: Int, store: Store): Unit = {
        pos = line
        try {
            val arrVal = store.load(loc)
            val index = evalExpr(idx, store)
            val v = evalExpr(value, store)
            (arrVal, index) match {
                case (Value.ArrVal(elements), Value.IntVal(i)) => {
                    if i < 0 || i >= elements.length then {
                        throwError(s"Index $i out of bounds for array of length ${elements.length}")
                    } else {
                        elements(i) = v
                    }
                }
                case _ => throwError("Expected array and integer index")
            }
        } catch case e : RuntimeException => {
            throwError(s"${e.getMessage}")
        }
    }
    private def execArrAssignNested(loc: String, indices: List[Expr], value: Expr, line: Int, store: Store): Unit = {
        pos = line
        val v = evalExpr(value, store)
        try {
            var current = store.load(loc) match {
                case Value.ArrVal(elements) => elements
                case _ => throwError(s"'$loc' is not an array")
            }
            var i = 0
            while i < indices.length - 1 do {
                val idx = evalExpr(indices(i), store) match {
                    case Value.IntVal(n) => n
                    case _ => throwError("Array index must be an integer")
                }
                current = current(idx) match {
                    case Value.ArrVal(elements) => elements
                    case _ => throwError(s"Not an array at index $idx")
                }
                i += 1
            }
            val lastIdx = evalExpr(indices.last, store) match {
                case Value.IntVal(n) => n
                case _ => throwError("Array index must be an integer")
            }
            current(lastIdx) = v
        } catch case e : RuntimeException => {
            throwError(s"${e.getMessage}")
        }
    }
    private def execFieldIndexAssignNested(loc: String, field: String, indices: List[Expr], value: Expr, line: Int, store: Store): Unit = {
        pos = line
        val v = evalExpr(value, store)
        try {
            val struct = store.load(loc) match {
                case Value.StructVal(_, fields) => fields
                case _ => throwError(s"[Error] '$loc' is not a struct")
            }
            var current = struct(field) match {
                case Value.ArrVal(elements) => elements
                case _ => throwError(s"[Error] '$loc.$field' is not an array")
            }
            var i = 0
            while i < indices.length - 1 do {
                val idx = evalExpr(indices(i), store) match {
                    case Value.IntVal(n) => n
                    case _ => throwError("[Error] Array index must be an integer")
                }
                current = current(idx) match {
                    case Value.ArrVal(elements) => elements
                    case _ => throwError(s"[Error] Not an array at index $idx")
                }
                i += 1
            }
            val lastIdx = evalExpr(indices.last, store) match {
                case Value.IntVal(n) => n
                case _ => throwError("[Error] Array index must be an integer")
            }
            current(lastIdx) = v
        } catch case e : RuntimeException => {
            throwError(s"${e.getMessage}")
        }
    }
    private def execCmd(cmd: Cmd, store: Store): Unit = {
        cmd match {
            case Cmd.Skip => 
            case Cmd.Scope(body) => execCmd(body, store.child())
            case Cmd.Assign(loc, expr, line) => execAssign(loc, expr, line, store)
            case Cmd.ConstAssign(loc, valueExpr, line) => execConstAssign(loc, valueExpr, line, store)
            case Cmd.FieldAssign(loc, field, valueExpr, line) => execFieldAssign(loc, field, valueExpr, line, store)
            case Cmd.FieldIndexAssign(loc, field, index, valueExpr, line) => execFieldIndexAssign(loc, field, index, valueExpr, line, store)
            case Cmd.Seq(fst, snd) => {
                execCmd(fst, store)
                execCmd(snd, store)
            }
            case Cmd.If(cond, t, e, line) => execIf(cond, t, e, line, store)
            case Cmd.While(cond, body, line) => execWhile(cond, body, line, store)
            case Cmd.For(variable, iterable, body, line) => execFor(variable, iterable, body, line, store)
            case Cmd.Print(value, line) => {
                pos = line
                println(getPrettyPrint(evalExpr(value, store)))
            }
            case Cmd.Return(None, line) => {
                pos = line
                throw ReturnException(None)
            }
            case Cmd.Return(Some(expr), line) => {
                pos = line
                throw ReturnException(Some(evalExpr(expr, store)))
            }
            case Cmd.Continue => throw ContinueException()
            case Cmd.Break => throw BreakException()

            case Cmd.ArrAssign(loc, idx, value, line) => execArrAssign(loc, idx, value, line, store)
            case Cmd.ArrAssignNested(loc, indices, value, line) => execArrAssignNested(loc, indices, value, line, store)
            case Cmd.FieldIndexAssignNested(loc, field, indices, value, line) => execFieldIndexAssignNested(loc, field, indices, value, line, store)
        }
    }
