## Abstract Syntax Documentation

### Programs
A SIMP program is a sequence of commands and declarations:

```
Program ::= (Cmd | Decl)*
```

### Values
Expressions produce a value in 6 categories:
- Primitive type - `Int`, `Str`, `Bool`, `Float`
- Structs
- Tuple Type - `(Int, Str)`, `(Bool, Float)`, `(Int, Int)`
- Array Type - `Int[]`, `Str[]`, `Bool[]`, `Struct[]`
- Map Type - `Map(K, V)`
- Null Type - `null` 

| Type | Examples |
|------|---------|
| `Int` | `-7`, `42`, `90210` |
| `Float` | `20.05`, `6.7`, `3.142` |
| `Str` | `"hello"`, `"world"` |
| `Bool` | `true`, `false` |
| `Int[]`, `Str[]`, `Bool[]` , `Float[]`| `[1, 2, 3]`, `["a", "b"]`, `[true, false]`, `[]` |
| `StructName` | `Point { x: 1, y: 2 }` |
| `null` | `null` |
| `Map(K, V)` | `newMap(Str, Int)`, `newMap(Int, Bool)` |
| `(T, U)` | `(1, "hello")`, `(true, 3.14)` |

Pairs are immutable two-element tuples. Elements are accessed with `.fst` and `.snd`.
Maps are key-value stores with typed keys and values. They are created with `newMap(KeyType, ValueType)` 
and manipulated exclusively through built-in functions. See [Built-in Functions](BUILT-IN-FUNCTIONS.md).
---

### Declarations
Declarations come in 3 forms, declaring functions, declaring structs, and imports.

```
Decl ::= fn f(x₀ : t₀, x₁ : t₁, ...) -> t { Cmd }  
        | struct S { f₀ : t₀ = v₀, f₁ : t₁ = v₁, ... } 
        | import "F"
        | import "F" as A
where x₀, x₁, ... are parameter names (locations)
where t, t₀, t₁, ... are parameter types (One of Str, Int, Bool, Int[], Str[], Bool[], Struct, Struct[], (T,U), Map(K, V), or Void)
where f₀, f₁, ... are field names
where v₀, v₁, ... are optional default values
where F is the path of a file, and A is an optional alias
```

- Functions `fn` return a value and are treated as expressions
- primitives for functions are passed by value, arrays and structs are passed by reference
- Scoping is lexical, functions cannot see the caller's variables
- Declarations from imported files are accessible via `A::name` syntax
- If no alias is given, the filename without extension is used as the namespace
- Importing the same file with the same alias twice is ignored
- Importing the same file with different aliases registers it under both
- Circular imports throw a runtime error

---

### Commands

```
Cmd ::= skip                                -- no-op
      | const l := E                             -- constant assignment
      | l := E                             -- assignment
      | l[E] := E                                -- array index assignment
      | l += E                             -- compound add (just sugar for l := !l + E)
      | l -= E                             -- compound subtract (just sugar for l := !l - E)
      | l *= E                             -- compound multiply (just sugar for l := !l * E)
      | l /= E                             -- compound divide (just sugar for l := !l / E)
      | Cmd ; Cmd                          -- sequencing
      | if B then { Cmd } else { Cmd }     -- conditional
      | if B then { Cmd }                  
        elif B then { Cmd }                -- (chained, just sugar for nested if/else)
        else { Cmd }
      | while B do { Cmd }                 -- loop
      | { Cmd }                            -- anonymous scope block
      | for l in E { Cmd }                 -- foreach loop (E must be an array type, l is read-only)
      | break                            -- breaks loop
      | continue                            -- continues loop
      | print E                            -- print any expression
      | return E                           -- return from function
      | l.f := E                                 -- field assignment (write)
```

Where:
- `l ∈ L = {l₀, l₁, ...}`  locations (variable names)
- `E, E₀, E₁, ...`  expressions of any type

Note: blocks within if-then-else statements, while do statements, and for statements have their own scope, variables created in that block are scoped to that block.

---

### Expressions

Expressions produce a value of type `Int`, `Float`, `Struct`, `Str`, `Array`, or `Bool`.

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
    | (E, E)                                    -- pair literal
    | [E₀, E₁, ...]                            -- array literal
    | E[E]                                     -- array index (read)
    | S { f₀: E₀, f₁: E₁, ... }              -- struct literal
    | E.f                                      -- field access (read)
    | null                                      -- null value for non-primitive types
    | match E { case P => E; ... }              -- pattern match expression
    | { Cmd; ... E }                            -- block expression (evaluates commands, returns E)
