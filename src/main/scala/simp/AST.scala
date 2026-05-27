package simp

// AST Structure of SIMP

/* Integer Expressions can either be:
    - Integer Value
    - Dereferenced Variable
    - 2 Expressions joined by a Binary Operator
*/
enum Expr:
    case Deref(loc: String)
    case Num(value: Int)
    case BinaryOp(l: Expr, op: Op, r: Expr)
    case FnCall(name: String, args: List[Expr])

// Operators allowed - Add, Sub, Mul, Div, Mod
enum Op:
    case Add, Sub, Mul, Div, Mod


/* Boolean Expressions can either be:
    - True
    - False
    - E bop E
    - ¬B
    - B^B
    - BvB
*/
enum BoolExpr:
    case Literal(value: Boolean)
    case Compare(l: Expr, bop: Bop, r: Expr)
    case Not(expr: BoolExpr)
    case And(l: BoolExpr, r: BoolExpr)
    case Or(l: BoolExpr, r: BoolExpr)

enum Bop:
    case Gt, Lt, Eq, Gte, Lte, Neq

enum Cmd:
    case Skip
    case Assign(loc: String, expr: Expr)
    case Seq(fst: Cmd, snd: Cmd)
    case If(cond: BoolExpr, thenBranch: Cmd, elseBranch: Cmd)
    case While(cond: BoolExpr, body: Cmd)
    case Print(value: Printable)
    case PdCall(name: String, args: List[Expr])
    case Return(expr: Expr)

enum Printable:
  case PrintStr(value: String)
  case PrintExpr(expr: Expr)

enum Program:
    case PCmd(cmd: Cmd)
    case PDecl(decl: Decl)
    case PExpr(expr: Expr)
    case PBool(boolExpr: BoolExpr)

enum Decl:
  case FnDecl(name: String, params: List[String], body: Cmd)
  case PdDecl(name: String, params: List[String], body: Cmd)

case class ReturnException(value: Int) extends Exception