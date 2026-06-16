package simp
import java.io.File
import SimpUtils.*

trait EvaluatorImport { self: Evaluator =>
    protected def processImport(path: String, alias: String, currentDir: String, store: Store ):  Unit = {
        val fullPath = java.io.File(s"$currentDir/$path").getCanonicalPath


        if importedFiles.contains(fullPath) then throwError(s"Circular import detected: $path")


        val existingAliases = completedImports.getOrElse(fullPath, Set())
        if existingAliases.contains(alias) then return
            
        importedFiles += fullPath

        val source = try {
            scala.io.Source.fromFile(fullPath).mkString
        } catch case _ => throwError(s"Could not find import $path")

        val newSource = source.split('\n').toList
        val tokens = Lexer(source, newSource).tokenise()
        val importStructEnv = StructEnv()
        val program = Parser(tokens._1, importStructEnv, tokens._2, newSource).parseProgram()


        val declaredNames = program.collect {
            case Program.PDecl(Decl.FnDecl(name, _, _, _)) => name
        }.toSet

        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body, returnType)) => {
                val qualifiedBody = qualifyBody(body, alias, declaredNames)
                fnEnv.registerFn(s"$alias::$name", Decl.FnDecl(s"$alias::$name", params, qualifiedBody, returnType))
            }
            case Program.PDecl(Decl.ImportDecl(path, alias)) => {
                val importDir = File(fullPath).getParentFile.getAbsolutePath
                processImport(path, alias, importDir, store)
            }
            case Program.PDecl(Decl.StructDecl(name, fields)) => structEnv.register(s"$alias::$name", StructDef(fields))
            case _ => throwError(s"Imports can only contain declarations")
        })

        importedFiles -= fullPath 
        completedImports(fullPath) = existingAliases + alias
    }


    protected def qualifyBody(cmd: Cmd, alias: String, declaredNames: Set[String]): Cmd = cmd match {
        case Cmd.Seq(a, b) => Cmd.Seq(qualifyBody(a, alias, declaredNames), qualifyBody(b, alias, declaredNames))
        case Cmd.If(cond, t, e, line) => Cmd.If(cond, qualifyBody(t, alias, declaredNames), qualifyBody(e, alias, declaredNames), line)
        case Cmd.While(cond, body, line) => Cmd.While(cond, qualifyBody(body, alias, declaredNames), line)
        case Cmd.Return(Some(expr), line) => Cmd.Return(Some(qualifyExpr(expr, alias, declaredNames)), line)
        case Cmd.Assign(loc, expr, line) => Cmd.Assign(loc, qualifyExpr(expr, alias, declaredNames), line)
        case other => other
    }

    protected def qualifyExpr(expr: Expr, alias: String, declaredNames: Set[String]): Expr = expr match {
        case Expr.FnCall(name, args) if declaredNames.contains(name) =>
            Expr.FnCall(s"$alias::$name", args.map(qualifyExpr(_, alias, declaredNames)))
        case Expr.BinaryOp(l, op, r) => 
            Expr.BinaryOp(qualifyExpr(l, alias, declaredNames), op, qualifyExpr(r, alias, declaredNames))
        case other => other
    }
}