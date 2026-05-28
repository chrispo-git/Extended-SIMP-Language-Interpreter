package simp

import scala.io.Source
import java.io.FileNotFoundException

def startRepl(store: Store, fnEnv: FunctionEnv, structEnv: StructEnv): Unit = {
    println("SIMP REPL - type 'exit' to quit")
    val evaluator = Evaluator(fnEnv, structEnv)
    var input = ""

    while true do {
        if input.isEmpty then print("simp> ")
        else print("    | ")

        val line = scala.io.StdIn.readLine()
        if line == null || line.trim == "exit" then {
            println("Goodbye!")
            sys.exit(0)
        }

        input = if input.isEmpty then line else input + "\n" + line

        val openBraces = input.count(_ == '{')
        val closeBraces = input.count(_ == '}')

        val isComplete = openBraces == closeBraces && {
            if openBraces > 0 then {
                val toks = Lexer(input).tokenise()
                val ifCount = toks.count(t => t == Token.If || t == Token.Elif)
                val elseCount = toks.count(t => t == Token.Else || t == Token.Elif)
                ifCount == elseCount
            } else true
        }


        if isComplete then {
            try {
                val tokens = Lexer(input).tokenise()
                val programs = Parser(tokens, structEnv).parseRepl()
                evaluator.evalProgram(programs, store)
            } catch {
                case e: RuntimeException => println(s"Error: ${e.getMessage}")
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

        val tokens = Lexer(source).tokenise()
        val program = Parser(tokens, structEnv).parseProgram()
        Evaluator(fnEnv, structEnv).evalProgram(program, store)
    }
}