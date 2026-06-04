package simp 

import scala.io.Source._

import scala.math._

import scala.util.Random

import SimpUtils.*

object Builtins:
    def register(fnEnv: FunctionEnv): Unit = {
        // len - Length of a String or Array
        fnEnv.registerBuiltin("len", args => args match {
                case List(Value.StrVal(s)) => Value.IntVal(s.length)
                case List(Value.ArrVal(s)) => Value.IntVal(s.length)
                case _ => throw RuntimeException("len expects a string or array")
            }
        )
        // upper - Makes a string uppercase
        fnEnv.registerBuiltin("upper", args => args match {
                case List(Value.StrVal(s)) => Value.StrVal(s.toUpperCase)
                case _ => throw RuntimeException("upper expects a string")
            }
        )
        // lower - Makes a string lowercase
        fnEnv.registerBuiltin("lower", args => args match {
                case List(Value.StrVal(s)) => Value.StrVal(s.toLowerCase)
                case _ => throw RuntimeException("lower expects a string")
            }
        )
        // trim - Trims leading/trailing whitespace from a string
        fnEnv.registerBuiltin("trim", args => args match {
                case List(Value.StrVal(s)) => Value.StrVal(s.trim)
                case _ => throw RuntimeException("trim expects a string")
            }
        )
        // reverse - Reverses a string or Array
        fnEnv.registerBuiltin("reverse", args => args match {
                case List(Value.StrVal(s)) => Value.StrVal(s.reverse)
                case List(Value.ArrVal(s)) => Value.ArrVal(s.reverse)
                case _ => throw RuntimeException("reverse expects a string or array")
            }
        )
        // contains - Checks if a string or array contains a value
        fnEnv.registerBuiltin("contains", args => args match {
                case List(Value.StrVal(s1), Value.StrVal(s2)) => Value.BoolVal(s1.contains(s2))
                case List(Value.ArrVal(arr), term) => Value.BoolVal(arr.contains(term))
                case _ => throw RuntimeException("contains expects 2 strings, or an array and value")
            }
        )
        // startsWith - Checks if a string starts with a value
        fnEnv.registerBuiltin("startsWith", args => args match {
                case List(Value.StrVal(s1), Value.StrVal(s2)) => Value.BoolVal(s1.startsWith(s2))
                case _ => throw RuntimeException("startsWith expects 2 strings (string, contained_string)")
            }
        )
        // endsWith - Checks if a string ends with a value
        fnEnv.registerBuiltin("endsWith", args => args match {
                case List(Value.StrVal(s1), Value.StrVal(s2)) => Value.BoolVal(s1.endsWith(s2))
                case _ => throw RuntimeException("endsWith expects 2 strings (string, contained_string)")
            }
        )
        // replace - Replaces all occurences of a target sequence
        fnEnv.registerBuiltin("replace", args => args match {
                case List(Value.StrVal(s1), Value.StrVal(s2), Value.StrVal(s3)) => Value.StrVal(s1.replace(s2, s3))
                case _ => throw RuntimeException("replace expects 3 strings (string, target, replace)")
            }
        )
        // substr - Gets a substring of the target string
        fnEnv.registerBuiltin("substr", args => args match {
                case List(Value.StrVal(s1), Value.IntVal(i1), Value.IntVal(i2)) => Value.StrVal(s1.substring(i1, i2))
                case _ => throw RuntimeException("substr expects 1 string & 2 ints (string, start, end)")
            }
        )
        // slice - Gets a slice of the target array
        fnEnv.registerBuiltin("slice", args => args match {
                case List(Value.ArrVal(arr), Value.IntVal(i1), Value.IntVal(i2)) => Value.ArrVal(arr.slice(i1, i2))
                case _ => throw RuntimeException("slice expects 1 array & 2 ints (arr, start, end)")
            }
        )
        // indexOf - Gets the index of a substring, or -1 if it fails
        fnEnv.registerBuiltin("indexOf", args => args match {
                case List(Value.StrVal(s1), Value.StrVal(s2)) => Value.IntVal(s1.indexOf(s2))
                case _ => throw RuntimeException("indexOf expects 2 strings (string, contained_string)")
            }
        )


        // isInt - Returns true if the argument is an integer
        fnEnv.registerBuiltin("isInt", args => args match {
                case List(Value.IntVal(i)) => Value.BoolVal(true)
                case List(x) => Value.BoolVal(false)
                case _ => throw RuntimeException("isInt expects 1 argument")
            }
        )
        // isStr - Returns true if the argument is a string
        fnEnv.registerBuiltin("isStr", args => args match {
                case List(Value.StrVal(s)) => Value.BoolVal(true)
                case List(x) => Value.BoolVal(false)
                case _ => throw RuntimeException("isStr expects 1 argument")
            }
        )
        // isBool - Returns true if the argument is a boolean
        fnEnv.registerBuiltin("isBool", args => args match {
                case List(Value.BoolVal(b)) => Value.BoolVal(true)
                case List(x) => Value.BoolVal(false)
                case _ => throw RuntimeException("isBool expects 1 argument")
            }
        )
        // isFloat - Returns true if the argument is a float
        fnEnv.registerBuiltin("isFloat", args => args match {
                case List(Value.FloatVal(i)) => Value.BoolVal(true)
                case List(x) => Value.BoolVal(false)
                case _ => throw RuntimeException("isFloat expects 1 argument")
            }
        )

        // toStr - Converts a value into a String
        fnEnv.registerBuiltin("toStr", args => args match {
                case List(x) => Value.StrVal(getPrettyPrint(x))
                case _ => throw RuntimeException("toStr expects 1 argument")
            }
        )
        // toInt - Converts a String or a float into an Int
        fnEnv.registerBuiltin("toInt", args => args match {
                case List(Value.StrVal(s)) => Value.IntVal(s.toInt)
                case List(Value.FloatVal(s)) => Value.IntVal(s.toInt)
                case _ => throw RuntimeException("toInt expects 1 string")
            }
        )
        // toFloat - Converts a String or an int into a float
        fnEnv.registerBuiltin("toFloat", args => args match {
                case List(Value.StrVal(s)) => Value.FloatVal(s.toDouble)
                case List(Value.IntVal(s)) => Value.FloatVal(s.toDouble)
                case _ => throw RuntimeException("toInt expects 1 string")
            }
        )
        // toBool - Converts a String into a Boolean
        fnEnv.registerBuiltin("toBool", args => args match {
                case List(Value.StrVal(s)) => Value.BoolVal(s.toBoolean)
                case _ => throw RuntimeException("toBool expects 1 string")
            }
        )
        // toArr - Converts a String into an Array of individual character strings
        fnEnv.registerBuiltin("toArr", args => args match {
                case List(Value.StrVal(s)) => {
                    Value.ArrVal(scala.collection.mutable.ArrayBuffer(
                        s.map(c => Value.StrVal(c.toString))*
                    ))
                }
                case _ => throw RuntimeException("toArr expects 1 string")
            }
        )
        // split - Splits a string into an array with a delimiter
        fnEnv.registerBuiltin("split", args => args match {
            case List(Value.StrVal(s), Value.StrVal(delimiter)) =>
                Value.ArrVal(scala.collection.mutable.ArrayBuffer(
                    s.split(scala.util.matching.Regex.quote(delimiter))
                    .map(Value.StrVal(_))
                    .toSeq*
                ))
            case _ => throw RuntimeException("split expects a string and a delimiter")
        })
        // range
        fnEnv.registerBuiltin("range", args => args match {
            case List(Value.IntVal(end)) =>
                Value.ArrVal(scala.collection.mutable.ArrayBuffer((0 until end).map(Value.IntVal(_))* ))
            case List(Value.IntVal(start), Value.IntVal(end)) =>
                Value.ArrVal(scala.collection.mutable.ArrayBuffer((start until end).map(Value.IntVal(_))*))
            case List(Value.IntVal(start), Value.IntVal(end), Value.IntVal(step)) =>
                Value.ArrVal(scala.collection.mutable.ArrayBuffer((start until end by step).map(Value.IntVal(_))*))
            case _ => throw RuntimeException("range expects 1-3 integer arguments")
        })
        // abs - Absolute Value
        fnEnv.registerBuiltin("abs", args => args match {
                case List(Value.IntVal(s)) => Value.IntVal(s.abs)
                case List(Value.FloatVal(s)) => Value.FloatVal(s.abs)
                case _ => throw RuntimeException("abs expects 1 integer / float")
            }
        )
        // max - Max of 2 Integers / Floats
        fnEnv.registerBuiltin("max", args => args match {
                case List(Value.IntVal(s1), Value.IntVal(s2)) => {
                    if s1 >= s2 then {Value.IntVal(s1)} else {Value.IntVal(s2)}
                }
                case List(Value.FloatVal(s1), Value.FloatVal(s2)) => {
                    if s1 >= s2 then {Value.FloatVal(s1)} else {Value.FloatVal(s2)}
                }
                case List(Value.IntVal(s1), Value.FloatVal(s2)) => {
                    if s1 >= s2 then {Value.FloatVal(s1)} else {Value.FloatVal(s2)}
                }
                case List(Value.FloatVal(s1), Value.IntVal(s2)) => {
                    if s1 >= s2 then {Value.FloatVal(s1)} else {Value.FloatVal(s2)}
                }
                case _ => throw RuntimeException("max expects 2 integers / floats")
            }
        )
        // min - Min of 2 Integers
        fnEnv.registerBuiltin("min", args => args match {
                case List(Value.IntVal(s1), Value.IntVal(s2)) => {
                    if s1 >= s2 then {Value.IntVal(s2)} else {Value.IntVal(s1)}
                }
                case List(Value.FloatVal(s1), Value.FloatVal(s2)) => {
                    if s1 >= s2 then {Value.FloatVal(s2)} else {Value.FloatVal(s1)}
                }
                case List(Value.IntVal(s1), Value.FloatVal(s2)) => {
                    if s1 >= s2 then {Value.FloatVal(s2)} else {Value.FloatVal(s1)}
                }
                case List(Value.FloatVal(s1), Value.IntVal(s2)) => {
                    if s1 >= s2 then {Value.FloatVal(s2)} else {Value.FloatVal(s1)}
                }
                case _ => throw RuntimeException("min expects 2 integers / floats")
            }
        )
        // clamp - Clamps Integer between min and max values
        fnEnv.registerBuiltin("clamp", args => args match {
                case List(Value.IntVal(i), Value.IntVal(min), Value.IntVal(max)) => {
                    i match {
                        case x if i < min => Value.IntVal(min)
                        case x if i > max => Value.IntVal(max)
                        case _ => Value.IntVal(i)
                    }
                }
                case List(Value.FloatVal(i), Value.IntVal(min), Value.IntVal(max)) => {
                    i match {
                        case x if i < min => Value.FloatVal(min)
                        case x if i > max => Value.FloatVal(max)
                        case _ => Value.FloatVal(i)
                    }
                }
                case List(Value.FloatVal(i), Value.FloatVal(min), Value.FloatVal(max)) => {
                    i match {
                        case x if i < min => Value.FloatVal(min)
                        case x if i > max => Value.FloatVal(max)
                        case _ => Value.FloatVal(i)
                    }
                }
                case _ => throw RuntimeException("clamp expects 3 integers / floats")
            }
        )

        // pow - Power
        fnEnv.registerBuiltin("pow", args => args match {
            case List(Value.IntVal(base), Value.IntVal(exp)) if exp > 0 => {
                var result = 1
                var currentBase = base
                var e = exp
                while e > 0 do {
                    if e % 2 == 1 then result *= currentBase
                    currentBase *= currentBase
                    e /= 2
                }
                Value.IntVal(result)
            }
            case List(Value.IntVal(base), Value.IntVal(exp)) => {
                Value.FloatVal(pow(base.toDouble, exp.toDouble))
            }
            case List(Value.IntVal(base), Value.FloatVal(exp)) => {
                Value.FloatVal(pow(base.toDouble, exp))
            }
            case List(Value.FloatVal(base), Value.IntVal(exp)) => {
                Value.FloatVal(pow(base, exp.toDouble))
            }
            case List(Value.FloatVal(base), Value.FloatVal(exp)) => {
                Value.FloatVal(pow(base, exp))
            }
            case _ => throw RuntimeException("pow expects 2 integers / floats")
        })
        // sqrt - Square Root
        fnEnv.registerBuiltin("sqrt", args => args match {
            case List(Value.IntVal(i)) => Value.FloatVal(sqrt(i))
            case List(Value.FloatVal(f)) => Value.FloatVal(sqrt(f))
            case _ => throw RuntimeException("sqrt expects 1 integer / float")
        })
        // ln - natural log
        fnEnv.registerBuiltin("ln", args => args match {
            case List(Value.IntVal(a)) => Value.FloatVal(log(a))
            case List(Value.FloatVal(a)) => Value.FloatVal(log(a))
            case _ => throw RuntimeException("ln expects 1 integer / float")
        })
        // log10 - log base 10
        fnEnv.registerBuiltin("log10", args => args match {
            case List(Value.IntVal(a)) => Value.FloatVal(log10(a))
            case List(Value.FloatVal(a)) => Value.FloatVal(log10(a))
            case _ => throw RuntimeException("log10 expects 1 integer / float")
        })
        // log - log with arbitrary base
        fnEnv.registerBuiltin("log", args => args match {
            case List(Value.IntVal(a), Value.IntVal(b)) => Value.FloatVal(log(a) / log(b))
            case List(Value.IntVal(a), Value.FloatVal(b)) => Value.FloatVal(log(a) / log(b))
            case List(Value.FloatVal(a), Value.IntVal(b)) => Value.FloatVal(log(a) / log(b))
            case List(Value.FloatVal(a), Value.FloatVal(b)) => Value.FloatVal(log(a) / log(b))
            case _ => throw RuntimeException("log expects 2 integers / floats")
        })
        fnEnv.registerBuiltin("pi", args => args match {
            case List() => Value.FloatVal(Pi)
            case _ => throw RuntimeException("pi takes no arguments")
        })

        fnEnv.registerBuiltin("e", args => args match {
            case List() => Value.FloatVal(E)
            case _ => throw RuntimeException("e takes no arguments")
        })
        // assert - If false, throws an Error
        fnEnv.registerBuiltin("assert", args => args match {
                case List(Value.BoolVal(b)) => {
                    if !b then throw RuntimeException("Assertion failed")
                    Value.BoolVal(true)
                }
                case List(Value.BoolVal(b), Value.StrVal(msg)) => {
                    if !b then throw RuntimeException(s"Assertion failed: $msg")
                    Value.BoolVal(true)
                }
                case _ => throw RuntimeException("assert expects a boolean and an optional message")
            }
        )
        

        // Inputs

        fnEnv.registerBuiltin("input", args => args match {
            case List(Value.StrVal(prompt)) =>
                print(prompt)
                Value.StrVal(scala.io.StdIn.readLine())
            case List() =>
                Value.StrVal(scala.io.StdIn.readLine())
            case _ => throw RuntimeException("input expects an optional string prompt")
        })

        fnEnv.registerBuiltin("inputInt", args => args match {
            case List(Value.StrVal(prompt)) => {
                print(prompt)
                val line = scala.io.StdIn.readLine()
                try Value.IntVal(line.toInt)
                catch case _ => throw RuntimeException(s"Could not convert '$line' to Int")
            }
            case List() => {
                val line = scala.io.StdIn.readLine()
                try Value.IntVal(line.toInt)
                catch case _ => throw RuntimeException(s"Could not convert '$line' to Int")
            }
            case _ => throw RuntimeException("inputInt expects an optional string prompt")
        })
        fnEnv.registerBuiltin("intSqrt", args => args match {
            case List(Value.IntVal(n)) =>
                if n < 0 then throw RuntimeException("sqrt of negative number")
                Value.IntVal(math.sqrt(n.toDouble).toInt)
            case _ => throw RuntimeException("sqrt expects an integer")
        })
        fnEnv.registerBuiltin("inputBool", args => args match {
            case List(Value.StrVal(prompt)) => {
                print(prompt)
                val line = scala.io.StdIn.readLine().toLowerCase
                line match {
                    case "y" | "yes" | "true" | "t" | "1" => Value.BoolVal(true)
                    case "n" | "no" | "false" | "f" | "0" => Value.BoolVal(true)
                    case _ => throw RuntimeException(s"Could not convert '$line' to Bool")
                }
            }
            case List() => {
                val line = scala.io.StdIn.readLine().toLowerCase
                line match {
                    case "y" | "yes" | "true" | "t" | "1" => Value.BoolVal(true)
                    case "n" | "no" | "false" | "f" | "0" => Value.BoolVal(true)
                    case _ => throw RuntimeException(s"Could not convert '$line' to Bool")
                }
            }
            case _ => throw RuntimeException("inputBool expects an optional string prompt")
        })

        // File I/O

        fnEnv.registerBuiltin("readFile", args => args match {
            case List(Value.StrVal(file)) => {
                try {
                    val out = scala.collection.mutable.ArrayBuffer[Value]()
                    val lines = fromFile(file).getLines
                    lines.foreach(str => out += Value.StrVal(str))
                    Value.ArrVal(out)
                } catch case e: Exception => throw RuntimeException(s"Could not read file '$file': ${e.getMessage}")
            }
            case _ => throw RuntimeException("readFile expected a filepath")    
        })

        fnEnv.registerBuiltin("writeFile", args => args match {
            case List(Value.StrVal(path), Value.ArrVal(lines)) => {
                try {
                    val writer = java.io.FileWriter(path)
                    lines.foreach(v => v match {
                        case Value.StrVal(s) => writer.write(s + "\n")
                        case _ => throw RuntimeException("writeFile expects an array of strings")
                    })
                    writer.close()
                    Value.BoolVal(true)
                } catch case e: Exception => throw RuntimeException(s"Could not write file '$path': ${e.getMessage}")
            }
            case List(Value.StrVal(path), Value.StrVal(content)) => {
                try {
                    val writer = java.io.FileWriter(path)
                    writer.write(content)
                    writer.close()
                    Value.BoolVal(true)
                } catch case e: Exception => Value.BoolVal(false)
            }
            case _ => throw RuntimeException("writeFile expects a filepath and an array of strings")
        })

        fnEnv.registerBuiltin("typeOf", args => args match {
            case List(value) => Value.StrVal(getTypeName(value))
            case _ => throw RuntimeException("typeOf expects exactly one argument")
        })

        fnEnv.registerBuiltin("deepCopy", args => args match {
            case List(value) => deepCopyValue(value)
            case _ => throw RuntimeException("deepCopy expects exactly one argument")
        })

        fnEnv.registerBuiltin("isNull", args => args match {
            case List(value) => Value.BoolVal(value == Value.NullVal)
            case _ => throw RuntimeException("isNull expects one argument")
        })

        fnEnv.registerBuiltin("push", args => args match {
            case List(Value.ArrVal(elements), value) =>
                elements += value
                Value.ArrVal(elements)
            case _ => throw RuntimeException("push expects an array and a value")
        })
        fnEnv.registerBuiltin("isEmpty", args => args match {
            case List(Value.ArrVal(elements)) => Value.BoolVal(elements.isEmpty)
            case _ => throw RuntimeException("isEmpty expects an array")
        })

        // Awesome Map Stuff
        fnEnv.registerBuiltin("newMap", args => args match {
            case List(Value.TypeVal(keyType), Value.TypeVal(valueType)) =>
                Value.MapVal(scala.collection.mutable.Map[Value, Value](), keyType, valueType)
            case _ => throw RuntimeException("newMap expects two type arguments e.g. newMap(Str, Int)")
        })

        fnEnv.registerBuiltin("get", args => args match {
            case List(Value.MapVal(entries, keyType, _), key) =>
                checkType(key, keyType, "map key")
                entries.getOrElse(key, throw RuntimeException(s"Key not found in map"))
            case _ => throw RuntimeException("get expects a map and a key")
        })

        fnEnv.registerBuiltin("set", args => args match {
            case List(Value.MapVal(entries, keyType, valueType), key, value) =>
                checkType(key, keyType, "map key")
                checkType(value, valueType, "map value")
                entries(key) = value
                Value.NullVal
            case _ => throw RuntimeException("set expects a map, a key, and a value")
        })

        fnEnv.registerBuiltin("hasKey", args => args match {
            case List(Value.MapVal(entries, keyType, _), key) =>
                checkType(key, keyType, "map key")
                Value.BoolVal(entries.contains(key))
            case _ => throw RuntimeException("hasKey expects a map and a key")
        })

        fnEnv.registerBuiltin("remove", args => args match {
            case List(Value.MapVal(entries, keyType, _), key) =>
                checkType(key, keyType, "map key")
                entries.remove(key)
                Value.NullVal
            case _ => throw RuntimeException("remove expects a map and a key")
        })

        fnEnv.registerBuiltin("keys", args => args match {
            case List(Value.MapVal(entries, _, _)) =>
                Value.ArrVal(scala.collection.mutable.ArrayBuffer(entries.keys.toSeq*))
            case _ => throw RuntimeException("keys expects a map")
        })

        fnEnv.registerBuiltin("toBinary", args => args match {
            case List(Value.IntVal(i)) => Value.StrVal(i.toBinaryString)
            case _ => throw RuntimeException("toBinary expects an integer")
        })

        fnEnv.registerBuiltin("floor", args => args match {
            case List(Value.FloatVal(f)) => Value.FloatVal(floor(f))
            case _ => throw RuntimeException("floor expects a float")
        })
        fnEnv.registerBuiltin("ceil", args => args match {
            case List(Value.FloatVal(f)) => Value.FloatVal(ceil(f))
            case _ => throw RuntimeException("ceil expects a float")
        })
        fnEnv.registerBuiltin("round", args => args match {
            case List(Value.FloatVal(f)) => Value.FloatVal(rint(f))
            case _ => throw RuntimeException("round expects a float")
        })
        fnEnv.registerBuiltin("cos", args => args match {
            case List(Value.FloatVal(f)) => Value.FloatVal(cos(f))
            case _ => throw RuntimeException("cos expects a float")
        })
        fnEnv.registerBuiltin("sin", args => args match {
            case List(Value.FloatVal(f)) => Value.FloatVal(sin(f))
            case _ => throw RuntimeException("sin expects a float")
        })
        fnEnv.registerBuiltin("tan", args => args match {
            case List(Value.FloatVal(f)) if f % 90 == 0 && f % 180 != 0 => throw RuntimeException("tan cannot handle divergent values")
            case List(Value.FloatVal(f)) => Value.FloatVal(tan(f))
            case _ => throw RuntimeException("tan expects a float")
        })
        fnEnv.registerBuiltin("acos", args => args match {
            case List(Value.FloatVal(f)) => Value.FloatVal(acos(f))
            case _ => throw RuntimeException("acos expects a float")
        })
        fnEnv.registerBuiltin("asin", args => args match {
            case List(Value.FloatVal(f)) => Value.FloatVal(asin(f))
            case _ => throw RuntimeException("sin expects a float")
        })
        fnEnv.registerBuiltin("atan", args => args match {
            case List(Value.FloatVal(f)) => Value.FloatVal(atan(f))
            case _ => throw RuntimeException("atan expects a float")
        })

        fnEnv.registerBuiltin("flatten", args => args match {
            case List(Value.ArrVal(elements)) => {
                val result = scala.collection.mutable.ArrayBuffer[Value]()
                elements.foreach {
                    case Value.ArrVal(inner) => result ++= inner
                    case _ => throw RuntimeException("flatten expects an array of arrays")
                }
                Value.ArrVal(result)
            }
            case _ => throw RuntimeException("flatten expects an array")
        })
        fnEnv.registerBuiltin("sum", args => args match {
            
            case List(Value.ArrVal(elements)) if getType(Value.ArrVal(elements)) == SimpType.TypeArr(SimpType.TypeInt) => {
                Value.IntVal(elements.collect { case Value.IntVal(n) => n }.sum)
            }
            case List(Value.ArrVal(elements)) if getType(Value.ArrVal(elements)) == SimpType.TypeArr(SimpType.TypeFloat) => {
                Value.FloatVal(elements.collect { case Value.FloatVal(n) => n }.sum)
            }
            case _ => throw RuntimeException("sum expects Int[] or Float[]")
        })

        fnEnv.registerBuiltin("random", args => args match {
            case List(Value.IntVal(min), Value.IntVal(max)) => Value.IntVal(Random.nextInt(max) + min)
            case _ => throw RuntimeException("random expects 2 integers (min and max)")
        })
        fnEnv.registerBuiltin("ord", args => args match {
            case List(Value.StrVal(str)) if str.length == 1 => Value.IntVal(str.head.toInt)
            case _ => throw RuntimeException("ord expects a string of length 1")
        })
        fnEnv.registerBuiltin("chr", args => args match {
            case List(Value.IntVal(o))=> Value.StrVal(o.toChar.toString)
            case _ => throw RuntimeException("chr expects an integer")
        })
    }