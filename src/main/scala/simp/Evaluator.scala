package simp

class Evaluator(store: Store):

    def evalProgram(p: Program): Unit = p match {
        case Program.PCmd(cmd)   => execCmd(cmd)
        case Program.PExpr(expr) => println(evalExpr(expr))
        case Program.PBool(b)    => println(evalBool(b))
    }

    private def evalExpr(expr: Expr): Int = {
        expr match {
            case Expr.Num(n) => n 
            case Expr.Deref(loc) => store.load(loc)
            case Expr.BinaryOp(l, op, r) => {
                val left = evalExpr(l)
                val right = evalExpr(r)
                op match {
                    case Op.Add => left + right
                    case Op.Sub => left - right 
                    case Op.Mul => left * right 
                    case Op.Div if right == 0 => throw RuntimeException(s"Division by Zero!")
                    case Op.Div => left / right 
                }
            }
        }
    }

    private def evalBool(boolExpr: BoolExpr): Boolean = {
        boolExpr match {
            case BoolExpr.Literal(b) => b 
            case BoolExpr.Not(inner) => {
                val result = evalBool(inner)
                !result
            }
            case BoolExpr.And(l, r) => {
                val left = evalBool(l)
                if !left then return false
                val right = evalBool(r)
                left && right
            }
            case BoolExpr.Or(l, r) => {
                val left = evalBool(l)
                if left then return true
                val right = evalBool(r)
                left || right
            }
            case BoolExpr.Compare(l,bop,r) => {
                val left = evalExpr(l)
                val right = evalExpr(r)
                bop match {
                    case Bop.Gt => left > right
                    case Bop.Gte => left >= right 
                    case Bop.Lt => left < right 
                    case Bop.Lte => left <= right
                    case Bop.Eq => left == right 
                }
            }
        }
    }

    private def execCmd(cmd: Cmd): Unit = {
        cmd match {
            case Cmd.Skip => 
            case Cmd.Assign(loc, expr) => {
                val value = evalExpr(expr)
                store.store(loc, value)
            }
            case Cmd.Seq(fst, snd) => {
                execCmd(fst)
                execCmd(snd)
            }
            case Cmd.If(cond, t, e) => {
                val condition = evalBool(cond)
                if condition then {
                    execCmd(t)
                } else {
                    execCmd(e)
                }
            }
            case Cmd.While(cond, body) => {
                while evalBool(cond) do execCmd(body)
            }
            case Cmd.Print(value) => {
                value match {
                    case Printable.PrintStr(s) => println(s)
                    case Printable.PrintExpr(e) => println(evalExpr(e))
                }
            }
        }
    }
