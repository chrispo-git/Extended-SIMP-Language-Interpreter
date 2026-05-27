# SIMP-Interpreter
A simple imperative language + interpreter with REPL.

## What is SIMP?
SIMP is a simple imperative language covered in the textbook `Programming Languages and Operational Semantics : A Concise Overview`. This repository extends that language with recursive functions & procedures, compound assignment operators, and other features like curly braces to delineate blocks.
The full Grammar in BNF is available [here](SYNTAX.md), but here is some example code:

```
pd fizzbuzz(n) {
    i := 1;
    while !i <= !n do {
        if !i % 3 == 0 && !i % 5 == 0 then {
            print "Fizzbuzz!";
        } elif !i % 3 == 0 then {
            print "Fizz";
        } elif !i % 5 == 0 then {
            print "Buzz";
        } else {
            print !i;
        };
        i := !i + 1;
    };
}
call fizzbuzz(30);
```

## Getting Started
### Prerequisites
- Scala 3
- sbt (simple build tool)
### How to Use
1. Download the repository
2. Run `sbt compile` within the root of the repository
3. To run a file, use `sbt "run examples/greatcomdiv.simp"`
4. To just start the REPL, use `sbt run`

## Features
- Integer arithmetic
- Boolean expressions
- Comparison operators
- Variable assignment and dereferencing
- if / else conditionals
- while loops
- Sequencing
- Compound assignment operators
- Single-line comments
- Negative integer literals
- Curly brace syntax for blocks
- First-class functions with explicit `return`
- Void procedures with `call` syntax
- Recursive functions
- Lexical scoping with pass-by-value-semantics
- Separate function environment from variable store

## Tooling
- File runner `sbt "run file.simp"`
- Interactive REPL with persistent state `sbt run`
- Multiline input support in the REPL
- Meaningful runtime error messages

## Architecture
The pipeline from Source to output is
`Source → Lexer → Tokens → Parser → AST → Evaluator → Output`
All components were written without usage of external parsing libraries.

### Lexer
The Lexer takes the source text, and splits it into a list of accepted tokens to provide to the Parser.
### Parser
The Parser takes the list of tokens provided by the Lexer, verifies that they're in an order that makes sense with the Abstract Syntax of the language, and converts the list into a list of Abstract Syntax Trees. This list comes with 2 types of entries:
- function/procedure definitions
- imperative commands

This is also where desugaring takes place.

### Evaluator
The Evaluator takes the list of ASTs emitted by the Parser along with an initially empty Store & recursively evaluates them in order.


## Challenges & Solutions
### Language Selection (Scala vs Java)
Writing a compiler pipeline from scratch without external tools requires processing the complex nesting structures seen in ASTs. While a language like Java or C++ would require more verbose object-oriented design patterns like the Visitor to traverse an AST, Scala 3 allowed me to treat the input code as data in a way that made the work of building the interpreter easier. The biggest aid for me was Structural Pattern Matching, which combined with Scala's enums allowed me to map the ASTs directly to language constructs. 

### Lexical Analysis & the Whitespace Problem
Human-written code can vary wildly in whitespace styles, and one thing that had to be ensured was making sure it was all converted into tokens correctly, including edge cases. This involved designing a lexer that ignores whitespace unless it's syntactically necessary (i.e. correctly marking `ifx==2then{print!x}` as invalid). This, along with careful language design to avoid lexical ambiguity as much as possible (like designating `¬` as the Not operator rather than `!`) allowed me to make my job easier in later stages.

### Managing State
Implementing lexical scoping for recursive functions allowed me to delve deeper into state management. I had to make sure that function calls couldn't access or pollute their caller's variable environment. To solve this, I made sure the Evaluator passed down isolated, localized variable stores during recursive steps, with each store only containing the values of arguments passed in by value, marking a clean separation of recursive calls.
