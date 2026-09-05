package simp

object SimpUtils:
    def isNullable(t: SimpType): Boolean = t match {
        case SimpType.TypeInt | SimpType.TypeString | SimpType.TypeBool | SimpType.TypeFloat => false
        case _ => true
    }
    def getType(value: Value): SimpType = value match {
        case Value.IntVal(_)  => SimpType.TypeInt
        case Value.FloatVal(_)  => SimpType.TypeFloat
        case Value.StrVal(_)  => SimpType.TypeString
        case Value.BoolVal(_) => SimpType.TypeBool
        case Value.NullVal    => SimpType.TypeNull
        case Value.StructVal(typeName, _, _) => SimpType.TypeStruct(typeName)
        case Value.MapVal(_, keyType, valueType) => SimpType.TypeMap(keyType, valueType)
        case Value.PairVal(fst, snd) => SimpType.TypePair(getType(fst), getType(snd))
        case Value.TypeVal(_) => SimpType.TypeType
        case Value.StructTypeVal(_, _) => SimpType.TypeType
        case Value.RefVal(loc, refStore) => 
            refStore.load(loc) match {
                case Value.RefVal(_, _) => throw RuntimeException("Nested references are not supported")
                case v => getType(v)
            }
        case Value.ArrVal(elements) =>
            if elements.isEmpty then SimpType.TypeArr(elements.declaredType.getOrElse(SimpType.TypeInt))
            else elements.head match {
                case Value.RefVal(_, _) => throw RuntimeException("Arrays of references are not supported")
                case v => SimpType.TypeArr(getType(v))
            }
    }
    def getPrettyPrint(value: Value, structEnv: StructEnv, visited: Set[AnyRef] = Set()): String = {
        value match {
            case Value.StrVal(s) => s
            case Value.IntVal(n) => n.toString
            case Value.FloatVal(n) => n.toString
            case Value.BoolVal(b) => b.toString
            case Value.NullVal => "null"
            case Value.RefVal(name,_) => s"Ref($name)"
            case Value.MapVal(_, keyType, valueType) => s"Map(${getSimpTypeName(keyType)} -> ${getSimpTypeName(valueType)})"
            case Value.TypeVal(t) => s"Type.${getSimpTypeName(t)}"
            case Value.StructTypeVal(typeName, typeArgs) =>
                if typeArgs.isEmpty then s"Type.$typeName" else s"Type.$typeName<${typeArgs.map(getSimpTypeName).mkString(", ")}>"
            case Value.PairVal(fst, snd) => s"(${getPrettyPrint(fst, structEnv, visited)}, ${getPrettyPrint(snd, structEnv, visited)})"
            case Value.StructVal(typeName, fields, _) => {
                if visited.contains(fields) then {
                    s"$typeName { ... }"
                } else {
                    val newVisited = visited + fields
                    s"$typeName { ${fields.map { (k, v) =>
                    val isPriv = structEnv.lookup(typeName).fields.find(_(0) == k).exists(_(3))
                    if (isPriv) s"$k: ???"
                    else s"$k: ${getPrettyPrint(v, structEnv, newVisited)}"
                    }.mkString(", ")} }"
                }
            }
            case Value.ArrVal(elements) => {
                if visited.contains(elements) then {
                    "[...]"
                } else {
                    val newVisited = visited + elements
                    "[" + elements.map(v => getPrettyPrint(v, structEnv, newVisited)).mkString(", ") + "]"
                }
            }
        }
    }
    def deepCopyValue(value: Value, visited: Set[AnyRef] = Set()): Value = value match {
        case Value.IntVal(_)  => value 
        case Value.FloatVal(_)  => value 
        case Value.StrVal(_)  => value
        case Value.BoolVal(_) => value
        case Value.NullVal    => value
        case Value.TypeVal(_)    => value
        case Value.StructTypeVal(_, _) => value
        case Value.ArrVal(elements) => Value.ArrVal(TypedArray.from(elements.map(e => deepCopyValue(e, visited))))
        case Value.MapVal(entries, keyType, valueType) => {
            Value.MapVal(
                scala.collection.mutable.Map(entries.map((k, v) => deepCopyValue(k) -> deepCopyValue(v)).toSeq*),
                keyType,
                valueType
            )
        }
        case Value.PairVal(fst, snd) => Value.PairVal(deepCopyValue(fst), deepCopyValue(snd))
        case Value.StructVal(typeName, fields, typeArgs) => {
            if visited.contains(fields) then {
                throw SimpError("ValueError", "deepCopy doesn't support Cyclical references")
            } else {
                val newVisisted = visited + fields
                Value.StructVal(typeName, scala.collection.mutable.Map(fields.map((k, v) => k -> deepCopyValue(v, newVisisted)).toSeq*), typeArgs)
            }
        }
        case Value.RefVal(loc, refStore) => Value.RefVal(loc, refStore)
    }
    // A type containing a generic type parameter (e.g. `T` or `T[]` inside a
    // generic struct/method) must be resolved against the currently-bound
    // concrete type(s) before it can be type-checked - see resolveTypeParams.
    def containsTypeParam(t: SimpType): Boolean = t match {
        case SimpType.TypeParam(_) => true
        case SimpType.TypeArr(inner) => containsTypeParam(inner)
        case SimpType.TypeRef(inner) => containsTypeParam(inner)
        case SimpType.TypePair(fst, snd) => containsTypeParam(fst) || containsTypeParam(snd)
        case SimpType.TypeMap(k, v) => containsTypeParam(k) || containsTypeParam(v)
        case _ => false
    }
    // Substitutes every `TypeParam(name)` found in `t` with its bound concrete
    // type from `typeBindings` (the current generic struct/method's type
    // arguments). Throws if a type parameter has no binding - this should never
    // happen for a well-formed program, since a generic struct's type argument
    // is bound at construction time and threaded through every method call on
    // that instance; seeing one unbound indicates a real bug, not something to
    // silently paper over by allowing anything through.
    def resolveTypeParams(t: SimpType, typeBindings: Map[String, SimpType]): SimpType = t match {
        case SimpType.TypeParam(name) => typeBindings.getOrElse(name, throw SimpError("TypeError", s"Unbound type parameter '$name'"))
        case SimpType.TypeArr(inner) => SimpType.TypeArr(resolveTypeParams(inner, typeBindings))
        case SimpType.TypeRef(inner) => SimpType.TypeRef(resolveTypeParams(inner, typeBindings))
        case SimpType.TypePair(fst, snd) => SimpType.TypePair(resolveTypeParams(fst, typeBindings), resolveTypeParams(snd, typeBindings))
        case SimpType.TypeMap(k, v) => SimpType.TypeMap(resolveTypeParams(k, typeBindings), resolveTypeParams(v, typeBindings))
        case other => other
    }
    def checkType(value: Value, expected: SimpType, name: String, typeBindings: Map[String, SimpType] = Map()): Unit = {
        val resolved = if containsTypeParam(expected) then resolveTypeParams(expected, typeBindings) else expected
        val actual = getType(value);
        if actual == SimpType.TypeNull then {
            if !isNullable(resolved) then {
                throw SimpError("TypeError", s"'$name' of type $resolved cannot be Null")
            }
        } else {
            value match {
                case Value.ArrVal(elements) if elements.isEmpty => {
                    resolved match {
                        case SimpType.TypeArr(innerExpected) => {
                            // An empty array with no declared element type carries no
                            // information to check against, and stays permissive (it
                            // satisfies any array type). One that *does* carry a
                            // declared type (set by `x : T[];`, Phase 5.3/7.1) must
                            // actually match the expected element type - otherwise an
                            // empty `Int[]` would silently satisfy a `Str[]`-typed slot.
                            elements.declaredType.foreach(declared => {
                                val resolvedDeclared = if containsTypeParam(declared) then resolveTypeParams(declared, typeBindings) else declared
                                if resolvedDeclared != innerExpected then {
                                    throw SimpError("TypeError", s"Type mismatch for '$name': expected $resolved, got ${SimpType.TypeArr(resolvedDeclared)}")
                                }
                            })
                        }
                        case _ => throw SimpError("TypeError", s"Type mismatch for '$name': expected $resolved, got []")
                    }
                }
                case _ => {
                    if actual != resolved then {
                        throw SimpError("TypeError", s"Type mismatch for '$name': expected $resolved, got $actual")
                    }
                }
            }
        }
    }
    def getSimpTypeName(t: SimpType): String = t match {
        case SimpType.TypeInt    => "Int"
        case SimpType.TypeString => "Str"
        case SimpType.TypeBool   => "Bool"
        case SimpType.TypeFloat  => "Float"
        case SimpType.TypeNull   => "Void"
        case SimpType.TypeType   => "Type"
        case SimpType.TypeArr(inner) => s"${getSimpTypeName(inner)}[]"
        case SimpType.TypeStruct(name) => name
        case SimpType.TypeRef(inner) => s"ref ${getSimpTypeName(inner)}"
        case SimpType.TypeMap(k, v) => s"Map(${getSimpTypeName(k)}, ${getSimpTypeName(v)})"
        case SimpType.TypePair(fst, snd) => s"Pair(${getSimpTypeName(fst)}, ${getSimpTypeName(snd)})"
        case SimpType.TypeParam(name) => name
    }
    def getTypeName(value: Value): String = value match {
        case Value.IntVal(_)  => "Int"
        case Value.FloatVal(_)  => "Float"
        case Value.StrVal(_)  => "Str"
        case Value.BoolVal(_) => "Bool"
        case Value.StructVal(typeName, _, _) => typeName
        case Value.RefVal(loc, refStore)  => s"ref ${getTypeName(refStore.load(loc))}"
        case Value.MapVal(_, keyType, valueType) => s"Map(${getSimpTypeName(keyType)} -> ${getSimpTypeName(valueType)})"
        case Value.PairVal(fst, snd) => s"Pair(${getTypeName(fst)}, ${getTypeName(snd)})"
        case Value.TypeVal(t) => s"Type.${getSimpTypeName(t)}"
        case Value.StructTypeVal(typeName, _) => s"Type.$typeName"
        case Value.ArrVal(elements) =>
            if elements.isEmpty then elements.declaredType.map(t => s"${getSimpTypeName(t)}[]").getOrElse("Unknown[]")
            else s"${getTypeName(elements.head)}[]"
        case Value.NullVal => "Null"
    }


    def prettyPrintExpr(expr: Expr, indent: Int = 0): String = {
        val pad = "  " * indent
        expr match {
            case Expr.Num(n)           => s"${pad}Num($n)"
            case Expr.Flt(f)           => s"${pad}Flt($f)"
            case Expr.Bool(b)          => s"${pad}Bool($b)"
            case Expr.Str(s)           => s"${pad}Str(\"$s\")"
            case Expr.Null             => s"${pad}Null"
            case Expr.Ref(loc)         => s"${pad}Ref($loc)"
            case Expr.Deref(loc)       => s"${pad}Deref($loc)"
            case Expr.BoolLift(b)      => s"${pad}BoolLift(\n${prettyPrintBool(b, indent + 1)}\n${pad})"
            case Expr.BinaryOp(l, op, r) =>
                s"${pad}BinaryOp($op,\n${prettyPrintExpr(l, indent + 1)},\n${prettyPrintExpr(r, indent + 1)}\n${pad})"
            case Expr.UnaryOp(e, op)   =>
                s"${pad}UnaryOp($op,\n${prettyPrintExpr(e, indent + 1)}\n${pad})"
            case Expr.ArrLiteral(elems) =>
                val inner = elems.map(prettyPrintExpr(_, indent + 1)).mkString(",\n")
                s"${pad}ArrLiteral(\n$inner\n${pad})"
            case Expr.ArrIndex(arr, idx) =>
                s"${pad}ArrIndex(\n${prettyPrintExpr(arr, indent + 1)},\n${prettyPrintExpr(idx, indent + 1)}\n${pad})"
            case Expr.FieldAccess(e, f) =>
                s"${pad}FieldAccess($f,\n${prettyPrintExpr(e, indent + 1)}\n${pad})"
            case Expr.FnCall(name, args) =>
                val inner = args.map(prettyPrintExpr(_, indent + 1)).mkString(",\n")
                s"${pad}FnCall($name,\n$inner\n${pad})"
            case Expr.MethodCall(recv, name, args) =>
                val inner = args.map(prettyPrintExpr(_, indent + 1)).mkString(",\n")
                s"${pad}MethodCall($name,\n${prettyPrintExpr(recv, indent + 1)},\n$inner\n${pad})"
            case Expr.StructLiteral(name, fields, typeArgs) =>
                val inner = fields.map((f, e) => s"${"  " * (indent+1)}$f:\n${prettyPrintExpr(e, indent + 2)}").mkString(",\n")
                val typeArgsStr = if typeArgs.isEmpty then "" else s"<${typeArgs.map(getSimpTypeName).mkString(", ")}>"
                s"${pad}StructLiteral($name$typeArgsStr,\n$inner\n${pad})"
            case Expr.StructTypeRef(name, typeArgs) =>
                val typeArgsStr = if typeArgs.isEmpty then "" else s"<${typeArgs.map(getSimpTypeName).mkString(", ")}>"
                s"${pad}StructTypeRef($name$typeArgsStr)"
            case Expr.Pair(fst, snd) =>
                s"${pad}Pair(\n${prettyPrintExpr(fst, indent + 1)},\n${prettyPrintExpr(snd, indent + 1)}\n${pad})"
            case Expr.Block(cmds, result) =>
                val inner = cmds.map(prettyPrintCmd(_, indent + 1)).mkString("\n")
                s"${pad}Block(\n$inner,\n${prettyPrintExpr(result, indent + 1)}\n${pad})"
            case Expr.Match(expr, arms) =>
                val inner = arms.map(a => s"${"  " * (indent+1)}case ${a.pattern} =>\n${prettyPrintExpr(a.body, indent + 2)}").mkString(",\n")
                s"${pad}Match(\n${prettyPrintExpr(expr, indent + 1)},\n$inner\n${pad})"
            case Expr.TypeLiteral(t)   => s"${pad}TypeLiteral($t)"
        }
    }

    def prettyPrintBool(bool: BoolExpr, indent: Int = 0): String = {
        val pad = "  " * indent
        bool match {
            case BoolExpr.Literal(b)         => s"${pad}Literal($b)"
            case BoolExpr.FromExpr(e)        => s"${pad}FromExpr(\n${prettyPrintExpr(e, indent + 1)}\n${pad})"
            case BoolExpr.Compare(l, bop, r) =>
                s"${pad}Compare($bop,\n${prettyPrintExpr(l, indent + 1)},\n${prettyPrintExpr(r, indent + 1)}\n${pad})"
            case BoolExpr.And(l, r) =>
                s"${pad}And(\n${prettyPrintBool(l, indent + 1)},\n${prettyPrintBool(r, indent + 1)}\n${pad})"
            case BoolExpr.Or(l, r) =>
                s"${pad}Or(\n${prettyPrintBool(l, indent + 1)},\n${prettyPrintBool(r, indent + 1)}\n${pad})"
            case BoolExpr.Not(b) =>
                s"${pad}Not(\n${prettyPrintBool(b, indent + 1)}\n${pad})"
        }
    }

    def prettyPrintCmd(cmd: Cmd, indent: Int = 0): String = {
        val pad = "  " * indent
        cmd match {
            case Cmd.Skip                      => s"${pad}Skip"
            case Cmd.Seq(l, r)                 => s"${prettyPrintCmd(l, indent)}\n${prettyPrintCmd(r, indent)}"
            case Cmd.Assign(loc, expr, _)      => s"${pad}Assign($loc,\n${prettyPrintExpr(expr, indent + 1)}\n${pad})"
            case Cmd.ConstAssign(loc, expr, _) => s"${pad}ConstAssign($loc,\n${prettyPrintExpr(expr, indent + 1)}\n${pad})"
            case Cmd.If(cond, t, e, _)         =>
                s"${pad}If(\n${prettyPrintBool(cond, indent + 1)},\n${prettyPrintCmd(t, indent + 1)},\n${prettyPrintCmd(e, indent + 1)}\n${pad})"
            case Cmd.While(cond, body, _)      =>
                s"${pad}While(\n${prettyPrintBool(cond, indent + 1)},\n${prettyPrintCmd(body, indent + 1)}\n${pad})"
            case Cmd.For(v, iter, body, _)     =>
                s"${pad}For($v,\n${prettyPrintExpr(iter, indent + 1)},\n${prettyPrintCmd(body, indent + 1)}\n${pad})"
            case Cmd.Print(expr, _)            => s"${pad}Print(\n${prettyPrintExpr(expr, indent + 1)}\n${pad})"
            case Cmd.Return(Some(e), _)        => s"${pad}Return(\n${prettyPrintExpr(e, indent + 1)}\n${pad})"
            case Cmd.Return(None, _)           => s"${pad}Return"
            case Cmd.Scope(body)               => s"${pad}Scope(\n${prettyPrintCmd(body, indent + 1)}\n${pad})"
            case Cmd.Break                     => s"${pad}Break"
            case Cmd.Continue                  => s"${pad}Continue"
            case Cmd.ArrAssign(arr, idx, v, _) =>
                s"${pad}ArrAssign($arr,\n${prettyPrintExpr(idx, indent + 1)},\n${prettyPrintExpr(v, indent + 1)}\n${pad})"
            case Cmd.Try(tryBody, catches, _) => {
                val catchesStr = catches.map(c =>
                    s"${pad}  Catch(${c.errorType}${c.bindVar.map(v => s" as $v").getOrElse("")},\n${prettyPrintCmd(c.body, indent + 2)}\n${pad}  )"
                ).mkString(",\n")
                s"${pad}Try(\n${prettyPrintCmd(tryBody, indent + 1)},\n$catchesStr\n${pad})"
            }
            case Cmd.Throw(errorType, expr, _) =>
                s"${pad}Throw(${errorType.getOrElse("Error")},\n${prettyPrintExpr(expr, indent + 1)}\n${pad})"
            case Cmd.FieldAssign(loc, f, v, _) =>
                s"${pad}FieldAssign($loc.$f,\n${prettyPrintExpr(v, indent + 1)}\n${pad})"
            case _ => s"${pad}${cmd.toString}"
        }
    }