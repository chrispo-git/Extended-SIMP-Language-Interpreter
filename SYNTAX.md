## Abstract Syntax Documentation

### Programs
A SIMP program is a sequence of commands and declarations:

```
Program ::= (Cmd | Decl)*
```

### Values
SIMP has three value types:

| Type | Examples |
|------|---------|
| `Int` | `0`, `42`, `-5` |
| `Str` | `"hello"`, `"world"` |
| `Bool` | `true`, `false` |

---

### Declarations
Declarations come in 2 forms, declaring functions and declaring procedures.

```
Decl ::= fn f(x₀, x₁, ...) { Cmd }  | pd p(x₀, x₁, ...) { Cmd }
where x₀, x₁, ... are parameter names (locations)
```

- Functions `fn` return a value and are treated as expressions
- Procedures `pd` don't return a value and are treated as commands
- All parameters are passed by value
- Scoping is lexical, functions/procedures cannot see the caller's variables

---

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
      | print E                            -- print any expression
      | call p(E₀, E₁, ...)               -- procedure call
      | return E                           -- return from function
```

Where:
- `l ∈ L = {l₀, l₁, ...}` — locations (variable names)
- `E, E₀, E₁, ...` — expressions of any type

---

### Expressions

Expressions produce a value of type `Int`, `Str`, or `Bool`.

```
E ::= n                                    -- integer literal (n ∈ ℤ)
    | "s"                                  -- string literal
    | true | false                         -- boolean literals
    | !l                                   -- dereference (value stored at l)
    | E op E                               -- binary operation
    | E bop E                              -- comparison (produces Bool)
    | ¬E                                   -- boolean negation (E must be Bool)
    | E && E                                -- boolean and (E must be Bool)
    | E || E                                -- boolean or (E must be Bool)
    | f(E₀, E₁, ...)                      -- function call
    | (E)                                  -- parenthesised expression
```

#### Arithmetic Operators
Only valid on `Int` operands, produce `Int`:

```
op ::= + | - | * | / | %
```

Operator precedence (highest to lowest):

| Precedence | Operators |
|------------|-----------|
| High | `*`, `/`, `%` |
| Low | `+`, `-` |

All arithmetic operators are left-associative.

#### String Concatenation
`+` is also valid when the left operand is a `Str`. The right operand may be any type — it is automatically converted to `Str`:

```
"hello" + " world"     -- Str + Str -> Str
"count: " + 5          -- Str + Int -> Str
"flag: " + true        -- Str + Bool -> Str
```

#### Comparison Operators
Valid on `Int`/`Int` or `Str`/`Str` or `Bool`/`Bool` operand pairs, produce `Bool`:

```
bop ::= > | < | >= | <= | == | !=
```

Note: `>`, `<`, `>=`, `<=` are only valid on `Int` operands. `==` and `!=` are valid on any matching pair.

---

### Boolean Expressions

Boolean expressions produce a `Bool` value. They can appear anywhere an expression is expected.

```
B ::= true | false                         -- boolean literals
    | !l                                   -- dereference of a Bool variable
    | E bop E                              -- comparison
    | ¬B                                   -- negation
    | B && B                                -- conjunction (and)
    | B || B                                -- disjunction (or)
    | (B)                                  -- parenthesised boolean
    | f(E₀, E₁, ...)                      -- function call returning Bool
```

---

### Syntactic Sugar
These constructs don't appear in the AST, but are desugared by the parser.

| Syntax | Desugars to |
|--------|-------------|
| `l += E` | `l := !l + E` |
| `l -= E` | `l := !l - E` |
| `l *= E` | `l := !l * E` |
| `l /= E` | `l := !l / E` |
| `elif B then { C }` | `else { if B then { C } }` |

---

### Comments
Single line comments begin with `//` and extend to the end of the line:
```
// This is a comment
x := 5; // This is also a comment
// x += 5; Even this is a comment!
```

---

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
        } else {
            b := !b - !a;
        };
    };
    return !a;
}

print "The greatest common divisor of 48 and 18 is...";
print gcd(48, 18);
```

#### String Operations
```
name := "SIMP";
version := 1;
print "Welcome to " + !name + " version " + !version;

x := len("hello");
print "Length: " + !x;
```

#### Boolean Variables
```
flag := true;
other := false;

if !flag && ¬!other then {
    print "both conditions met";
};

result := !flag == !other;
print "flags equal: " + !result;
```
