package simp

object SimpUtils:
    def isNullable(t: SimpType): Boolean = t match {
        case SimpType.TypeInt | SimpType.TypeString | SimpType.TypeBool => false
        case _ => true
    }
    def getType(value: Value): SimpType = value match {
        case Value.IntVal(_)  => SimpType.TypeInt
        case Value.FloatVal(_)  => SimpType.TypeFloat
        case Value.StrVal(_)  => SimpType.TypeString
        case Value.BoolVal(_) => SimpType.TypeBool
        case Value.NullVal    => SimpType.TypeNull
        case Value.StructVal(typeName, _) => SimpType.TypeStruct(typeName)
        case Value.MapVal(_, keyType, valueType) => SimpType.TypeMap(keyType, valueType)
        case Value.PairVal(fst, snd) => SimpType.TypePair(getType(fst), getType(snd))
        case Value.TypeVal(_) => SimpType.TypeType
        case Value.RefVal(loc, refStore) => 
            refStore.load(loc) match {
                case Value.RefVal(_, _) => throw RuntimeException("Nested references are not supported")
                case v => getType(v)
            }
        case Value.ArrVal(elements) =>
            if elements.isEmpty then SimpType.TypeArr(SimpType.TypeInt)
            else elements.head match {
                case Value.RefVal(_, _) => throw RuntimeException("Arrays of references are not supported")
                case v => SimpType.TypeArr(getType(v))
            }
    }
    def getPrettyPrint(value: Value, visited: Set[AnyRef] = Set()): String = {
        value match {
            case Value.StrVal(s) => s
            case Value.IntVal(n) => n.toString
            case Value.FloatVal(n) => n.toString
            case Value.BoolVal(b) => b.toString
            case Value.NullVal => "null"
            case Value.RefVal(name,_) => s"Ref($name)"
            case Value.MapVal(_, keyType, valueType) => s"Map(${getSimpTypeName(keyType)} -> ${getSimpTypeName(valueType)})"
            case Value.TypeVal(t) => s"Type.${getSimpTypeName(t)}"
            case Value.PairVal(fst, snd) => s"(${getPrettyPrint(fst)}, ${getPrettyPrint(snd)})"
            case Value.StructVal(typeName, fields) => {
                if visited.contains(fields) then {
                    s"$typeName { ... }"
                } else {
                    val newVisited = visited + fields
                    s"$typeName { ${fields.map((k,v) => s"$k: ${getPrettyPrint(v, newVisited)}").mkString(", ")} }"
                }
            }
            case Value.ArrVal(elements) => "[" + elements.map(v => getPrettyPrint(v, visited)).mkString(", ") + "]"
        }
    }
    def deepCopyValue(value: Value, visited: Set[AnyRef] = Set()): Value = value match {
        case Value.IntVal(_)  => value 
        case Value.FloatVal(_)  => value 
        case Value.StrVal(_)  => value
        case Value.BoolVal(_) => value
        case Value.NullVal    => value
        case Value.TypeVal(_)    => value
        case Value.ArrVal(elements) => Value.ArrVal(scala.collection.mutable.ArrayBuffer.from(elements.map(e => deepCopyValue(e, visited))))
        case Value.MapVal(entries, keyType, valueType) => {
            Value.MapVal(
                scala.collection.mutable.Map(entries.map((k, v) => deepCopyValue(k) -> deepCopyValue(v)).toSeq*),
                keyType,
                valueType
            )
        }
        case Value.PairVal(fst, snd) => Value.PairVal(deepCopyValue(fst), deepCopyValue(snd))
        case Value.StructVal(typeName, fields) => {
            if visited.contains(fields) then {
                throw RuntimeException("deepCopy doesn't support Cyclical references")
            } else {
                val newVisisted = visited + fields
                Value.StructVal(typeName, scala.collection.mutable.Map(fields.map((k, v) => k -> deepCopyValue(v, newVisisted)).toSeq*))
            }  
        }
        case Value.RefVal(loc, refStore) => Value.RefVal(loc, refStore)
    }
    def checkType(value: Value, expected: SimpType, name: String): Unit = {
        val actual = getType(value);
        if actual == SimpType.TypeNull then {
            if !isNullable(expected) then {
                throw RuntimeException(s"'$name' of type $expected cannot be Null")
            }
        } else {
            value match {
                case Value.ArrVal(elements) if elements.isEmpty => {
                    expected match {
                        case SimpType.TypeArr(_) => return  
                        case _ => throw RuntimeException(s"Type mismatch for '$name': expected $expected, got []")
                    }
                }
                case _ => {
                    if actual != expected then {
                        throw RuntimeException(s"Type mismatch for '$name': expected $expected, got $actual")
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
    }
    def getTypeName(value: Value): String = value match {
        case Value.IntVal(_)  => "Int"
        case Value.FloatVal(_)  => "Float"
        case Value.StrVal(_)  => "Str"
        case Value.BoolVal(_) => "Bool"
        case Value.StructVal(typeName, _) => typeName
        case Value.RefVal(loc, refStore)  => s"ref ${getTypeName(refStore.load(loc))}"
        case Value.MapVal(_, keyType, valueType) => s"Map(${getSimpTypeName(keyType)} -> ${getSimpTypeName(valueType)})"
        case Value.PairVal(fst, snd) => s"Pair(${getTypeName(fst)}, ${getTypeName(snd)})"
        case Value.TypeVal(t) => s"Type.${getSimpTypeName(t)}"
        case Value.ArrVal(elements) =>
            if elements.isEmpty then "Unknown[]"
            else s"${getTypeName(elements.head)}[]"
        case Value.NullVal => "Null"
    }