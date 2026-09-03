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
    protected var implContextStack: List[String] = Nil

    protected def currentLine(): Int = pos

    protected def checkFieldPrivacy(typeName: String, field: String): Unit = {
        structEnv.lookup(typeName).fields.find(_._1 == field).foreach {
            case (_, _, _, isPrivate) => {
                if isPrivate && !implContextStack.headOption.contains(typeName) then {
                    throwError(s"Field '$field' is private to struct '$typeName'")
                }
            }
        }
    }
    protected def checkMethodPrivacy(typeName: String, methodName: String): Unit = {
        val method = fnEnv.methodTable.getOrElse(
            (typeName, methodName),
            throwError(s"No method '$methodName' found for struct '$typeName'")
        );
        if !implContextStack.headOption.contains(typeName) && method.isPrivate then {
            throwError(s"Method '$methodName' is private to struct '$typeName'")
        }
    }

    def evalProgram(program: List[Program], store: Store): Unit = {
        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body, returnType, isPrivate)) => fnEnv.registerFn(name, Decl.FnDecl(name, params, body, returnType, isPrivate))
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
    
    
    
