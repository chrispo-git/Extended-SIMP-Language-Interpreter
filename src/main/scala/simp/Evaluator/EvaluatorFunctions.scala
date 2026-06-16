package simp
import java.io.File
import SimpUtils.*

trait EvaluatorFunctions { self: Evaluator =>
    protected def callFunction(name: String, function: Decl.FnDecl, args: List[Expr], store: Store): Value = {
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
    protected def callFunctionWithValues(name: String, function: Decl.FnDecl, argVals: List[Value], store: Store): Value = {
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
    protected def populateStore(params: List[(String, SimpType)], args: List[Expr], callerStore: Store): Store = {
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
    

    protected def populateStoreFromValues(params: List[(String, SimpType)], argVals: List[Value], callerStore: Store): Store = {
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
}