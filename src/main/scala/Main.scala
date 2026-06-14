package simp

import scala.io.Source
import java.io.FileNotFoundException
import org.jline.reader.{LineReaderBuilder, EndOfFileException, UserInterruptException}
import org.jline.terminal.TerminalBuilder
import SimpUtils.*

val RED = "\u001b[31m"
val RESET = "\u001b[0m"

val HELPSCRIPT = 
"""
:exit               - Exit the repl
:dump               - Dump the current memory
:fns                - List all declared functions
:structs            - List all declared struct types
:reset              - Reset all memory
:load "<path>"      - load and execute an ExtSimp file in the current session
:ast <expr/cmd>     - show the parsed AST for an expression or commands
:ast "<path>"       - show the parsed AST for a .simp file
"""
def runSource(source: String, store: Store, fnEnv: FunctionEnv, structEnv: StructEnv, currentDir: String = "."): Unit = {
    try {
        val sourceLines = source.split('\n').toList
        val tokens = Lexer(source, sourceLines).tokenise()
        val program = Parser(tokens._1, structEnv, tokens._2, sourceLines).parseProgram()
        Evaluator(fnEnv, structEnv, sourceLines, currentDir).evalProgram(program, store)
    } catch {
        case e: RuntimeException => println(s"[${RED}Error${RESET}] ${e.getMessage}")
    }
}
def startRepl(store: Store, fnEnv: FunctionEnv, structEnv: StructEnv): Unit = {
    val terminal = TerminalBuilder.builder().system(true).build()
    val reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .option(org.jline.reader.LineReader.Option.DISABLE_EVENT_EXPANSION, true)
        .build()

    println("SIMP+ REPL - type ':exit' to quit, and ':help' for more commands")
    var input = ""

    while true do {
        var skip = false
        val prompt = if input.isEmpty then "simp> " else "    | "
        val line = try reader.readLine(prompt)
            catch
                case _: EndOfFileException => sys.exit(0)
                case _: UserInterruptException => sys.exit(0)
        line.trim match {
            case ":exit" => {println("Goodbye!");sys.exit(0)}
            case ":help" => {println(HELPSCRIPT); skip = true}
            case ":dump" => {println(store.dump()); skip = true}
            case ":fns" => {println(fnEnv.dumpFn()); skip = true}
            case ":structs" => {println(structEnv.dumpStruct()); skip = true}
            case ":reset" => {
                store.clear();
                fnEnv.clear();
                structEnv.clear();
                println("Session reset.");
                skip = true
            }
            case s if s.startsWith(":load ") => {
                val path = s.stripPrefix(":load ").trim.stripPrefix("\"").stripSuffix("\"")
                try {
                    val source = scala.io.Source.fromFile(path).mkString
                    runSource(source, store, fnEnv, structEnv)
                    println(s"Loaded '$path'")
                } catch {
                    case e: java.io.FileNotFoundException => println(s"${RED}Error${RESET}] File not found: '$path'")
                    case e: RuntimeException => println(s"${RED}Error${RESET}] ${e.getMessage}")
                }
                skip = true
            }
            case s if s.startsWith(":ast ") => {
                val arg = s.stripPrefix(":ast ").trim
                try {
                    val source = if arg.startsWith("\"") && arg.endsWith("\"") then {
                        scala.io.Source.fromFile(arg.stripPrefix("\"").stripSuffix("\"")).mkString
                    } else {
                        arg
                    }
                    val sourceLines = source.split('\n').toList
                    val (tokens, lineNumbers) = Lexer(source, sourceLines).tokenise()
                    val programs = Parser(tokens, structEnv, lineNumbers, sourceLines).parseRepl()
                    programs.foreach {
                        case Program.PCmd(cmd)   => println(SimpUtils.prettyPrintCmd(cmd))
                        case Program.PExpr(expr) => println(SimpUtils.prettyPrintExpr(expr))
                        case Program.PDecl(decl) => println(decl.toString)
                        case Program.PBool(bool) => println(SimpUtils.prettyPrintBool(bool))
                        case Program.PImpl(name, methods) =>
                            println(s"Impl($name, [${methods.map(_.name).mkString(", ")}])")
                    }
                } catch {
                    case e: java.io.FileNotFoundException => println(s"${RED}Error${RESET}] File not found: '$arg'")
                    case e: RuntimeException => println(s"${RED}Error${RESET}] ${e.getMessage}")
                }
                skip = true
            }
            case _ =>
        }

        if skip == false then {
            input = if input.isEmpty then line else input + "\n" + line

            val openBraces = input.count(_ == '{')
            val closeBraces = input.count(_ == '}')

            val isComplete = openBraces == closeBraces && {
                if openBraces > 0 then {
                    val sourceLines = input.split('\n').toList
                    val toks = Lexer(input, sourceLines).tokenise()._1
                    !(toks.contains(Token.If) && !toks.contains(Token.Else))
                } else true
            }

            if isComplete then {
                try {
                    val sourceLines = input.split('\n').toList
                    val (tokens, lineNumbers) = Lexer(input, sourceLines).tokenise()
                    val programs = Parser(tokens, structEnv, lineNumbers, sourceLines).parseRepl()
                    Evaluator(fnEnv, structEnv, sourceLines).evalProgram(programs, store)
                } catch {
                    case e: RuntimeException =>
                        println(s"[${RED}Error${RESET}] ${e.getMessage}")
                }
                input = ""
            }
        }
    }
}

@main def run(args: String*): Unit = {
    val store = Store()
    val fnEnv = FunctionEnv()
    Builtins.register(fnEnv)
    val structEnv = StructEnv()
    if args.isEmpty then {
        startRepl(store, fnEnv, structEnv)
    } else {
        val filename = args(0)

        val source = {
            try Source.fromFile(filename).mkString
            catch case _: FileNotFoundException => {
                println(s"File not found: $filename")
                sys.exit(1)
            }
        }
        val file = java.io.File(filename)
        val currentDir = file.getParentFile.getAbsolutePath

        runSource(source, store, fnEnv, structEnv, currentDir)
    }
}