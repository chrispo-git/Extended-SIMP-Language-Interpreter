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
    protected def evalDeref(loc: String, store: Store): Value = {
        try {
            store.load(loc) match {
                case Value.RefVal(refLoc, refStore) => refStore.load(refLoc)
                case v => v
            }
        } catch case e : RuntimeException => {
            throwError(s"${e.getMessage}")
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
    protected def evalRef(loc: String, store: Store): Value = {
        try {
            store.load(loc)
        } catch case e : RuntimeException => {
            throwError(s"${e.getMessage}")
        }
    }
    protected def evalArrIndex(arr: Expr, idx: Expr, store: Store): Value = {
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
    protected def evalUnaryOp(l: Expr, op: Op, store: Store): Value = {
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
    protected def evalBinarySingleNormal(l: Int | Double, op: Op, r: Int | Double): Value = {
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
    protected def evalBinaryOp(l: Expr, op: Op, r: Expr, store: Store): Value = {
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
    protected def evalStructLiteral(typeName: String, fields: List[(String, Expr)], store: Store): Value = {
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
    protected def evalFieldAccess(expr: Expr, field: String, store: Store): Value = {
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
    protected def evalMethodCall(receiver: Expr, methodName: String, args: List[Expr], store: Store): Value = {
        val receiverVal = evalExpr(receiver, store)
        val typeName = receiverVal match {
            case Value.StructVal(name, _) => name
            case _ => throwError(s"Can't call method '$methodName' on a non-struct value")
        }
        val fnDecl = fnEnv.methodTable.getOrElse((typeName, methodName), throwError(s"No method '$methodName' found for struct '$typeName'"))
        val argVals = receiverVal :: args.map(evalExpr(_, store))
        callFunctionWithValues(methodName, fnDecl, argVals, store)
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
}