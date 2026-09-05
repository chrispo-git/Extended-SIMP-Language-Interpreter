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
    // Stack of (structName, {typeParamName -> concreteType}) frames, pushed whenever
    // constructing an instance of a generic struct or entering one of its methods
    // (static or instance), so a `TypeParam` can be resolved to the concrete type
    // it's bound to for the struct currently under construction/execution.
    protected var typeBindingStack: List[(String, Map[String, SimpType])] = Nil

    protected def currentTypeBindings: Map[String, SimpType] = typeBindingStack.headOption.map(_._2).getOrElse(Map())

    protected def currentLine(): Int = pos

    protected def checkStructLock(typeName: String): Unit = {
        if structEnv.lookup(typeName).isLocked && !implContextStack.headOption.contains(typeName) then {
            throwError(s"Struct '$typeName' is locked and cannot be constructed directly; use a static factory method")
        }
    }
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
            case Program.PDecl(Decl.FnDecl(name, params, body, returnType, isPrivate, isStatic)) => fnEnv.registerFn(name, Decl.FnDecl(name, params, body, returnType, isPrivate, isStatic))
            case Program.PDecl(Decl.ImportDecl(path, alias)) => processImport(path, alias, cwd, store)
            case Program.PDecl(Decl.StructDecl(name, fields, isLocked, typeParams)) => structEnv.register(name, StructDef(fields, isLocked, typeParams))
            case Program.PCmd(cmd) => execCmd(cmd, store)
            case Program.PExpr(expr) => println(getPrettyPrint(evalExpr(expr, store), structEnv))
            case Program.PBool(b) => println(evalBool(b, store))
            case Program.PImpl(structName, methods) => methods.foreach(m => fnEnv.methodTable((structName, m.name)) = m)
        })
    }
    protected def currentLineSource(): String = sourceLines(currentLine()-1).trim
    protected def throwError(msg: String, errorType: String = SimpError.Root): Nothing = {
        throw SimpError(errorType, s"on line ${currentLine()}\n${currentLineSource()}\n\u001b[31m$msg\u001b[0m")
    }
    
    
    
