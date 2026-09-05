package simp
import java.io.File
import SimpUtils.*

trait EvaluatorCmd { self: Evaluator =>
    protected def execAssign(loc: String, expr: Expr, line: Int, store: Store): Unit = {
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
                    throwError(e.getMessage, SimpError.errorTypeOf(e))
                }
            }
        }
    }
    protected def execConstAssign(loc: String, valueExpr: Expr, line: Int, store: Store): Unit = {
        pos = line
        val value = evalExpr(valueExpr, store)
        store.declareConst(loc, value)
    }
    protected def defaultValueFor(t: SimpType, store: Store): Value = t match {
        case SimpType.TypeInt => Value.IntVal(0)
        case SimpType.TypeFloat => Value.FloatVal(0.0)
        case SimpType.TypeString => Value.StrVal("")
        case SimpType.TypeBool => Value.BoolVal(false)
        case SimpType.TypeNull => Value.NullVal
        case SimpType.TypeType => throwError(s"Cannot create a default value for type 'type'")
        case SimpType.TypeArr(inner) => Value.ArrVal(TypedArray(inner))
        case SimpType.TypeMap(keyType, valueType) => Value.MapVal(scala.collection.mutable.Map(), keyType, valueType)
        case SimpType.TypePair(fst, snd) => Value.PairVal(defaultValueFor(fst, store), defaultValueFor(snd, store))
        case SimpType.TypeRef(_) => throwError(s"Cannot create a default value for a reference type")
        case SimpType.TypeParam(name) => currentTypeBindings.get(name) match {
            case Some(concrete) => defaultValueFor(concrete, store)
            case None => throwError(s"Unbound type parameter '$name'", "TypeError")
        }
        case SimpType.TypeStruct(name) => {
            val defn = structEnv.lookup(name)
            val boundTypeArgs = resolveStructTypeArgs(name, defn, List())
            typeBindingStack = (name, boundTypeArgs) :: typeBindingStack
            try {
                val fieldMap = scala.collection.mutable.Map[String, Value]()
                defn.fields.foreach((fname, ftype, fdefault, _) => {
                    fieldMap(fname) = fdefault match {
                        case Some(expr) => evalExpr(expr, store)
                        case None => defaultValueFor(ftype, store)
                    }
                })
                Value.StructVal(name, fieldMap, boundTypeArgs)
            } finally {
                typeBindingStack = typeBindingStack.tail
            }
        }
    }
    protected def execTypeDecl(loc: String, t: SimpType, line: Int, store: Store): Unit = {
        pos = line
        store.store(loc, defaultValueFor(t, store))
    }
    protected def execFieldAssign(loc: String, field: String, valueExpr: Expr, line: Int, store: Store): Unit = {
        pos = line
        try {
            store.load(loc) match {
                case Value.StructVal(typeName, fields, typeArgs) => {
                    val defn = structEnv.lookup(typeName)
                    val expectedType = defn.fields.find(_._1 == field).getOrElse(
                        throwError(s"Unknown field '$field'", "NameError")
                    )._2
                    checkFieldPrivacy(typeName, field)
                    val value = evalExpr(valueExpr, store)
                    checkType(value, expectedType, field, typeArgs)
                    fields(field) = value
                }
                case _ => throwError(s"'$loc' is not a struct", "TypeError")
            }
        } catch case e : RuntimeException => {
            throwError(e.getMessage, SimpError.errorTypeOf(e))
        }
    }
    protected def execFieldIndexAssign(loc: String, field: String, index: Expr, valueExpr: Expr, line: Int, store: Store): Unit = {
        pos = line
        try {
            store.load(loc) match {
                case Value.StructVal(typeName, fields, _) => {
                    checkFieldPrivacy(typeName, field)
                    fields.get(field) match {
                        case Some(Value.ArrVal(elements)) => {
                            val idx = evalExpr(index, store) match {
                                case Value.IntVal(i) => i
                                case _ => throwError("Array index must be an integer", "TypeError")
                            }
                            if idx < 0 || idx >= elements.length then
                                throwError(s"Index $idx out of bounds", "IndexError")
                            elements(idx) = evalExpr(valueExpr, store)
                        }
                        case _ => throwError(s"'$field' is not an array", "TypeError")
                    }
                }
                case _ => throwError(s"'$loc' is not a struct", "TypeError")
            }
        } catch case e : RuntimeException => {
            throwError(e.getMessage, SimpError.errorTypeOf(e))
        }
    }
    protected def execIf(cond: BoolExpr, thenBranch: Cmd, elseBranch: Cmd, line: Int, store: Store): Unit = {
        pos = line
        val condition = evalBool(cond, store)
        if condition then {
            execCmd(thenBranch, store.child())
        } else {
            execCmd(elseBranch, store.child())
        }
    }
    protected def execWhile(cond: BoolExpr, body: Cmd, line: Int, store: Store): Unit = {
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
    protected def execFor(variable: String, iterable: Expr, body: Cmd, line: Int, store: Store): Unit = {
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
            case _ => throwError("for loop expects an array", "TypeError")
        }
    }
    protected def execArrAssign(loc: String, idx: Expr, value: Expr, line: Int, store: Store): Unit = {
        pos = line
        try {
            val arrVal = store.load(loc)
            val index = evalExpr(idx, store)
            val v = evalExpr(value, store)
            (arrVal, index) match {
                case (Value.ArrVal(elements), Value.IntVal(i)) => {
                    if i < 0 || i >= elements.length then {
                        throwError(s"Index $i out of bounds for array of length ${elements.length}", "IndexError")
                    } else {
                        elements(i) = v
                    }
                }
                case _ => throwError("Expected array and integer index", "TypeError")
            }
        } catch case e : RuntimeException => {
            throwError(e.getMessage, SimpError.errorTypeOf(e))
        }
    }
    protected def execArrAssignNested(loc: String, indices: List[Expr], value: Expr, line: Int, store: Store): Unit = {
        pos = line
        val v = evalExpr(value, store)
        try {
            var current = store.load(loc) match {
                case Value.ArrVal(elements) => elements
                case _ => throwError(s"'$loc' is not an array", "TypeError")
            }
            var i = 0
            while i < indices.length - 1 do {
                val idx = evalExpr(indices(i), store) match {
                    case Value.IntVal(n) => n
                    case _ => throwError("Array index must be an integer", "TypeError")
                }
                current = current(idx) match {
                    case Value.ArrVal(elements) => elements
                    case _ => throwError(s"Not an array at index $idx", "IndexError")
                }
                i += 1
            }
            val lastIdx = evalExpr(indices.last, store) match {
                case Value.IntVal(n) => n
                case _ => throwError("Array index must be an integer", "TypeError")
            }
            current(lastIdx) = v
        } catch case e : RuntimeException => {
            throwError(e.getMessage, SimpError.errorTypeOf(e))
        }
    }
    protected def execFieldIndexAssignNested(loc: String, field: String, indices: List[Expr], value: Expr, line: Int, store: Store): Unit = {
        pos = line
        val v = evalExpr(value, store)
        try {
            val struct = store.load(loc) match {
                case Value.StructVal(typeName, fields, _) => { checkFieldPrivacy(typeName, field); fields }
                case _ => throwError(s"'$loc' is not a struct", "TypeError")
            }
            var current = struct(field) match {
                case Value.ArrVal(elements) => elements
                case _ => throwError(s"'$loc.$field' is not an array", "TypeError")
            }
            var i = 0
            while i < indices.length - 1 do {
                val idx = evalExpr(indices(i), store) match {
                    case Value.IntVal(n) => n
                    case _ => throwError("Array index must be an integer", "TypeError")
                }
                current = current(idx) match {
                    case Value.ArrVal(elements) => elements
                    case _ => throwError(s"Not an array at index $idx", "IndexError")
                }
                i += 1
            }
            val lastIdx = evalExpr(indices.last, store) match {
                case Value.IntVal(n) => n
                case _ => throwError("Array index must be an integer", "TypeError")
            }
            current(lastIdx) = v
        } catch case e : RuntimeException => {
            throwError(e.getMessage, SimpError.errorTypeOf(e))
        }
    }
    protected def execTry(tryBody: Cmd, catches: List[CatchClause], line: Int, store: Store): Unit = {
        pos = line
        try {
            execCmd(tryBody, store.child())
        } catch case e: RuntimeException => {
            val errType = SimpError.errorTypeOf(e)
            catches.find(c => SimpError.matches(errType, c.errorType)) match {
                case Some(clause) => {
                    val catchStore = store.child()
                    clause.bindVar.foreach(v => catchStore.declareConst(v, Value.StrVal(e.getMessage)))
                    execCmd(clause.body, catchStore)
                }
                case None => throw e
            }
        }
    }
    protected def execThrow(errorType: Option[String], expr: Expr, line: Int, store: Store): Unit = {
        pos = line
        val message = evalExpr(expr, store) match {
            case Value.StrVal(s) => s
            case other => getPrettyPrint(other, structEnv)
        }
        throwError(message, errorType.getOrElse(SimpError.Root))
    }
    protected def execCmd(cmd: Cmd, store: Store): Unit = {
        cmd match {
            case Cmd.Skip => 
            case Cmd.Scope(body) => execCmd(body, store.child())
            case Cmd.Assign(loc, expr, line) => execAssign(loc, expr, line, store)
            case Cmd.ConstAssign(loc, valueExpr, line) => execConstAssign(loc, valueExpr, line, store)
            case Cmd.TypeDecl(loc, t, line) => execTypeDecl(loc, t, line, store)
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
                println(getPrettyPrint(evalExpr(value, store), structEnv))
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
            case Cmd.Try(tryBody, catches, line) => execTry(tryBody, catches, line, store)
            case Cmd.Throw(errorType, expr, line) => execThrow(errorType, expr, line, store)
        }
    }
}