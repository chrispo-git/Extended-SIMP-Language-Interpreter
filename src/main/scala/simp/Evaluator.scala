package simp

class Evaluator(fnEnv: FunctionEnv):

    def evalProgram(program: List[Program], store: Store): Unit = {
        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body)) => fnEnv.registerFn(name, Decl.FnDecl(name, params, body))
            case Program.PDecl(Decl.PdDecl(name, params, body)) => fnEnv.registerPd(name, Decl.PdDecl(name, params, body))
            case Program.PCmd(cmd) => execCmd(cmd, store)
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

    private def evalExpr(expr: Expr, store: Store): Int = {
        expr match {
            case Expr.Num(n) => n 
            case Expr.Deref(loc) => store.load(loc)
            case Expr.BinaryOp(l, op, r) => {
                val left = evalExpr(l, store)
                val right = evalExpr(r, store)
                op match {
                    case Op.Add => left + right
                    case Op.Sub => left - right 
                    case Op.Mul => left * right 
                    case Op.Div if right == 0 => throw RuntimeException(s"Division by Zero!")
                    case Op.Div => left / right 
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
            case BoolExpr.Compare(l,bop,r) => {
                val left = evalExpr(l, store)
                val right = evalExpr(r, store)
                bop match {
                    case Bop.Gt => left > right
                    case Bop.Gte => left >= right 
                    case Bop.Lt => left < right 
                    case Bop.Lte => left <= right
                    case Bop.Eq => left == right 
                    case Bop.Neq => left != right 
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
                value match {
                    case Printable.PrintStr(s) => println(s)
                    case Printable.PrintExpr(e) => println(evalExpr(e, store))
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