```

Note: Block expressions execute a sequence of commands and return the value of the final expression.
The final expression can't have a trailing semicolon.

#### Arithmetic Operators

Here are all the operators

| Operator | Description | Available Types |
|----------|-------------|-------------|
| `+` | Addition | `Int`, `Str`, `Float`|
| `-` | Subtraction |`Int`, `Float`|
| `*` | Multiplication |`Int`, `Float`|
| `/` | Division |`Int`, `Float`|
| `%` | Modulo |`Int`|
| `<<` | Left shift |`Int`|
| `>>` | Right shift |`Int`|
| `>>>` | Unsigned right shift |`Int`|
| `&` | Bitwise AND |`Int`|
| `\|` | Bitwise OR |`Int`|
| `^` | Bitwise XOR |`Int`|
| `~` | Bitwise complement (unary) |`Int`|

Operator precedence (highest to lowest):

| Precedence | Operators |
|------------|-----------|
| Highest | `~` |
| High | `*`, `/`, `%` |
| Medium | `+`, `-` |
| Lowest | `<<`, `>>`, `>>>`, `&`, `\|`, `^` |

All arithmetic operators are left-associative.

#### String Concatenation
`+` is also valid when the left operand is a `Str`. The right operand can be any type it's automatically converted to `Str`:

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

### Patterns

Patterns are used in `match` expressions to destructure and bind values.

```
P ::= _                                    -- wildcard (matches anything)
    | n                                    -- integer literal
    | f                                    -- float literal
    | "s"                                  -- string literal
    | true | false                         -- boolean literals
    | null                                 -- null
    | x                                    -- variable binding (binds matched value to x)
    | (P, P)                               -- pair destructuring
    | S { f₀: P, f₀: P, ... }             -- struct destructuring
```
Patterns are tried in order, the first matching arm is executed. If no arm matches a runtime error is thrown.

#### Guards
A guard can be added to any pattern with `if` like so:
```
case x if !x > 9000 => "It's over 9000!"
```

The guard is evaluated iff the pattern matches. If the guard fails the next arm is tried.

#### Variable Binding
Variables bound in a pattern are available in the arm body and guard:
```
case Point { x: px, y: py } => print !px + ", " + !py;
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

Multi-line comments are enclosed between `/*` and `*/`:
```
/* This 
really
is
a
comment*/
x +=/*This too*/ 2
```

---

### Example Programs

#### Factorial
```
fn factorial(n : Int) -> Int {
    if !n == 0 then {
        return 1;
    } else {
        return !n * factorial(!n - 1);
    };
}

print "The Factorial of 5 is " + factorial(5);
```

#### GCD
```
fn gcd(a : Int, b : Int) -> Int {
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

print "The greatest common divisor of 48 and 18 is..." + gcd(48, 18);
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

#### Arrays
```
fn reverseArr(a: Int[]) -> Void {
    i := 0;
    j := len(a) - 1;
    while !i < !j do {
        tmp := a[!i];
        a[!i] := a[!j];
        a[!j] := !tmp;
        i += 1;
        j -= 1;
    };
}

nums := [1, 2, 3, 4, 5];
print nums;
_ := reverseArr(nums);
print nums;
```

### Maps
```
// Word frequency counter
text := "The FitnessGram Pacer Test is a multistage aerobic capacity test that progressively gets more difficult as it continues";
words := split(!text, " ");
counts := newMap(Str, Int);

for word in words {
    if hasKey(counts, word) then {
        _ := set(counts, word, get(counts, word) + 1);
    } else {
        _ := set(counts, word, 1);
    };
};

wordList := keys(counts);
for word in wordList {
    print word + ": " + get(counts, word);
};
```

### Tuples
```
fn minMax(arr: Int[]) -> (Int, Int) {
    mn := arr[0];
    mx := arr[0];
    for x in arr {
        if x < !mn then { mn := x; };
        if x > !mx then { mx := x; };
    };
    return (!mn, !mx);
}

result := minMax([3, 1, 4, 1, 5, 9, 2, 6]);
print "min: " + result.fst;
print "max: " + result.snd;
```

### Pattern Matching
```
struct Shape { kind: Str, size: Int }

fn describe(s: Shape) -> Str {
    return match s {
        case Shape { kind: "circle", size: r } => "circle with radius " + !r;
        case Shape { kind: "square", size: w } => "square with width " + !w;
        case Shape { kind: k } => "unknown shape: " + !k;
    };
}

print describe(Shape { kind: "circle", size: 5 });
print describe(Shape { kind: "square", size: 3 });
```