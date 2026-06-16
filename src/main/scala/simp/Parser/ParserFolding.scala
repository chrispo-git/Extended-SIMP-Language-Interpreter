package simp

trait ParserFolding { self: Parser =>
    protected def evalLitCompare(l: Double, bop: Bop, r: Double): Boolean = bop match {
        case Bop.Gt  => l > r
        case Bop.Lt  => l < r
        case Bop.Gte => l >= r
        case Bop.Lte => l <= r
        case Bop.Eq  => l == r
        case Bop.Neq => l != r
    }
    
    protected def foldIf(cond: BoolExpr, t: Cmd, e: Cmd): Cmd = cond match {
        case BoolExpr.Literal(true)  => t
        case BoolExpr.Literal(false) => e
        case _ => Cmd.If(cond, t, e, currentLine())
    }
    protected def foldWhile(cond: BoolExpr, body: Cmd): Cmd = cond match {
        case BoolExpr.Literal(false) => Cmd.Skip
        case _ => Cmd.While(cond, body, currentLine())
    }
    protected def foldCompare(left: Expr, bop: Bop, right: Expr): BoolExpr = (left, right) match {
        case (Expr.Num(l), Expr.Num(r)) => BoolExpr.Literal(evalLitCompare(l.toDouble, bop, r.toDouble))
        case (Expr.Flt(l), Expr.Flt(r)) => BoolExpr.Literal(evalLitCompare(l, bop, r))
        case (Expr.Num(l), Expr.Flt(r)) => BoolExpr.Literal(evalLitCompare(l.toDouble, bop, r))
        case (Expr.Flt(l), Expr.Num(r)) => BoolExpr.Literal(evalLitCompare(l, bop, r.toDouble))
        case (Expr.Str(l), Expr.Str(r)) if bop == Bop.Eq || bop == Bop.Neq =>
            BoolExpr.Literal(if bop == Bop.Eq then l == r else l != r)
        case (Expr.Bool(l), Expr.Bool(r)) if bop == Bop.Eq || bop == Bop.Neq =>
            BoolExpr.Literal(if bop == Bop.Eq then l == r else l != r)
        case _ => BoolExpr.Compare(left, bop, right)
    }
    protected def foldBinary(left: Expr, op: Op, right: Expr): Expr = (left, op, right) match {

        // Just Ints...
        case (Expr.Num(l),Op.Add, Expr.Num(r)) => Expr.Num(l + r)
        case (Expr.Num(l),Op.Sub, Expr.Num(r)) => Expr.Num(l - r)
        case (Expr.Num(l),Op.Mul, Expr.Num(r)) => Expr.Num(l * r)
        case (Expr.Num(l),Op.Div, Expr.Num(r)) if r != 0 => Expr.Num(l / r)
        case (Expr.Num(l),Op.Mod, Expr.Num(r)) if r != 0 => Expr.Num(l % r)

        // Just Floats...
        case (Expr.Flt(l),Op.Add, Expr.Flt(r)) => Expr.Flt(l + r)
        case (Expr.Flt(l),Op.Sub, Expr.Flt(r)) => Expr.Flt(l - r)
        case (Expr.Flt(l),Op.Mul, Expr.Flt(r)) => Expr.Flt(l * r)
        case (Expr.Flt(l),Op.Div, Expr.Flt(r)) if r != 0 => Expr.Flt(l / r)

        // Float + Int Mixed
        case (Expr.Num(l),Op.Add, Expr.Flt(r)) => Expr.Flt(l + r)
        case (Expr.Num(l),Op.Sub, Expr.Flt(r)) => Expr.Flt(l - r)
        case (Expr.Num(l),Op.Mul, Expr.Flt(r)) => Expr.Flt(l * r)
        case (Expr.Num(l),Op.Div, Expr.Flt(r)) if r != 0 => Expr.Flt(l / r)
        case (Expr.Flt(l),Op.Add, Expr.Num(r)) => Expr.Flt(l + r)
        case (Expr.Flt(l),Op.Sub, Expr.Num(r)) => Expr.Flt(l - r)
        case (Expr.Flt(l),Op.Mul, Expr.Num(r)) => Expr.Flt(l * r)
        case (Expr.Flt(l),Op.Div, Expr.Num(r)) if r != 0 => Expr.Flt(l / r)

        // Concatenation of literals
        case (Expr.Str(l), Op.Add, Expr.Str(r)) => Expr.Str(l + r)

        // Bitwise shit
        case (Expr.Num(l), Op.BitAnd,      Expr.Num(r)) => Expr.Num(l & r)
        case (Expr.Num(l), Op.BitOr,       Expr.Num(r)) => Expr.Num(l | r)
        case (Expr.Num(l), Op.BitXor,      Expr.Num(r)) => Expr.Num(l ^ r)
        case (Expr.Num(l), Op.BitLeft,     Expr.Num(r)) => Expr.Num(l << r)
        case (Expr.Num(l), Op.BitRight,    Expr.Num(r)) => Expr.Num(l >> r)
        case (Expr.Num(l), Op.BitRightFill, Expr.Num(r)) => Expr.Num(l >>> r)
        
        case _ => Expr.BinaryOp(left, op, right)
    }

    protected def foldUnary(expr: Expr, op: Op): Expr = (expr, op) match {
        case (Expr.Num(n), Op.BitComplement) => Expr.Num(~n)
        case _ => Expr.UnaryOp(expr, op)
    }
}