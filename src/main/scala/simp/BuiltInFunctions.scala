package simp 

import scala.io.Source._

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
                case List(Value.BoolVal(b)) => Value.BoolVal(false)
                case List(Value.StrVal(s)) => Value.BoolVal(false)
                case _ => throw RuntimeException("isInt expects 1 argument")
            }
        )
        // isStr - Returns true if the argument is a string
        fnEnv.registerBuiltin("isStr", args => args match {
                case List(Value.IntVal(i)) => Value.BoolVal(false)
                case List(Value.BoolVal(b)) => Value.BoolVal(false)
                case List(Value.StrVal(s)) => Value.BoolVal(true)
                case _ => throw RuntimeException("isStr expects 1 argument")
            }
        )
        // isBool - Returns true if the argument is a boolean
        fnEnv.registerBuiltin("isBool", args => args match {
                case List(Value.IntVal(i)) => Value.BoolVal(false)
                case List(Value.BoolVal(b)) => Value.BoolVal(true)
                case List(Value.StrVal(s)) => Value.BoolVal(false)
                case _ => throw RuntimeException("isBool expects 1 argument")
            }
        )

        // toStr - Converts a boolean/integer into a String
        fnEnv.registerBuiltin("toStr", args => args match {
                case List(Value.IntVal(i)) => Value.StrVal(i.toString)
                case List(Value.BoolVal(b)) => Value.StrVal(b.toString)
                case List(Value.StrVal(s)) => Value.StrVal(s)
                case _ => throw RuntimeException("toStr expects 1 argument")
            }
        )
        // toInt - Converts a String into an Int
        fnEnv.registerBuiltin("toInt", args => args match {
                case List(Value.StrVal(s)) => Value.IntVal(s.toInt)
                case _ => throw RuntimeException("toInt expects 1 string")
            }
        )
        // toBool - Converts a String into a Boolean
        fnEnv.registerBuiltin("toBool", args => args match {
                case List(Value.StrVal(s)) => Value.BoolVal(s.toBoolean)
                case _ => throw RuntimeException("toBool expects 1 string")
            }
        )
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
                case _ => throw RuntimeException("abs expects 1 integer")
            }
        )
        // max - Max of 2 Integers
        fnEnv.registerBuiltin("max", args => args match {
                case List(Value.IntVal(s1), Value.IntVal(s2)) => {
                    if s1 >= s2 then {
                        Value.IntVal(s1)
                    } else {
                        Value.IntVal(s2)
                    }
                }
                case _ => throw RuntimeException("max expects 2 integers")
            }
        )
        // min - Min of 2 Integers
        fnEnv.registerBuiltin("min", args => args match {
                case List(Value.IntVal(s1), Value.IntVal(s2)) => {
                    if s1 >= s2 then {
                        Value.IntVal(s2)
                    } else {
                        Value.IntVal(s1)
                    }
                }
                case _ => throw RuntimeException("min expects 2 integers")
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
                case _ => throw RuntimeException("clamp expects 3 integers")
            }
        )

        // pow - Integer Power
        fnEnv.registerBuiltin("pow", args => args match {
                case List(Value.IntVal(s1), Value.IntVal(s2)) => {
                   if s2 < 1 then {
                        throw RuntimeException("negative powers not supported")
                   } else {
                        var result = 1
                        var currentBase = s1
                        var exp = s2
                        while (exp > 0) {
                            if (exp % 2 == 1) {
                                result *= currentBase 
                            }
                            currentBase *= currentBase
                            exp /= 2
                        }
                        Value.IntVal(result)
                   }
                }
                case _ => throw RuntimeException("pow expects 2 integers")
            }
        )

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
    }