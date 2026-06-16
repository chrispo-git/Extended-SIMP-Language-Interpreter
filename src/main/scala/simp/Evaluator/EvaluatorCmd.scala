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
                    throwError(s"${e.getMessage}")
                }
            }
        }
    }
    protected def execConstAssign(loc: String, valueExpr: Expr, line: Int, store: Store): Unit = {
        pos = line
        val value = evalExpr(valueExpr, store)
        store.declareConst(loc, value)
    }
    protected def execFieldAssign(loc: String, field: String, valueExpr: Expr, line: Int, store: Store): Unit = {
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
    protected def execFieldIndexAssign(loc: String, field: String, index: Expr, valueExpr: Expr, line: Int, store: Store): Unit = {
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
            case _ => throwError("for loop expects an array")
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
    protected def execArrAssignNested(loc: String, indices: List[Expr], value: Expr, line: Int, store: Store): Unit = {
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
    protected def execFieldIndexAssignNested(loc: String, field: String, indices: List[Expr], value: Expr, line: Int, store: Store): Unit = {
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
    protected def execCmd(cmd: Cmd, store: Store): Unit = {
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
}