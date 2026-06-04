package simp

import scala.io.Source
import java.io.FileNotFoundException
import org.jline.reader.{LineReaderBuilder, EndOfFileException, UserInterruptException}
import org.jline.terminal.TerminalBuilder

val RED = "\u001b[31m"
val RESET = "\u001b[0m"


def startRepl(store: Store, fnEnv: FunctionEnv, structEnv: StructEnv): Unit = {
    val terminal = TerminalBuilder.builder().system(true).build()
    val reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .build()

    println("SIMP+ REPL - type 'exit' to quit")
    var input = ""

    while true do {
        val prompt = if input.isEmpty then "simp> " else "    | "
        val line = try reader.readLine(prompt)
            catch
                case _: EndOfFileException => sys.exit(0)
                case _: UserInterruptException => sys.exit(0)

        if line.trim == "exit" then {
            println("Goodbye!")
            sys.exit(0)
        }

        input = if input.isEmpty then line else input + "\n" + line

        val openBraces = input.count(_ == '{')
        val closeBraces = input.count(_ == '}')

        val isComplete = openBraces == closeBraces && {
            if openBraces > 0 then {
                val toks = Lexer(input).tokenise()._1
                !(toks.contains(Token.If) && !toks.contains(Token.Else))
            } else true
        }

        if isComplete then {
            try {
                val (tokens, lineNumbers) = Lexer(input).tokenise()
                val programs = Parser(tokens, structEnv, lineNumbers).parseRepl()
                Evaluator(fnEnv, structEnv).evalProgram(programs, store)
            } catch {
                case e: RuntimeException =>
                    println(s"[${RED}Error${RESET}] ${e.getMessage}")
            }
            input = ""
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

        try {
            val tokens = Lexer(source).tokenise()
            val program = Parser(tokens._1, structEnv, tokens._2).parseProgram()
            Evaluator(fnEnv, structEnv, currentDir).evalProgram(program, store)
        } catch {
            case e: RuntimeException => println(s"[${RED}Error${RESET}] ${e.getMessage}")
        }
    }
}