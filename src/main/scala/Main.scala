package simp

import scala.io.Source
import java.io.FileNotFoundException

@main def run(args: String*): Unit =
    if args.isEmpty then
        println("Usage: simp <file.simp>")
        sys.exit(1)

    val filename = args(0)

    val source = {
        try Source.fromFile(filename).mkString
        catch case _: FileNotFoundException => {
            println(s"File not found: $filename")
            sys.exit(1)
        }
    }

    val tokens = Lexer(source).tokenise()
    val program = Parser(tokens).parseProgram()
    val store = Store()
    val fnEnv = FunctionEnv()
    Evaluator(fnEnv).evalProgram(program, store)