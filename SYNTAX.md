## Abstract Syntax Documentation

### Programs
A SIMP program is a sequence of commands and declarations:

```
Program ::= (Cmd | Decl)*
```

### Declarations
Declarations come in 2 forms, declaring functions and declaring procedures.

```
Decl ::= fn f(x₀, x₁, ...) { Cmd }  | pd p(x₀, x₁, ...) { Cmd }
where x₀, x₁, ... are integer terms that can be evaluated
```

- Functions `fn` return a value and are treated as expressions
- Procedures `pd` don't return a value and are treated as commands
- All parameters are passed by value
- Scoping is lexical, functions/procedures can't see the caller's variables

### Commands

```
Cmd ::= skip                                -- no-op
      | l := E                             -- assignment
      | l += E                             -- compound add (sugar for l := !l + E)
      | l -= E                             -- compound subtract (sugar for l := !l - E)
      | l *= E                             -- compound multiply (sugar for l := !l * E)
      | l /= E                             -- compound divide (sugar for l := !l / E)
      | Cmd ; Cmd                          -- sequencing
      | if B then { Cmd }                  -- conditional (no else)
      | if B then { Cmd } else { Cmd }     -- conditional
      | if B then { Cmd }                  -- if
        elif B then { Cmd }                -- (chained, sugar for nested if/else)
        else { Cmd }
      | while B do { Cmd }                 -- loop
      | print E                            -- print integer expression
      | print "s"                          -- print string literal
      | call p(E₀, E₁, ...)               -- procedure call
      | return E                           -- return from function
```

Where:
- `l ∈ L = {l₀, l₁, ...}`   i.e. l is bound, defined earlier
- `s`                       is a string literal, e.g. "Hello World!"

### Integer Expressions
```
E ::= n                                    -- integer literal (n ∈ ℤ)
    | !l                                   -- dereference (value stored at l)
    | E op E                               -- binary operation
    | f(E₀, E₁, ...)                      -- function call
    | (E)                                  -- parenthesised expression

op ::= + | - | * | / | %
```

- Operators have precedence in 2 tiers, High (*, /, %) and Low (+, -)
- All operators are left-associative

### Boolean Expressions
```
B ::= true | false                         -- boolean literals
    | E bop E                              -- comparison
    | ¬B                                   -- negation
    | B & B                                -- conjunction (and)
    | B | B                                -- disjunction (or)
    | (B)                                  -- parenthesised boolean

bop ::= > | < | >= | <= | == | !=
```

### Syntactic Sugar
These constructs don't appear in the AST, but are desugared by the parser. They've been included to make the code nicer and easier for people to write and understand.
| Syntax | Desugars to |
| -------- | ------- |
| `l += E` | `l := !l + E`|
| `l -= E` | `l := !l - E`|
| `l *= E` | `l := !l * E`|
| `l /= E` | `l := !l / E`|
| `elif B then { C }` | `else { if B then { C } }`|

### Comments
Single line comments begin with `//` and extend to the end of the line
```
// This is a comment
x := 5; // This is also a comment
// x += 5; Even this is a comment!
```

### Example Programs
#### Factorial
```
fn factorial(n) {
    if !n == 0 then {
        return 1;
    } else {
        return !n * factorial(!n - 1);
    };
}

print "The Factorial of 5 is...";
print factorial(5);
```

#### GCD
```
fn gcd(a, b) {
    while ¬(!a == !b) do {
        if !a > !b then {
            a := !a - !b;
        }
        else {
            b := !b - !a;
        }
    };
    return !a;
}
print "The greatest common divisor of 48 and 18 is...";
print gcd(48, 18);
```

More are available in [examples](examples)