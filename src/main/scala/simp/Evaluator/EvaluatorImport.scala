package simp
import java.io.File
import SimpUtils.*

// Bundles what's needed to re-qualify (rename to `alias::name`) references to
// this-file-local declarations once they're imported into another file's
// namespace: the alias itself, and the two sets of names actually declared
// at this file's top level (functions, structs) - anything NOT in these sets
// is left untouched, since it's either a builtin, a reference to some other
// already-imported/global name, or a generic type parameter.
case class ImportContext(alias: String, fnNames: Set[String], structNames: Set[String])

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


        val declaredFnNames = program.collect {
            case Program.PDecl(Decl.FnDecl(name, _, _, _, _, _)) => name
        }.toSet
        val declaredStructNames = program.collect {
            case Program.PDecl(Decl.StructDecl(name, _, _, _)) => name
        }.toSet
        val ctx = ImportContext(alias, declaredFnNames, declaredStructNames)

        program.foreach(p => p match {
            case Program.PDecl(Decl.FnDecl(name, params, body, returnType, isPrivate, isStatic)) => {
                val qualifiedParams = params.map((pname, ptype) => (pname, qualifyType(ptype, ctx)))
                val qualifiedBody = qualifyBody(body, ctx)
                fnEnv.registerFn(s"$alias::$name", Decl.FnDecl(s"$alias::$name", qualifiedParams, qualifiedBody, qualifyType(returnType, ctx), isPrivate, isStatic))
            }
            case Program.PDecl(Decl.ImportDecl(path, alias)) => {
                val importDir = File(fullPath).getParentFile.getAbsolutePath
                processImport(path, alias, importDir, store)
            }
            case Program.PDecl(Decl.StructDecl(name, fields, isLocked, typeParams)) => {
                val qualifiedFields = fields.map((fname, ftype, fdefault, fpriv) =>
                    (fname, qualifyType(ftype, ctx), fdefault.map(qualifyExpr(_, ctx)), fpriv)
                )
                structEnv.register(s"$alias::$name", StructDef(qualifiedFields, isLocked, typeParams))
            }
            case Program.PImpl(structName, methods) => {
                val qualifiedStructName = if declaredStructNames.contains(structName) then s"$alias::$structName" else structName
                methods.foreach(m => {
                    val qualifiedParams = m.params.map((pname, ptype) => (pname, qualifyType(ptype, ctx)))
                    val qualified: Decl.FnDecl = Decl.FnDecl(
                        m.name, qualifiedParams, qualifyBody(m.body, ctx),
                        qualifyType(m.returnType, ctx), m.isPrivate, m.isStatic
                    )
                    fnEnv.methodTable((qualifiedStructName, m.name)) = qualified
                })
            }
            case _ => throwError(s"Imports can only contain declarations")
        })

        importedFiles -= fullPath
        completedImports(fullPath) = existingAliases + alias
    }

    // Rewrites a `SimpType` so any reference to a struct declared in the
    // imported file itself becomes its qualified `alias::Name` form -
    // otherwise, e.g., a method's `self: Stack<T>` parameter (parsed against
    // the imported file's own, unqualified struct name) would never match a
    // `Stack` instance actually constructed as `alias::Stack` at runtime, and
    // every call would fail its own `self`-parameter type check.
    protected def qualifyType(t: SimpType, ctx: ImportContext): SimpType = t match {
        case SimpType.TypeStruct(name) if ctx.structNames.contains(name) => SimpType.TypeStruct(s"${ctx.alias}::$name")
        case SimpType.TypeStruct(name) => SimpType.TypeStruct(name)
        case SimpType.TypeArr(inner) => SimpType.TypeArr(qualifyType(inner, ctx))
        case SimpType.TypeRef(inner) => SimpType.TypeRef(qualifyType(inner, ctx))
        case SimpType.TypePair(fst, snd) => SimpType.TypePair(qualifyType(fst, ctx), qualifyType(snd, ctx))
        case SimpType.TypeMap(k, v) => SimpType.TypeMap(qualifyType(k, ctx), qualifyType(v, ctx))
        case other => other
    }

    // A full, exhaustive traversal of every `Cmd` shape - unlike an earlier
    // revision of this rewrite, which only descended into Seq/If/While/
    // Return/Assign, missing any recursive/self-referential call made from
    // inside a `for` loop, a `print`, a method call's own arguments, a
    // struct literal's field values, a nested block expression, etc.
    protected def qualifyBody(cmd: Cmd, ctx: ImportContext): Cmd = cmd match {
        case Cmd.Skip => Cmd.Skip
        case Cmd.Assign(loc, expr, line) => Cmd.Assign(loc, qualifyExpr(expr, ctx), line)
        case Cmd.ConstAssign(loc, expr, line) => Cmd.ConstAssign(loc, qualifyExpr(expr, ctx), line)
        case Cmd.TypeDecl(loc, t, line) => Cmd.TypeDecl(loc, qualifyType(t, ctx), line)
        case Cmd.Seq(a, b) => Cmd.Seq(qualifyBody(a, ctx), qualifyBody(b, ctx))
        case Cmd.If(cond, t, e, line) => Cmd.If(qualifyBool(cond, ctx), qualifyBody(t, ctx), qualifyBody(e, ctx), line)
        case Cmd.Scope(body) => Cmd.Scope(qualifyBody(body, ctx))
        case Cmd.While(cond, body, line) => Cmd.While(qualifyBool(cond, ctx), qualifyBody(body, ctx), line)
        case Cmd.For(variable, iterable, body, line) => Cmd.For(variable, qualifyExpr(iterable, ctx), qualifyBody(body, ctx), line)
        case Cmd.Print(value, line) => Cmd.Print(qualifyExpr(value, ctx), line)
        case Cmd.Return(exprOpt, line) => Cmd.Return(exprOpt.map(qualifyExpr(_, ctx)), line)
        case Cmd.ArrAssign(arr, index, value, line) => Cmd.ArrAssign(arr, qualifyExpr(index, ctx), qualifyExpr(value, ctx), line)
        case Cmd.ArrAssignNested(loc, indices, value, line) => Cmd.ArrAssignNested(loc, indices.map(qualifyExpr(_, ctx)), qualifyExpr(value, ctx), line)
        case Cmd.FieldAssign(loc, field, value, line) => Cmd.FieldAssign(loc, field, qualifyExpr(value, ctx), line)
        case Cmd.FieldIndexAssign(loc, field, index, value, line) => Cmd.FieldIndexAssign(loc, field, qualifyExpr(index, ctx), qualifyExpr(value, ctx), line)
        case Cmd.FieldIndexAssignNested(loc, field, indices, value, line) => Cmd.FieldIndexAssignNested(loc, field, indices.map(qualifyExpr(_, ctx)), qualifyExpr(value, ctx), line)
        case Cmd.Continue => Cmd.Continue
        case Cmd.Break => Cmd.Break
        case Cmd.Try(tryBody, catches, line) => Cmd.Try(qualifyBody(tryBody, ctx), catches.map(c => c.copy(body = qualifyBody(c.body, ctx))), line)
        case Cmd.Throw(errorType, expr, line) => Cmd.Throw(errorType, qualifyExpr(expr, ctx), line)
    }

    protected def qualifyExpr(expr: Expr, ctx: ImportContext): Expr = expr match {
        case Expr.Deref(loc) => Expr.Deref(loc)
        case Expr.Num(n) => Expr.Num(n)
        case Expr.Flt(n) => Expr.Flt(n)
        case Expr.Str(s) => Expr.Str(s)
        case Expr.Bool(b) => Expr.Bool(b)
        case Expr.BoolLift(b) => Expr.BoolLift(qualifyBool(b, ctx))
        case Expr.BinaryOp(l, op, r) => Expr.BinaryOp(qualifyExpr(l, ctx), op, qualifyExpr(r, ctx))
        case Expr.UnaryOp(l, op) => Expr.UnaryOp(qualifyExpr(l, ctx), op)
        case Expr.FnCall(name, args) if ctx.fnNames.contains(name) =>
            Expr.FnCall(s"${ctx.alias}::$name", args.map(qualifyExpr(_, ctx)))
        case Expr.FnCall(name, args) => Expr.FnCall(name, args.map(qualifyExpr(_, ctx)))
        case Expr.Ref(loc) => Expr.Ref(loc)
        case Expr.ArrLiteral(elements) => Expr.ArrLiteral(elements.map(qualifyExpr(_, ctx)))
        case Expr.ArrIndex(arr, index) => Expr.ArrIndex(qualifyExpr(arr, ctx), qualifyExpr(index, ctx))
        case Expr.StructLiteral(typeName, fields, typeArgs) => {
            val qualifiedName = if ctx.structNames.contains(typeName) then s"${ctx.alias}::$typeName" else typeName
            Expr.StructLiteral(
                qualifiedName,
                fields.map((k, v) => (k, qualifyExpr(v, ctx))),
                typeArgs.map(qualifyType(_, ctx))
            )
        }
        case Expr.StructTypeRef(typeName, typeArgs) => {
            val qualifiedName = if ctx.structNames.contains(typeName) then s"${ctx.alias}::$typeName" else typeName
            Expr.StructTypeRef(qualifiedName, typeArgs.map(qualifyType(_, ctx)))
        }
        case Expr.FieldAccess(e, field) => Expr.FieldAccess(qualifyExpr(e, ctx), field)
        case Expr.TypeLiteral(t) => Expr.TypeLiteral(qualifyType(t, ctx))
        case Expr.Pair(l, r) => Expr.Pair(qualifyExpr(l, ctx), qualifyExpr(r, ctx))
        case Expr.Match(e, arms) => Expr.Match(
            qualifyExpr(e, ctx),
            arms.map(a => MatchArm(qualifyPattern(a.pattern, ctx), a.guard.map(qualifyExpr(_, ctx)), qualifyExpr(a.body, ctx)))
        )
        case Expr.Block(cmds, result) => Expr.Block(cmds.map(qualifyBody(_, ctx)), qualifyExpr(result, ctx))
        case Expr.MethodCall(receiver, methodName, args) => Expr.MethodCall(qualifyExpr(receiver, ctx), methodName, args.map(qualifyExpr(_, ctx)))
        case Expr.Null => Expr.Null
    }

    protected def qualifyBool(b: BoolExpr, ctx: ImportContext): BoolExpr = b match {
        case BoolExpr.Literal(v) => BoolExpr.Literal(v)
        case BoolExpr.Compare(l, bop, r) => BoolExpr.Compare(qualifyExpr(l, ctx), bop, qualifyExpr(r, ctx))
        case BoolExpr.Not(inner) => BoolExpr.Not(qualifyBool(inner, ctx))
        case BoolExpr.And(l, r) => BoolExpr.And(qualifyBool(l, ctx), qualifyBool(r, ctx))
        case BoolExpr.Or(l, r) => BoolExpr.Or(qualifyBool(l, ctx), qualifyBool(r, ctx))
        case BoolExpr.FromExpr(e) => BoolExpr.FromExpr(qualifyExpr(e, ctx))
    }

    protected def qualifyPattern(pattern: Pattern, ctx: ImportContext): Pattern = pattern match {
        case Pattern.PWild => Pattern.PWild
        case Pattern.PLit(expr) => Pattern.PLit(qualifyExpr(expr, ctx))
        case Pattern.PVar(name) => Pattern.PVar(name)
        case Pattern.PStruct(typeName, fields) => {
            val qualifiedName = if ctx.structNames.contains(typeName) then s"${ctx.alias}::$typeName" else typeName
            Pattern.PStruct(qualifiedName, fields.map((k, p) => (k, qualifyPattern(p, ctx))))
        }
        case Pattern.PPair(fst, snd) => Pattern.PPair(qualifyPattern(fst, ctx), qualifyPattern(snd, ctx))
    }
}
