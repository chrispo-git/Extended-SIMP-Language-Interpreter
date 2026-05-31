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
    case Flt(value: Double)
    case Str(value: String)
    case Bool(value: Boolean)
    case BoolLift(expr: BoolExpr)
    case BinaryOp(l: Expr, op: Op, r: Expr)
    case FnCall(name: String, args: List[Expr])
    case Ref(loc: String)
    case ArrLiteral(elements: List[Expr]) 
    case ArrIndex(arr: Expr, index: Expr)
    case StructLiteral(typeName: String, fields: List[(String, Expr)]) 
    case FieldAccess(expr: Expr, field: String) 
    case Null

// Operators allowed - Add, Sub, Mul, Div, Mod
enum Op:
    case Add, Sub, Mul, Div, Mod


// Values allowed, including a ref value
enum Value:
  case IntVal(n: Int)
  case FloatVal(n: Double)
  case StrVal(s: String)
  case BoolVal(b: Boolean)
  case RefVal(loc: String, store: Store)
  case ArrVal(elements: scala.collection.mutable.ArrayBuffer[Value])
  case StructVal(typeName: String, fields: scala.collection.mutable.Map[String, Value])
  case NullVal

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
    case For(variable: String, iterable: Expr, body: Cmd)
    case Print(value: Expr)
    case Return(expr: Option[Expr] = None)
    case ArrAssign(arr: String, index: Expr, value: Expr)
    case FieldAssign(loc: String, field: String, value: Expr)
    case FieldIndexAssign(loc: String, field: String, index: Expr, value: Expr)
    case Continue
    case Break


enum Program:
    case PCmd(cmd: Cmd)
    case PDecl(decl: Decl)
    case PExpr(expr: Expr)
    case PBool(boolExpr: BoolExpr)

enum Decl:
  case FnDecl(name: String, params: List[(String, SimpType)], body: Cmd, returnType: SimpType)
  case StructDecl(name: String, fields: List[(String, SimpType, Option[Expr])])
  case ImportDecl(path: String, alias: String)

enum SimpType:
    case TypeInt
    case TypeString
    case TypeFloat
    case TypeBool 
    case TypeNull
    case TypeRef(inner: SimpType)
    case TypeArr(inner: SimpType)
    case TypeStruct(name: String)

case class ReturnException(value: Option[Value] = None) extends Exception
case class BreakException() extends Exception
case class ContinueException() extends Exception