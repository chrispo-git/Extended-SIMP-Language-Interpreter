package simp

class Evaluator(fnEnv: FunctionEnv):

    def evalProgram(program: List[Program], store: Store): Unit = {
        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body)) => fnEnv.registerFn(name, Decl.FnDecl(name, params, body))
            case Program.PDecl(Decl.PdDecl(name, params, body)) => fnEnv.registerPd(name, Decl.PdDecl(name, params, body))
            case Program.PCmd(cmd) => execCmd(cmd, store)
            case Program.PExpr(expr) => println(evalExpr(expr, store))
            case Program.PBool(b) => println(evalBool(b, store))
        })
    }

    private def populateStore(params: List[String], args: List[Expr], callerStore: Store): Store = {
        if params.length != args.length then
            throw RuntimeException(s"Expected ${params.length} arguments, got ${args.length}")
        val localStore = Store()
        params.zip(args).foreach((param, arg) =>
            localStore.store(param, evalExpr(arg, callerStore))
        )
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
            case Expr.Deref(loc) => store.load(loc)
            case Expr.Str(s) => Value.StrVal(s)
            case Expr.Bool(b) => Value.BoolVal(b)
            case Expr.BoolLift(b) => Value.BoolVal(evalBool(b, store))
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
            case Expr.FnCall(name, args) => {
                val function = fnEnv.lookupFn(name)
                val localStore = populateStore(function.params, args, store)
                try {
                    execCmd(function.body, localStore)
                    throw RuntimeException(s"Function '$name' has no return statement")
                } catch {
                    case ReturnException(value) => value
                }
            }
        }
    }



    private def execCmd(cmd: Cmd, store: Store): Unit = {
        cmd match {
            case Cmd.Skip => 
            case Cmd.Assign(loc, expr) => {
                val value = evalExpr(expr, store)
                store.store(loc, value)
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
                while evalBool(cond, store) do execCmd(body, store)
            }
            case Cmd.Print(value) => {
                evalExpr(value, store) match {
                    case Value.StrVal(s) => println(s)
                    case Value.IntVal(n) => println(n)
                    case Value.BoolVal(b) => println(b)
                }
            }
            case Cmd.PdCall(name, args) => {
                val function = fnEnv.lookupPd(name)
                val localStore = populateStore(function.params, args, store)
                execCmd(function.body, localStore)
            }
            case Cmd.Return(expr) => throw ReturnException(evalExpr(expr, store))
        }
    }
