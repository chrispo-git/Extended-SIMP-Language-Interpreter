package simp 


enum Token:
    case LiteralInt(value: Int)
    case BoolLit(value: Boolean)
    case Variable(loc: String)

    case Add
    case Sub 
    case Div 
    case Mod 
    case Mul 
    case Deref

    case Assign 
    
    case Not 
    case And 
    case Or 

    case Gt
    case Lt 
    case Gte 
    case Lte 
    case Eq
    case Neq

    case Skip 
    case If 
    case Then 
    case Else 
    case While 
    case Do 

    case Semicolon 
    case OpenBracket
    case CloseBracket

    case OpenBrace 
    case CloseBrace

    case Print
    case StringLit(value: String)

    case EOF 

    case Fn 
    case Pd 
    case Return 
    case Call
    case Comma

    case PlusEq 
    case MinusEq
    case MulEq
    case DivEq

    case Elif

    case TypeInt
    case TypeBool
    case TypeString
    case Arrow
    case Colon

    case Ref

    case OpenSquare
    case CloseSquare 

    case Break 
    case Continue