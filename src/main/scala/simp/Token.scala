package simp 


enum Token:
    case LiteralInt(value: Int)
    case BoolLit(value: Boolean)
    case Variable(loc: String)

    case Add
    case Sub 
    case Div 
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

    case Skip 
    case If 
    case Then 
    case Else 
    case While 
    case Do 

    case Semicolon 
    case OpenBracket
    case CloseBracket

    case EOF 
