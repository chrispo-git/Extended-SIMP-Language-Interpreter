package simp
import java.io.File
import SimpUtils.*

class Evaluator(protected val fnEnv: FunctionEnv, protected val structEnv: StructEnv, protected val sourceLines: List[String], protected val cwd: String = ".")
    extends EvaluatorExpr
    with EvaluatorBoolExpr
    with EvaluatorCmd
    with EvaluatorFunctions
    with EvaluatorImport:
    protected val importedFiles = scala.collection.mutable.Set[String]()
    protected val completedImports = scala.collection.mutable.Map[String, Set[String]]()


    protected var pos: Int = 0

    protected def currentLine(): Int = pos

    def evalProgram(program: List[Program], store: Store): Unit = {
        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body, returnType)) => fnEnv.registerFn(name, Decl.FnDecl(name, params, body, returnType))
            case Program.PDecl(Decl.ImportDecl(path, alias)) => processImport(path, alias, cwd, store)
            case Program.PDecl(Decl.StructDecl(name, fields)) => structEnv.register(name, StructDef(fields))
            case Program.PCmd(cmd) => execCmd(cmd, store)
            case Program.PExpr(expr) => println(getPrettyPrint(evalExpr(expr, store)))
            case Program.PBool(b) => println(evalBool(b, store))
            case Program.PImpl(structName, methods) => methods.foreach(m => fnEnv.methodTable((structName, m.name)) = m)
        })
    }
    protected def currentLineSource(): String = sourceLines(currentLine()-1).trim
    protected def throwError(msg: String): Nothing = {
        throw RuntimeException(s"on line ${currentLine()}\n${currentLineSource()}\n\u001b[31m$msg\u001b[0m")
    }
    
    
    
