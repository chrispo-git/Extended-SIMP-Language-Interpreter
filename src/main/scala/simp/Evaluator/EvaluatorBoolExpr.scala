package simp
import java.io.File
import SimpUtils.*

trait EvaluatorBoolExpr { self: Evaluator =>
    protected def compare(left: Int | Double, right: Int | Double): Int = {
        (left, right) match {
            case (l: Int, r: Int)       => l.compareTo(r)
            case (l: Double, r: Double) => l.compareTo(r)
            case (l: Int, r: Double)    => l.toDouble.compareTo(r)
            case (l: Double, r: Int)    => l.compareTo(r.toDouble)
        }
    }

    protected def singleCompare(left: Int | Double, bop: Bop, right: Int | Double): Boolean = {
        bop match {
            case Bop.Gt => compare(left, right) > 0
            case Bop.Gte => compare(left, right) >= 0 
            case Bop.Lt => compare(left, right) < 0 
            case Bop.Lte => compare(left, right) <= 0
            case Bop.Eq => compare(left, right) == 0 
            case Bop.Neq => compare(left, right) != 0 
        }
    }
    protected def evalCompare(l: Expr, bop: Bop, r: Expr, store: Store): Boolean = {
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

    protected def evalBool(boolExpr: BoolExpr, store: Store): Boolean = {
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
    protected def structsEqual(f1: scala.collection.mutable.Map[String, Value], f2: scala.collection.mutable.Map[String, Value], visited: Set[(AnyRef, AnyRef)] = Set()): Boolean = {
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

    protected def valuesEqual(v1: Value, v2: Value, visited: Set[(AnyRef, AnyRef)]): Boolean = (v1, v2) match {
        case (Value.IntVal(a), Value.IntVal(b))   => a == b
        case (Value.FloatVal(a), Value.FloatVal(b))   => a == b
        case (Value.StrVal(a), Value.StrVal(b))   => a == b
        case (Value.BoolVal(a), Value.BoolVal(b)) => a == b
        case (Value.NullVal, Value.NullVal)        => true
        case (Value.ArrVal(a), Value.ArrVal(b))   => a.length == b.length && a.zip(b).forall((x, y) => valuesEqual(x, y, visited))
        case (Value.StructVal(t1, f1), Value.StructVal(t2, f2)) => t1 == t2 && structsEqual(f1, f2, visited)
        case _ => false
    }
}