package simp
import java.io.File
import SimpUtils.*

trait EvaluatorExpr { self: Evaluator =>
    protected def matchPattern(pattern: Pattern, value: Value, store: Store): Option[Map[String, Value]] = {
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
                    case Value.StructVal(vTypeName, fields, _) if vTypeName == typeName => {
                        val bindings = scala.collection.mutable.Map[String, Value]()
                        val allMatch = fieldPats.forall((fieldName, fieldPat) => {
                            checkFieldPrivacy(vTypeName, fieldName)
                            fields.get(fieldName) match {
                                case None => false
                                case Some(fieldVal) =>
                                    matchPattern(fieldPat, fieldVal, store) match {
                                        case None => false
                                        case Some(b) => bindings ++= b; true
                                    }
                            }
                        })
                        if allMatch then Some(bindings.toMap) else None
                    }
                    case _ => None
                }
            }
        }
    }
    protected def evalDeref(loc: String, store: Store): Value = {
        try {
            store.load(loc) match {
                case Value.RefVal(refLoc, refStore) => refStore.load(refLoc)
                case v => v
            }
        } catch case e : RuntimeException => {
            throwError(e.getMessage, SimpError.errorTypeOf(e))
        }
    }
    protected def evalBlock(cmds: List[Cmd], result: Expr, store: Store): Value = {
        val childStore = store.child()
        cmds.foreach(cmd => execCmd(cmd, childStore))
        evalExpr(result, childStore)
    }
    protected def evalMatch(expr: Expr, arms: List[MatchArm], store: Store): Value = {
        val value = evalExpr(expr, store)
        val matched = arms.find(arm =>
            matchPattern(arm.pattern, value, store) match {
                case Some(bindings) => {
                    arm.guard match {
                        case None => true
                        case Some(guard) => {
                            val guardStore = store.child()
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
                val matchStore = store.child()
                matchPattern(arm.pattern, value, store).get.foreach((k, v) => matchStore.store(k, v))
                evalExpr(arm.body, matchStore)
            }
        }
    }
    protected def evalRef(loc: String, store: Store): Value = {
        try {
            store.load(loc)
        } catch case e : RuntimeException => {
            throwError(e.getMessage, SimpError.errorTypeOf(e))
        }
    }
    protected def evalArrIndex(arr: Expr, idx: Expr, store: Store): Value = {
        val arrVal = evalExpr(arr, store)
        val index = evalExpr(idx, store)
        (arrVal, index) match {
            case (Value.ArrVal(elements), Value.IntVal(i)) => {
                if i < 0 || i >= elements.length then {
                    throwError(s"Index $i out of bounds for array of length ${elements.length}", "IndexError")
                } else {
                    elements(i)
                }
            }
            case _ => throwError("Expected array and integer index", "TypeError")
        }
    }
    protected def evalUnaryOp(l: Expr, op: Op, store: Store): Value = {
        evalExpr(l, store) match {
            case Value.IntVal(left) => {
                op match {
                    case Op.BitComplement => Value.IntVal(~left)
                    case x => throwError(s"Unsupported operation '$x'", "TypeError")
                }
            }
            case _ => throwError(s"Type mismatch in unary operation", "TypeError")
        }
    }
    protected def evalBinarySingleNormal(l: Int | Double, op: Op, r: Int | Double): Value = {
        val left = l match { case i: Int => i.toDouble; case d: Double => d }
        val right = r match { case i: Int => i.toDouble; case d: Double => d }
        op match {
            case Op.Add => Value.FloatVal(left + right)
            case Op.Sub => Value.FloatVal(left - right)
            case Op.Mul => Value.FloatVal(left * right)
            case Op.Div if right == 0 => throwError(s"Division by Zero!", "ValueError")
            case Op.Div => Value.FloatVal(left / right)
            case x => throwError(s"Unsupported operation '$x'", "TypeError")
        }
    }
    val opMethodName: Map[Op, String] = Map(
        Op.Add -> "add", Op.Sub -> "sub", Op.Mul -> "mul", Op.Div -> "div", Op.Mod -> "mod"
    )
    protected def evalBinaryOp(l: Expr, op: Op, r: Expr, store: Store): Value = {
        (evalExpr(l, store), evalExpr(r, store)) match {
            case (Value.IntVal(left), Value.IntVal(right)) => {
                op match {
                    case Op.Add => Value.IntVal(left + right)
                    case Op.Sub => Value.IntVal(left - right)
                    case Op.Mul => Value.IntVal(left * right)
                    case Op.Mod => Value.IntVal(left % right)
                    case Op.Div if right == 0 => throwError(s"Division by Zero!", "ValueError")
                    case Op.Div => Value.IntVal(left / right)
                    case Op.BitAnd => Value.IntVal(left & right)
                    case Op.BitOr => Value.IntVal(left | right)
                    case Op.BitXor => Value.IntVal(left ^ right)
                    case Op.BitLeft => Value.IntVal(left << right)
                    case Op.BitRight => Value.IntVal(left >> right)
                    case Op.BitRightFill => Value.IntVal(left >>> right)
                    case x => throwError(s"Unsupported operation '$x'", "TypeError") 
                }
            }
            case (Value.IntVal(left), Value.FloatVal(right)) => evalBinarySingleNormal(left, op, right)
            case (Value.FloatVal(left), Value.IntVal(right)) => evalBinarySingleNormal(left, op, right)
            case (Value.FloatVal(left), Value.FloatVal(right)) => evalBinarySingleNormal(left, op, right)
            case (Value.StrVal(left),right) => {
                op match {
                    case Op.Add => Value.StrVal(left + getPrettyPrint(right, structEnv))
                    case x => throwError(s"Unsupported operation '$x'", "TypeError")
                }
            }
            case (sL @ Value.StructVal(t1, _, _), sR @ Value.StructVal(t2, _, _)) if t1==t2 => op match {
                case Op.Add => callMethod(t1, "_add", List(sL, sR), store)
                case Op.Sub => callMethod(t1, "_sub", List(sL, sR), store)
                case Op.Mul => callMethod(t1, "_mul", List(sL, sR), store)
                case Op.Mod => callMethod(t1, "_mod", List(sL, sR), store)
                case Op.Div => callMethod(t1, "_div", List(sL, sR), store)
                case x => throwError(s"Unsupported operation '$x'", "TypeError")
            }
            
            case _ => throwError(s"Type mismatch in binary operation", "TypeError")
        }
    }
    // Resolves the concrete type-argument bindings a generic struct literal/instance
    // is constructed with: either from explicit `<...>` args at this literal, or
    // (when omitted) inherited from the nearest enclosing generic context for this
    // exact struct (e.g. `Stack{}` written inside `Stack<T>`'s own static factory
    // method, which should bind `T` to whatever the caller of that factory chose).
    protected def resolveStructTypeArgs(typeName: String, defn: StructDef, typeArgs: List[SimpType]): Map[String, SimpType] = {
        if defn.typeParams.isEmpty then {
            Map()
        } else if typeArgs.nonEmpty then {
            if typeArgs.length != defn.typeParams.length then {
                throwError(s"Generic struct '$typeName' expects ${defn.typeParams.length} type argument(s), got ${typeArgs.length}", "TypeError")
            }
            // A given type argument may itself reference an *enclosing* generic
            // context's own type parameter (e.g. writing `Stack<T>{}` inside
            // `Stack<T>`'s own method, instead of the equivalent bare `Stack{}`) -
            // resolve it against the current ambient bindings before storing it,
            // so the instance ends up bound to the real concrete type, not a
            // dangling, unresolved `T`.
            val resolvedArgs = typeArgs.map(t => if containsTypeParam(t) then resolveTypeParams(t, currentTypeBindings) else t)
            defn.typeParams.zip(resolvedArgs).toMap
        } else {
            typeBindingStack.find(_._1 == typeName).map(_._2).getOrElse(
                throwError(s"Generic struct '$typeName' requires explicit type arguments, e.g. $typeName<Int>{...}", "TypeError")
            )
        }
    }
    protected def evalStructLiteral(typeName: String, fields: List[(String, Expr)], typeArgs: List[SimpType], store: Store): Value = {
        checkStructLock(typeName)
        val defn = structEnv.lookup(typeName)
        val boundTypeArgs = resolveStructTypeArgs(typeName, defn, typeArgs)
        typeBindingStack = (typeName, boundTypeArgs) :: typeBindingStack
        try {
            val fieldMap = scala.collection.mutable.Map[String, Value]()
            defn.fields.foreach((name, expectedType, default, isPriv) => {
                val fieldExpr = fields.find(_._1 == name)
                val value = fieldExpr match {
                    case Some((_, expr)) => evalExpr(expr, store)
                    case None => default match {
                        case Some(expr) => evalExpr(expr, store)
                        case None => throwError(s"Missing field '$name' in $typeName literal and no default value provided", "ValueError")
                    }
                }
                checkType(value, expectedType, name, currentTypeBindings)
                fieldMap(name) = value
            })
            Value.StructVal(typeName, fieldMap, boundTypeArgs)
        } finally {
            typeBindingStack = typeBindingStack.tail
        }
    }
    protected def evalFieldAccess(expr: Expr, field: String, store: Store): Value = {
        evalExpr(expr, store) match {
            case Value.PairVal(fst, snd) => field match {
                case "fst" => fst
                case "snd" => snd
                case _ => throwError(s"Pairs only have 'fst' and 'snd' fields", "NameError")
            }
            case Value.StructVal(typeName, fields, _) => {
                checkFieldPrivacy(typeName, field)
                fields.getOrElse(field, throwError(s"Unknown field '$field'", "NameError"))
            }
            case _ => throwError("Field access on non-struct or pair value", "TypeError")
        }
    }
    protected def callMethod(typeName: String, methodName: String, argVals: List[Value], store: Store): Value = {
        val fnDecl = fnEnv.methodTable.getOrElse(
            (typeName, methodName),
            throwError(s"No method '$methodName' found for struct '$typeName'", "NameError")
        )
        if fnDecl.isStatic then {
            throwError(s"Method '$methodName' is static and must be called as '$typeName.$methodName(...)', not on an instance", "TypeError")
        }
        checkMethodPrivacy(typeName, methodName)
        val boundTypeArgs = argVals.headOption match {
            case Some(Value.StructVal(_, _, typeArgs)) => typeArgs
            case _ => Map[String, SimpType]()
        }
        implContextStack = typeName :: implContextStack
        typeBindingStack = (typeName, boundTypeArgs) :: typeBindingStack
        try {
            callFunctionWithValues(methodName, fnDecl, argVals, store)
        } finally {
            implContextStack = implContextStack.tail
            typeBindingStack = typeBindingStack.tail
        }
    }
    protected def callStaticMethod(typeName: String, methodName: String, typeArgs: List[SimpType], argVals: List[Value], store: Store): Value = {
        val fnDecl = fnEnv.methodTable.getOrElse(
            (typeName, methodName),
            throwError(s"No method '$methodName' found for struct '$typeName'", "NameError")
        )
        if !fnDecl.isStatic then {
            throwError(s"Method '$methodName' is not static; it must be called on an instance of '$typeName'", "TypeError")
        }
        checkMethodPrivacy(typeName, methodName)
        val defn = structEnv.lookup(typeName)
        val boundTypeArgs =
            if defn.typeParams.isEmpty then Map[String, SimpType]()
            else if typeArgs.nonEmpty then {
                if typeArgs.length != defn.typeParams.length then {
                    throwError(s"Generic struct '$typeName' expects ${defn.typeParams.length} type argument(s), got ${typeArgs.length}", "TypeError")
                }
                // Same resolution as resolveStructTypeArgs: a type argument here may
                // itself be an enclosing generic context's own type parameter (e.g.
                // `Stack<T>.helper()` called from within Stack<T>'s own method).
                val resolvedArgs = typeArgs.map(t => if containsTypeParam(t) then resolveTypeParams(t, currentTypeBindings) else t)
                defn.typeParams.zip(resolvedArgs).toMap
            } else {
                throwError(s"Static method '$typeName.$methodName' on a generic struct requires explicit type arguments, e.g. $typeName<Int>.$methodName(...)", "TypeError")
            }
        implContextStack = typeName :: implContextStack
        typeBindingStack = (typeName, boundTypeArgs) :: typeBindingStack
        try {
            callFunctionWithValues(methodName, fnDecl, argVals, store)
        } finally {
            implContextStack = implContextStack.tail
            typeBindingStack = typeBindingStack.tail
        }
    }
    protected def evalMethodCall(receiver: Expr, methodName: String, args: List[Expr], store: Store): Value = {
        val receiverVal = evalExpr(receiver, store)
        receiverVal match {
            case Value.StructTypeVal(typeName, typeArgs) => {
                val argVals = args.map(evalExpr(_, store))
                callStaticMethod(typeName, methodName, typeArgs, argVals, store)
            }
            case Value.StructVal(typeName, _, _) => {
                val argVals = receiverVal :: args.map(evalExpr(_, store))
                callMethod(typeName, methodName, argVals, store)
            }
            case _ => throwError(s"Can't call method '$methodName' on a non-struct value", "TypeError")
        }
    }
    protected def evalFnCall(name: String, args: List[Expr], store: Store): Value = {
        val evaluatedArgs = args.map(evalExpr(_, store))
        fnEnv.lookupBuiltin(name) match {
            case Some(fn) => fn(evaluatedArgs)
            case None => {
                val function = fnEnv.lookupFn(name)
                callFunction(name, function, args, store)
            }
        }
    }
    protected def evalExpr(expr: Expr, store: Store): Value = {
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
                Value.ArrVal(TypedArray(evaluated*))
            }
            case Expr.Pair(l, r) => {
                val fst = evalExpr(l, store);
                val snd = evalExpr(r, store);
                Value.PairVal(fst, snd)
            }
            case Expr.ArrIndex(arr, idx) => evalArrIndex(arr, idx, store)
            case Expr.UnaryOp(l, op) => evalUnaryOp(l, op, store)
            case Expr.BinaryOp(l, op, r) => evalBinaryOp(l, op, r, store)
            case Expr.StructLiteral(typeName, fields, typeArgs) => evalStructLiteral(typeName, fields, typeArgs, store)
            case Expr.StructTypeRef(typeName, typeArgs) => Value.StructTypeVal(typeName, typeArgs)
            case Expr.FieldAccess(expr, field) => evalFieldAccess(expr, field, store)
            case Expr.MethodCall(receiver, methodName, args) => evalMethodCall(receiver, methodName, args, store)
            case Expr.FnCall(name, args) => evalFnCall(name, args, store)
        }
    }
}