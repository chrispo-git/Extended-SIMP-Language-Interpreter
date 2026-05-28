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
    case Str(value: String)
    case Bool(value: Boolean)
    case BoolLift(expr: BoolExpr)
    case BinaryOp(l: Expr, op: Op, r: Expr)
    case FnCall(name: String, args: List[Expr])
    case Ref(loc: String)

// Operators allowed - Add, Sub, Mul, Div, Mod
enum Op:
    case Add, Sub, Mul, Div, Mod


// Values allowed, including a ref value
enum Value:
  case IntVal(n: Int)
  case StrVal(s: String)
  case BoolVal(b: Boolean)
  case RefVal(loc: String, store: Store)

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
    case FromExpr(expr: Expr)

enum Bop:
    case Gt, Lt, Eq, Gte, Lte, Neq

enum Cmd:
    case Skip
    case Assign(loc: String, expr: Expr)
    case Seq(fst: Cmd, snd: Cmd)
    case If(cond: BoolExpr, thenBranch: Cmd, elseBranch: Cmd)
    case While(cond: BoolExpr, body: Cmd)
    case Print(value: Expr)
    case PdCall(name: String, args: List[Expr])
    case Return(expr: Expr)


enum Program:
    case PCmd(cmd: Cmd)
    case PDecl(decl: Decl)
    case PExpr(expr: Expr)
    case PBool(boolExpr: BoolExpr)

enum Decl:
  case FnDecl(name: String, params: List[(String, SimpType)], body: Cmd, returnType: SimpType)
  case PdDecl(name: String, params: List[(String, SimpType)], body: Cmd)

enum SimpType:
    case TypeInt
    case TypeString
    case TypeBool 
    case TypeRef(inner: SimpType)

case class ReturnException(value: Value) extends Exception