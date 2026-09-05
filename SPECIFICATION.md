# SIMP+ Interpreter Build Guide

This document is a **dependency-ordered build plan** for implementing a
SIMP+ interpreter from scratch, in any language (Python, C++, Java, Rust,
whatever). It is not organized by language concept ("here's how structs
work") — it's organized by **build order**: each phase only assumes the
phases before it are done, and says so explicitly where the dependency is
non-obvious. Follow it top to bottom and you will never need to
retrofit something you already built (e.g. you will never discover in
Phase 6 that Phase 3's parser needs restructuring).

Every phase gives:
- **What to build.**
- **Exact behavior it must have** (grammar/algorithm/semantics, at the
  same precision level as the reference implementation — this is the
  part that must be *exact*, not approximate).
- **Why it must come at this point** (what it depends on, what would
  break if built earlier/later).
- **How to know it's done** (a concrete, testable milestone before
  moving on).

Background reading, not a substitute for this guide: [SYNTAX.md](SYNTAX.md)
(examples), [BUILT-IN-FUNCTIONS.md](BUILT-IN-FUNCTIONS.md) (full builtin
signatures).

---

## Phase 0 — Decide your value representation strategy

Before writing any code, decide how your target language will represent
the five reference-counted/mutable "boxed" runtime shapes (**array,
struct, map**, plus the two immutable composites **pair, ref-cell**) vs.
the four unboxed primitives (**int, float, string, bool**), plus **null**
and **a first-class type token**. This decision ripples through every
later phase, so make it now:

- In a GC'd host language (Python, Java, C#, JS) you can generally use
  plain host objects/records for boxed values and let host reference
  semantics do the work: mutating an array element or struct field
  through one variable is automatically visible through every other
  variable/parameter/collection-slot holding "the same" value, because
  the host language already gives you reference semantics for mutable
  objects and value semantics for its own primitives. This matches
  SIMP+'s model almost exactly (see Phase 4.1) — the main risk is
  accidentally introducing a *deep copy* somewhere host idioms encourage
  it (e.g. Python's `+`/slicing on lists returns a *new* list — don't use
  that to implement SIMP+'s in-place array mutation builtins).
- In a non-GC'd host language (C++, Rust, C) you must design this
  explicitly: arrays/structs/maps need to be heap-allocated and
  reference-counted or arena/GC'd yourself (e.g. `shared_ptr` in C++,
  `Rc<RefCell<_>>` in Rust), because SIMP+ values are freely aliased
  (assigned to multiple variables, stored in multiple struct fields,
  captured in closures-of-a-sort via `ref` params) and outlive their
  original lexical binding in ways that make ownership-based deletion
  unsafe without reference counting.

Whatever you choose, the single invariant to preserve is: **Int, Float,
Str, Bool, Pair, Null are copied by value; Array, Struct, Map are copied
by reference (aliased, not cloned) on every assignment/pass/store,** with
the *sole* exception of the `deepCopy` builtin (Phase 9) which explicitly
walks and clones them. Get this wrong and every later phase's behavior
will subtly diverge.

---

## Phase 1 — Token model

**Build:** a `Token` sum type (enum/tagged union/whatever your language
calls it) with one variant per lexeme class:

```
literals:    IntLit(i32), FloatLit(f64), StrLit(string), BoolLit(bool), Variable(string)
arithmetic:  Add Sub Mul Div Mod Deref(!) BitComplement(~)
comparison:  Gt Lt Gte Lte Eq Neq
logic:       Not(¬) And(&&) Or(||)
bitwise:     BitAnd(&) BitOr(|) BitXor(^) BitLeft(<<) BitRight(>>) BitRightFill(>>>)
assignment:  Assign(:=) PlusEq MinusEq MulEq DivEq
keywords:    Skip If Then Else Elif While Do Break Continue For In
             Fn Return Struct Impl Priv Static Locked Import As Const
             Match Case Null Print
types:       TypeInt TypeString TypeBool TypeFloat TypeNull TypeMap Ref
punctuation: Semicolon Comma Colon DoubleColon(::) Dot Arrow(->) FatArrow(=>)
             OpenBracket CloseBracket OpenBrace CloseBrace OpenSquare CloseSquare
sentinel:    EOF
```

**Why now:** everything else — the lexer's output type, the parser's
input type — is defined in terms of this. Nothing before it makes sense.

**Done when:** the type compiles/type-checks with no behavior yet.

---

## Phase 2 — Lexer

**Build:** a function `tokenize(source: string) -> (Token[], int[])` — the
second array is a **parallel line-number list**, one entry per token (plus
one final entry for the trailing `EOF`), used only for error messages
later. Single forward-scanning pass, no backtracking.

**Exact algorithm** (each bullet is a priority-ordered dispatch — check
in roughly this order at each scan position, since several rules overlap
on their first character and must be disambiguated by lookahead):

1. **Whitespace**: space/tab/CR always skipped; `\n` skipped *and*
   increments a line counter.
2. **Comments**: `//` skips to end of line. `/* ... */` — **nests**: keep an
   integer depth counter (not a boolean), incremented on every `/*` seen
   (including ones encountered while already inside a comment) and
   decremented on every `*/` (only while depth > 0 — a stray `*/` outside
   any comment is consumed but does not go negative); you are "in a
   comment" whenever depth > 0. `/* outer /* inner */ still commented */`
   is one comment from the first `/*` to the *second* `*/` — replicate
   this, don't stop at the first `*/` the way a naive boolean-flag
   implementation would (see Phase 12's conformance item for this).
3. **Numeric literals**: scan the maximal run of characters that are
   letters/digits/`_`/`.` starting here (this is how the reference impl's
   `getWholeWord`/`getWholeFloat` work — note this means a numeric scan
   candidate can accidentally swallow trailing letters, which then fails
   the digit-only check and falls through to being parsed as an
   identifier instead — replicate that fallthrough behavior, don't special
   case it away). It's a **float** iff the run contains exactly one `.`
   splitting it into two non-empty all-digit halves (`3.14` yes; `3.` or
   `.5` or `1.2.3` no — these fall through to other rules and will likely
   error or lex as separate tokens). It's an **int** iff the run is
   all-digits. Parse to a 32-bit signed int / 64-bit float; do not
   pre-empt host-language overflow behavior on out-of-range literals —
   replicate whatever the host numeric-parse does (the reference
   implementation lets Scala's parse throw uncaught here; you may choose
   to make this a clean lex error instead, but note it as a deliberate
   deviation if you do).
4. **Unary minus context sensitivity**: a `-` immediately followed by a
   digit run that would itself lex as a valid int/float literal is folded
   directly into a *negative* `IntLit`/`FloatLit` token, **but only if the
   previously emitted token is not one that a value can follow** — track
   "was the last token one of: int/float/string/bool literal, an
   identifier, `null`, `)`, `]`, `}`" as a single boolean/lookup on the
   token list built so far. If the last token *was* one of those, `-`
   lexes as plain `Sub` instead (binary subtraction). This must run
   *before* generic operator lexing swallows `-` as `Sub` unconditionally.
   Get the previous-token check right — it is the entire mechanism that
   makes `x - 1` lex differently from `[-1, 2]` or `f(-1)`.
5. **Keywords vs. identifiers**: for every reserved word (`fn if then
   else elif while do break continue for in struct impl private static
   locked try catch throw import as const null match case print skip
   return true false Int Str Bool Float Void Map`), check "does the
   identifier-scan-from-here exactly equal this word" (whole-word match:
   the character right after the word must not itself be a
   letter/digit/`_`, else it's a longer identifier, e.g. `structs` ≠
   `struct`+`s`). Order doesn't matter between keywords (they're mutually
   exclusive by exact string), but **all keyword checks must run before
   the generic identifier-fallback rule** (`[A-Za-z_][A-Za-z0-9_]*` →
   `Variable(name)`), which is your last-resort case.

   **A trap that only surfaces once you add generics (Phase 4.8)**: once
   an identifier can legally be followed immediately by `<` with **no
   whitespace** (`Stack<T>`), make sure whatever helper function your
   keyword/identifier rules use to "skip past the word just matched" stops
   at *every* operator-leading character — not just punctuation like
   `( ) { } [ ] , . ; :`, but also `< > = ! ~ ^ & |` — and does **not**
   simply keep consuming letters/digits past that point. A scanner that
   only special-cases whitespace and a narrow punctuation set as
   word-boundaries will, given `Stack<T>` with zero surrounding
   whitespace, correctly identify the word `Stack` but then silently
   swallow `<T>` as if it were trailing filler being skipped past — no
   `Lt`/`Variable("T")`/`Gt` tokens ever get emitted, and the parser sees
   `Stack` immediately followed by `{` with the type argument simply
   gone, no error raised anywhere. This is easy to miss because ordinary
   comparisons (`a < b`) almost always have surrounding spaces in
   practice, so the bug stays latent until something writes a bare
   generic-type usage with no space. Test this specific case explicitly:
   tokenize `Stack<T>` with **no spaces** and confirm you get `Variable
   (Stack)`, `Lt`, `Variable(T)`, `Gt` — four tokens, not one.
6. **String literals**: opening `"` begins a scan that consumes escapes
   (`\n \t \r \' \" \\ \a \b \f \v \0` → their control-character
   equivalents; any other `\x` is a lex error) until a closing `"`; EOF
   before the closing quote is a lex error.
7. **Multi-character symbols before single-character ones**, matched by
   1-character lookahead: `:=`, `=>`, `::`, `<<`, `&&`, `||`, `+=`, `-=`,
   `/=`, `*=`, `->`, `>>>` (3 chars — check before `>>`), `>>`, `>=`,
   `<=`, `==`, `!=`, then the single-character remainder: `+ - / % * ! ¬
   & | ^ ~ < > = : ; , . ( ) { } [ ]`.
8. Append a final `EOF` token (with the current line number) once input
   is exhausted.

**Why now:** the parser (Phase 3) consumes exactly this token stream;
nothing about parsing can be designed sensibly until you know precisely
what atoms it will see (in particular, the unary-minus behavior in step 4
changes what the parser ever has to handle as a prefix-negation case at
all — since negative literals already arrive pre-folded, the parser never
needs its own unary-minus-on-literals rule).

**Done when:** feeding every `.simp` file in `examples/` through your
lexer alone (dump the token list) produces no lex errors and, spot-checked
by hand against a few tricky lines (negative-number-in-array-literal,
`a - 1`, a line with `//` and `/* */` comments, a string with escapes),
matches the expected token sequence.

---

## Phase 3 — AST node types

**Build**, as plain data (no methods/logic yet):

```
SimpType   = Int | String | Float | Bool | Null | Type
           | Ref(SimpType) | Arr(SimpType) | Struct(name)
           | Map(SimpType, SimpType) | Pair(SimpType, SimpType)
           | Param(name)                     -- a generic type parameter, e.g. `T` (see Phase 4.8, 5.3)

Op         = Add Sub Mul Div Mod BitAnd BitOr BitXor BitComplement
             BitLeft BitRight BitRightFill
Bop        = Gt Lt Eq Gte Lte Neq

Expr       = Deref(loc) | Ref(loc) | Num(int) | Flt(float) | Str(string)
           | Bool(bool) | Null | BoolLift(BoolExpr)
           | BinaryOp(Expr, Op, Expr) | UnaryOp(Expr, Op)
           | FnCall(name, Expr[]) | ArrLiteral(Expr[]) | ArrIndex(Expr, Expr)
           | StructLiteral(typeName, (name,Expr)[]) | FieldAccess(Expr, name)
           | TypeLiteral(SimpType) | Pair(Expr, Expr)
           | Match(Expr, MatchArm[]) | Block(Cmd[], Expr)
           | MethodCall(receiver: Expr, methodName, Expr[])

MatchArm   = { pattern: Pattern, guard: Expr?, body: Expr }
Pattern    = Wild | Lit(Expr) | Var(name) | Struct(typeName, (name,Pattern)[])
           | Pair(Pattern, Pattern)

BoolExpr   = Literal(bool) | Compare(Expr, Bop, Expr) | Not(BoolExpr)
           | And(BoolExpr, BoolExpr) | Or(BoolExpr, BoolExpr) | FromExpr(Expr)

Cmd        = Skip | Assign(loc, Expr, line) | ConstAssign(loc, Expr, line)
           | TypeDecl(loc, SimpType, line) | Seq(Cmd, Cmd)
           | If(BoolExpr, thenCmd, elseCmd, line) | Scope(Cmd)
           | While(BoolExpr, Cmd, line) | For(loc, Expr, Cmd, line)
           | Print(Expr, line) | Return(Expr?, line)
           | ArrAssign(loc, index: Expr, value: Expr, line)
           | ArrAssignNested(loc, indices: Expr[], value: Expr, line)
           | FieldAssign(loc, field, value: Expr, line)
           | FieldIndexAssign(loc, field, index: Expr, value: Expr, line)
           | FieldIndexAssignNested(loc, field, indices: Expr[], value: Expr, line)
           | Continue | Break
           | Try(tryBody: Cmd, catches: CatchClause[], line)
           | Throw(errorType: name?, Expr, line)

CatchClause = { errorType: name, bindVar: name?, body: Cmd }   -- see Phase 5.5

Decl       = FnDecl(name, params: (name,SimpType)[], body: Cmd, returnType: SimpType,
                     isPrivate: bool, isStatic: bool)
           | StructDecl(name, fields: (name,SimpType,default: Expr?,isPrivate: bool)[],
                        isLocked: bool)
           | ImportDecl(path, alias)

Program    = PCmd(Cmd) | PDecl(Decl) | PImpl(structName, FnDecl[]) | PExpr(Expr) | PBool(BoolExpr)
```

**Why now, and why *before* the parser:** the parser's entire job is
"tokens in, one of these shapes out" — you cannot write a single parser
function without the return type already existing. Note this phase
deliberately does **not** include the *runtime* `Value` type — that's
Phase 6, needed only once you start evaluating, not while parsing.
`SimpType` is needed here (not deferred to Phase 6) because it appears
directly inside `Expr.TypeLiteral`/`Decl`/params — it's a syntactic type
annotation, produced by the parser, before it's ever used as a runtime
type-check target.

**Done when:** it compiles. There is no behavior to test yet.

---

## Phase 4 — Parser

Build this **innermost-grammar-rule-first**, because in a recursive-
descent parser every rule is a function that *calls* the next-tighter-
precedence rule — you cannot write `parseAddSub` before `parseTerm`
exists, and you cannot write `parseTerm` before the atomic-expression
parser exists. The dependency order below is the only order in which each
step can be written and unit-tested in isolation before the next needs it.

### 4.1 Parser scaffolding
A cursor over the token list: `peek()`, `peekNext()` (1-token lookahead
beyond current — needed for several disambiguations below), `advance()`,
`expect(token)` (assert-and-advance, else throw a parse error carrying the
current line number + source line text, matching the reference's error
style if you want matching diagnostics). No backtracking primitive is
needed globally — only one specific rule (4.3.6) needs a save/restore-
position + try/catch idiom; keep that local to that rule, don't build a
general backtracking framework.

### 4.2 Struct name pre-scan
Before any real parsing, do a **single raw linear pass over the entire
token list** looking for `Struct` immediately followed by
`Variable(name)`, and register `name` into your struct-name registry
(empty/placeholder field list for now — just the *name* needs to exist).
**Why here, before the real parser runs at all:** several parser rules
below (struct-literal syntax, static-method-call syntax) need to answer
"is this identifier a known struct type?" via simple lookahead *while
parsing*, including for structs declared *later* in the same file
(forward reference) — this pre-scan is what makes that possible without
a two-pass parser. Note it's a token-level scan (only checks `Struct`,
ignores whether `locked` precedes it — the struct's *name* position
relative to the `Struct` token is unaffected by a preceding `locked`
token, so this pre-scan needs no special-casing for the locked variant).

### 4.3 Expression parser, tightest precedence to loosest
Write these functions in this exact order (each is defined in terms of
the one above it):

1. **`parseType`**: needed by param/field/return-type parsing later, but
   write it now since nothing else depends on ordering here — it's a
   standalone recursive rule: `Int|Str|Bool|Float|Void|Map(K,V)|(A,B)|
   ref T|StructName`, each optionally followed by any number of `[]`
   suffixes (each `[]` wraps the type one level deeper in `Arr(...)`).
   For a bare identifier (the `StructName` case), first check it against
   a parser-level mutable set of **currently active generic type
   parameter names** (empty outside a generic `struct`/`impl` — see Phase
   4.8): if the identifier is in that set, produce `Param(name)` instead
   of `Struct(name)`. Either way, then check whether `<` immediately
   follows: if so, parse a comma-separated list of further types
   (recursively, via this same `parseType`) until `>`, and **discard**
   them — this is what lets a *usage* of a generic type in a type
   position (e.g. `self: Stack<T>`, or `Stack<Int>` as a return type)
   parse at all, without needing to do anything with the type arguments
   at runtime (see Phase 5.3's `containsTypeParam`/erasure rule). Only
   *after* that optional `<...>` do you apply the `[]`-suffix loop.
2. **`parseAtomicExpr`** — the base case, handles (in whatever internal
   order, since each is guarded by a distinct leading token so order
   only matters between overlapping-lookahead cases):
   - `~E` (bitwise complement, prefix, recurses into `parseAtomicExpr`
     again — this is why it's "tightest": `~a * b` parses as
     `(~a) * b`).
   - `match E { case P [if E] => E ; ... }` (full sub-grammar, see 4.6).
   - int/float/string/bool literals, `null`.
   - `[E, E, ...]` / `[]` array literal.
   - `Int`/`Str`/`Bool`/`Float` bare-keyword-as-value → a type-literal
     expression node (this is what lets `newMap(Str, Int)` work — the
     type names are parsed as ordinary expressions here, not specially
     inside `newMap`'s own call-parsing).
   - `namespace::name(...)` / `namespace::StructName{...}` — only when a
     `Variable` is immediately followed by `::` (1-token lookahead).
   - `StructName{f: E, ...}` — only when a `Variable` naming a *known*
     struct (per 4.2's registry) is immediately followed by `{`. Produces
     a struct-literal node with an empty type-argument list — valid as-is
     only for a non-generic struct (Phase 5.6 rejects it at *eval* time
     for a generic one with no ambient binding available; this isn't a
     parse-time distinction, since the parser doesn't know at this point
     whether an ambient binding will exist when this code actually runs).
   - `StructName<T0, T1, ...>` — only when a `Variable` naming a known
     struct is immediately followed by `<` (checked *before* the `Dot`
     case below, since both can follow a known struct name and `<` takes
     priority when present): parse a type-argument list the same way
     `parseType` does (4.3 step 1) — comma-separated full types via
     `parseType`, closing `>` — then look at what follows: `{` → a
     generic struct literal carrying this type-argument list (parse
     fields exactly as the plain case above, just with a non-empty
     type-argument list attached); `.` → produces the "type name with
     bound arguments" marker node the next bullet describes, just
     carrying this non-empty list instead of an empty one; anything else
     is a parse error (there's no third thing `<...>` could be followed
     by at an expression site).
   - `StructName.` — only when a `Variable` naming a known struct is
     immediately followed by `.` (**not** `{` or `<`) — this produces a
     "type name" marker expression (with an empty type-argument list)
     that static-method-call syntax needs (postfix parsing, 4.3.4, then
     attaches `.methodName(...)` on top of it exactly like it would for
     an instance). This is the same marker node the `<...>` case above
     produces when followed by `.`, just for the common case of calling
     a static method on a struct that either isn't generic, or is being
     called through the "inherit the ambient binding" path (Phase 5.6) —
     which, note, only applies to a bare `StructName{}` *literal* inside
     that struct's own methods, **not** to a bare static call: a static
     call on a generic struct always needs its own explicit `<...>`,
     since there's no receiver instance to inherit a binding from the way
     a literal can inherit one from its enclosing method.
     **Order matters**: check `{` first, then `<`, then bare `.` last —
     all three guard on the same `Variable(name)` where `name` is a known
     struct, distinguished only by the very next token, so they don't
     actually conflict, but keep them as distinct guarded cases rather
     than one combined one, for clarity.
   - `!variable` (deref).
   - `¬E` — switches into the *separate* boolean-expression sub-grammar
     (4.5) for its operand, then wraps the result back into an
     expression node (`BoolLift`). This is the one place the two parallel
     grammars (`Expr` and `BoolExpr`) cross over going this direction.
   - `name(...)` (only when `Variable` is immediately followed by `(`) →
     a function-call expression (arguments parsed via 4.3.3 below).
   - a bare `Variable(name)` with none of the above lookaheads → a plain
     variable-reference expression.
   - `{ ... }` — **block expression**, the one place you need real
     backtracking (see 4.3.6 below): repeatedly attempt to parse a
     statement (4.7) followed by `;`; the moment that attempt either
     throws a parse error *or* isn't followed by `;`, rewind to before
     that attempt and instead parse the remainder as a single trailing
     expression, then require a closing `}`. An immediately-empty `{}`
     with nothing parseable as a trailing expression is a hard error.
   - `(E)` / `(E, E)` — parenthesized expression vs. pair literal,
     disambiguated by whether a `,` follows the first inner expression.
3. **Call-argument list parser**: `(E, E, ...)` or `()`, reusable by
   function calls, method calls, and namespaced calls alike — write it
   once, used everywhere an argument list appears.
4. **Postfix parser**: wraps *any* already-parsed expression (initially
   the atomic expression from 4.3.2, but also reused for method-call
   chaining) in a loop that repeatedly consumes trailing `[E]` (array
   index) or `.name` — where `.name` immediately followed by `(` becomes
   a method call (recurse: parse another argument list via 4.3.3, wrap,
   loop again to allow further chaining like `a.b().c[0].d()`), otherwise
   becomes a field access, then loops again. Stops when neither `[` nor
   `.` follows.
5. **`parseTerm`**: `parsePostfix(parseAtomicExpr())`, then a left-
   associative loop consuming `* / %`, each time parsing another
   `parsePostfix(parseAtomicExpr())` on the right.
6. **`parseAddSub`**: `parseTerm()`, then a left-associative loop
   consuming `+ -`, each time parsing another `parseTerm()` on the right.
7. **`parseExprCore`** (this is *not yet* the final `parseExpr` entry
   point everything else calls — see 4.5, right after this, for the thin
   `&&`/`||`-adding wrapper layered on top of it; build this function
   first since 4.5's wrapper calls it): `parseAddSub()`, then a
   left-associative loop consuming `& | ^ << >> >>>` (bitwise — note:
   **looser than `+ -`**, not tighter as in C-like languages), each time
   parsing another `parseAddSub()` on the right; **then, once that loop
   is done, check once (not in a loop) for a trailing comparison operator
   `> < >= <= == !=`** — if present, parse the right-hand side by calling
   **`parseExprCore()` again** (not `parseAddSub`, and — critically, see
   4.5's trap — not the outer `&&`/`||`-including `parseExpr`), producing
   a right-recursive (not left-associative, not "flat chain") comparison
   node wrapped as a boolean-lifted expression. This means a chained
   comparison `a < b < c` parses as `a < (b < c)` — replicate this
   exactly, don't "fix" it into a flat left-to-right chain, since that
   would silently accept/evaluate differently on any real SIMP+ program
   containing a chained comparison (which will type-error at *runtime*
   either way, but a VM claiming compatibility should error in the same
   shape).

This completes `parseExprCore`. **`&&`/`||` are layered on top of it, not
part of it** — see 4.5 for the outer `parseExpr` this feeds into, and for
a real bug that hides here if you get the two functions' call sites
mixed up.

### 4.4 Constant folding (optional, purely an optimization pass)
If you want it: at each binary/unary/comparison construction point in
4.3.5–4.3.7, check whether both operands are already literal nodes
(`Num`/`Flt`/`Str`/`Bool`) and, if so, compute the result immediately and
return a literal node instead of a `BinaryOp`/`UnaryOp` node — **except**
skip folding a literal `/0` or `%0`, leaving it as a real `BinaryOp` node
so it throws SIMP+'s own "Division by Zero!" runtime error later instead
of crashing your parser/host-language's own division. Also fold `if`/
`while` whose condition parses to a literal boolean: a literal-`true` `if`
collapses to just its then-branch (the else-branch is discarded from the
tree entirely), a literal-`false` `while` collapses to a no-op. This
phase is **entirely skippable** — it changes nothing observable, since an
unfolded literal expression evaluates to the exact same value at runtime
anyway. Do it later, or never, without consequence.

### 4.5 `&&`/`||`, and the second, parallel `if`/`while`-condition grammar
`&&`/`||` are **ordinary expression operators**, usable anywhere an
expression is usable (a function-call argument, an array-literal element,
a struct-literal field value — anywhere `parseExpr`, 4.3.7, is called),
not restricted to a handful of "top level" positions. Implement this by
folding a `&&`/`||`-consuming loop directly into `parseExpr` itself, at
the very outermost/loosest layer — i.e. `parseExpr` becomes: parse one
level *below* `&&`/`||` (call this inner layer `parseExprCore` — it's
everything 4.3.7 already described: the bitwise loop plus the single
trailing comparison check), then loop consuming `&&`/`||` at equal
precedence, left-to-right (`a || b && c` groups as `(a || b) && c` — do
not give `&&` higher precedence than `||` the way most languages do),
each iteration parsing another `parseExprCore()` for the right operand
and combining both sides through a helper that turns a plain `Expr` into
a `BoolExpr` (unwrap if already a `BoolLift`; turn a literal `Bool` into
`BoolExpr.Literal`; otherwise wrap as `BoolExpr.FromExpr`) before
combining via `BoolExpr.And`/`Or`, then re-wraps the combined result back
into an `Expr` via `BoolLift`.

**A separate, second grammar** exists only for `if`/`while` conditions
(and `¬`'s operand): `parseBool`/`parseAtomicBool`, producing a `BoolExpr`
directly rather than an `Expr`-wrapped one. `parseAtomicBool` handles
literal `true`/`false` (optionally followed by `==`/`!=`), `¬` (recurse
into `parseBool`), a parenthesized `(B)`, or a plain expression position
(deref/literal/a postfix-expression starting with a variable — a call,
an index, or a dot-access) optionally followed by a comparison operator;
`parseBool` then wraps `parseAtomicBool` in the same left-to-right
`&&`/`||`-consuming loop, combining `BoolExpr`s directly (no `Expr`
wrapping needed, already in `BoolExpr` land).

**The trap, found by actually running this against real programs**: every
place *inside* `parseAtomicBool` that parses a comparison's right-hand
side — the literal-`true`/`false`-followed-by-`==`/`!=` case, and both
"postfix expression followed by a comparison operator" cases (call/index,
and dot-access) — **must call `parseExprCore()`, not the outer, `&&`/
`||`-including `parseExpr()`**, for that right operand. Get this wrong
(call the outer `parseExpr()` there instead) and a condition like
`while len(perm) > 0 && len(ops) > 0 do { ... }` silently misparses: the
comparison's own right-hand-side parse greedily swallows `0 && len(ops) >
0` *entirely into itself* (since that's exactly what the merged
`parseExpr` is now designed to do at its own top level), leaving the
outer `parseBool`'s own `&&`-loop with nothing left to consume, and
`len(perm) > (0 && len(ops) > 0)` runtime-errors as a type mismatch
(comparing an `Int` against whatever a `Bool && Bool` expression
evaluates to) instead of parsing as the intended `(len(perm) > 0) &&
(len(ops) > 0)`. This is exactly the kind of interaction bug that only
shows up on a real, moderately complex condition — a quick smoke test
with a single comparison and no `&&` won't trigger it at all, so test
specifically with a *chained* `X <op> Y && Z <op> W`-shaped condition
inside an `if`/`while` (Phase 12).

Elsewhere in the parser, everywhere that already called the old, narrower
`parseExpr` (call arguments, array-literal elements, struct-literal field
values, array indices, etc.) needs **no changes at all** to gain `&&`/
`||` support — that's the entire point of folding it into `parseExpr`
itself rather than maintaining a separate more-permissive wrapper only
reachable from a few call sites.

### 4.6 Match-arm pattern parser
`parsePattern`: `_` → wildcard; a literal token → literal pattern
(re-uses `parseAtomicExpr` to get the literal's `Expr` node, wrapped as
`Pattern.Lit`); `(P, P)` → pair-destructure; `Variable(name)` immediately
followed by `{` → struct-destructure (parses `name: Pattern` pairs,
comma-separated, until `}`); a bare `Variable(name)` otherwise → a
binding pattern. Used only by `parseMatch` (part of 4.3.2's atomic-
expression case for `match`), which additionally parses an optional
`if <Expr>` guard per arm and a `=>`-then-body-then-`;` per arm, looping
until `}`.

### 4.7 Statement (`Cmd`) parser
Now that `Expr`/`BoolExpr` are fully done, build the statement grammar —
**every** `Cmd` alternative's sub-expressions are parsed via 4.3's
`parseExpr` or 4.5's `parseBoolExpr`/`parseBool`, so this phase cannot be
written earlier. A single-statement dispatch function, keyed on the
current token:
- `skip` / `break` / `continue` → their respective leaf `Cmd`s.
- `const name := <parseBoolExpr>`.
- `Variable(l)` immediately followed by `.` → the field/index-assignment
  family (parse the field name, then branch on what follows: `[` → array-
  index-on-field assignment (single or, if further `[` follow, nested);
  `(` → actually a **method call used as a statement** (parse an argument
  list, build a method-call expression, wrap it as a discarded assignment
  to the sink location, see Phase 6.2 for what "sink" means — **this
  needs the exact same struct-vs-variable receiver disambiguation as the
  atomic-expression parser's dot-after-struct-name case**, so replicate
  that check here too, independently, since this is a separate code path
  from `parseAtomicExpr`, not a call into it); anything else → plain
  field assignment (`=`, `+=`, `-=`, `*=`, `/=`, each desugaring `+=` etc.
  into `l.f := !l.f <op> E` at parse time).
- `Variable(l)` immediately followed by `:` (not `:=`) → `l : T` type
  declaration (parse the type).
- `if` / `elif` → parse condition via `parseBool`, `then`, a `{ Cmd }`
  body, then optionally `elif`/`else` (an `elif` recurses into this same
  rule; if constant-folding a literal condition, per 4.4, fold now).
- `while` → condition via `parseBool`, `do`, `{ Cmd }` body.
- `for` → `Variable name`, `in`, an `Expr` (the iterable), `{ Cmd }` body.
- a bare `Variable(l)` (none of the above lookaheads matched) → the
  general assignment family: `[E] :=` (array-index assign, possibly
  nested with further `[E]`s), or `:=`/`+=`/`-=`/`*=`/`/=` (plain
  assignment, with the compound forms desugared the same way as field
  compound-assignment above, using `!l` for the current value).
- `print` → an `Expr`.
- `(` → an ambiguity between "a parenthesized command" and "a bare
  pair/parenthesized-expression used as a statement" — try parsing an
  `Expr` first; if a `,` or `)` immediately follows, it was actually an
  expression-as-statement (wrap it as a discarded assignment to the sink
  location, same as the method-call-statement case above); otherwise
  (parse failed, or something else followed) rewind and parse it as a
  parenthesized `Cmd` instead. This is the **second and only other**
  backtracking point in the whole parser (alongside 4.3.2's block
  expression) — implement it the same way (save position, try, catch,
  rewind).
- `return` → optionally followed immediately by `;`/`}`/EOF (a bare
  `return;`, no value) or else a `parseBoolExpr()` value.
- `{` → an anonymous scope block: parse a nested `Cmd` sequence until
  `}` (an empty `{}` is a valid no-op scope).
- `try` → `{`, a nested `Cmd` sequence (the try-body) until `}`, then
  **one or more** catch clauses, each: `catch`, an error-type name (an
  identifier that must be one of the fixed set from Phase 5.5 — reject
  anything else as a parse error, ideally naming the valid set in the
  message), optionally `as` followed by a variable name (the name the
  caught error's message will be bound under — omit `as` and no binding
  happens, useful when you only care that a particular type occurred),
  `{`, a nested `Cmd` sequence (the catch-body) until `}`. Keep parsing
  further `catch ...` clauses as long as `catch` keeps appearing (no
  separator needed between them). Build
  `Try(tryBody, catches: CatchClause[], line)`.
- `throw` → **two forms**, disambiguated by 2-token lookahead right after
  `throw`: if the next token is an identifier that's one of the fixed
  error-type names *and* the token after that is `(`, it's a **typed**
  throw — consume the type name, `(`, a `parseBoolExpr()` value, `)`, and
  build `Throw(Some(typeName), expr, line)`. Otherwise it's an
  **untyped** throw — just `parseBoolExpr()` for the value, building
  `Throw(None, expr, line)` (this raises the umbrella `Error` type at
  eval time — see Phase 5.5). Requiring the type name to be from the
  fixed set (not any arbitrary identifier) is what keeps this
  unambiguous with an ordinary `throw someFunctionCall(x);` — a call to
  a real function whose name isn't one of the six reserved type names
  falls through to the untyped path exactly as it should, evaluating the
  whole call as the message expression, not misparsing the function name
  as a type tag.

Then, **statement sequencing**: a top-level "parse a full `Cmd`" function
that parses one single-statement via the dispatch above, and — critically
— **if a `Fn`/`Struct`/`Locked`/`Import` token is encountered here**
instead (i.e. a declaration appearing where a statement was expected,
such as inside a function body or loop body), **immediately stop and
return a no-op `Skip`** without consuming it — declarations are not
statements and are not legal inside command bodies; this early-return
guard is what prevents the statement parser from ever trying to parse a
nested `fn`/`struct` and failing confusingly. After one statement, if a
`;` follows, consume it and — unless the next token is `EOF` or `}` —
recurse to parse another statement, joining the two with a `Seq` node;
otherwise return just the single statement.

### 4.8 Declaration parser
- `fn` → advance, then loop consuming any number/order of `private`/
  `static` modifier tokens (track two booleans), then a name, then a
  parameter list (`(name: Type, ...)` or `()`), `->`, a return type,
  `{`, a `Cmd` body (4.7), `}`.
- `struct` → advance, a name, then an **optional generic type-parameter
  list**: if `<` follows the name, parse a comma-separated list of plain
  identifiers (raw names, not full types — this is a *declaration* of new
  names, not a *use* of existing ones) until `>`. Before parsing the
  field list, install these names into the parser-level "currently active
  type parameters" set (4.3 step 1) — replacing whatever was active
  before, empty for a non-generic struct — and restore whatever was
  active beforehand once the field list is done (a plain save/set/parse/
  restore, no real nesting occurs in practice since structs never nest,
  but restoring defensively costs nothing). Then `{ field, ... }` where
  each field is an optional `private` marker, a name, `:`, a type
  (parsed with the type-parameter set active, so a field can use `T` —
  see 4.3 step 1), and an optional `= <Expr>` default, comma-separated,
  until `}` (a struct with zero fields — `{}` — is valid). **Unlike an
  erasure-only design, the declared type-parameter names here must be
  carried forward** — store them on the `StructDecl` node and, once
  registered, on the struct's definition (Phase 5.4/8.1) — because Phase
  5.6's reified-generics machinery needs to know, at construction time,
  how many type arguments a given struct expects and what to call each
  one when building the `{name -> concreteType}` binding map.
- `locked struct` → same as `struct` (including the optional `<...>`)
  but with a lock flag set; require the `struct` keyword to immediately
  follow `locked`.
- `import "path"` optionally followed by `as alias` (default alias, if
  omitted: the path's filename with its directory stripped and
  **everything from the first `.` onward** stripped too — i.e. splitting
  on `.` and keeping only the first segment, so `a.b.simp` aliases to
  `a`, not `a.b` — replicate this exactly, it is almost certainly not
  what a user would expect from a file named `a.b.simp`, but is the
  reference behavior).
- `impl StructName { ... }` → parse the same optional `<...>`
  type-parameter-name list described above right after `StructName`,
  install it as the active type-parameter set for the duration of parsing
  every method in this block (including each method's own body, via
  4.7's `l : T` statement form and any nested type annotations — the
  active set must stay installed across the *entire* block, not just each
  method's signature), restoring whatever was active before once the
  block's closing `}` is reached. Inside, repeatedly parse declarations
  via the `fn` rule above **only** (anything else inside an `impl` block,
  including a nested `struct`/`impl`/`import`, is a parse error) until
  `}`. Unlike the struct declaration's type-parameter list, **this one is
  not stored anywhere** — its only job is letting `T` parse as a type
  inside this block's method signatures/bodies (matched against the
  struct's own registered parameter names purely by *position*, not by
  the local name chosen here — nothing requires `impl Stack<T>` to spell
  its parameter the same way `struct Stack<T>` did, though doing
  otherwise would be needlessly confusing). The method dispatch table
  (Phase 8.1) is keyed only by the struct's base name (`"Stack"`, never
  `"Stack<T>"`) regardless.
- **Where a struct or static-call *expression* needs a type-argument
  list** (a generic struct literal `Stack<Int>{...}`, or a static call on
  one `Stack<Int>.new()` — Phase 4.3.2's struct-literal/static-call
  cases): parse `<`, then one or more comma-separated full types (via
  `parseType`, so a type argument can itself be another generic type, a
  built-in type, etc.), then `>`. Unlike the *declaration*-site list
  above (raw parameter *names*), this is a *use*-site list of actual
  types, evaluated and bound at runtime (Phase 5.6) — keep the two
  parsers/AST shapes distinct even though the punctuation looks the
  same, since declaring `<T>` and using `<Int>` are different grammar
  productions with different content.

### 4.9 Top-level program parser
Loop until `EOF`: if the current token is `Fn`/`Struct`/`Locked`/
`Import`, parse a declaration (4.8); if `Impl`, parse an impl block
(4.8's impl rule); otherwise parse a statement (4.7). Collect each into
the top-level `Program` list, consuming any stray `;` between items.

**Done when:** every `.simp` file in `examples/` parses with no errors,
and pretty-printing the resulting AST for a handful of hand-picked tricky
lines (a chained comparison, a bitwise expression mixed with `+`, a block
expression, a nested `if`/`elif`/`else`, a locked-struct-with-static-
factory declaration) matches what you'd expect from the precedence rules
above. **Do not proceed to Phase 5 until this milestone holds** — every
later phase assumes a correct AST is already in hand; debugging semantic
issues on top of a subtly-wrong parse is much harder than verifying the
parse first.

---

## Phase 5 — Runtime value model & environments

Now, and only now, start on the runtime (nothing here is needed by the
parser). Build in this order:

### 5.1 `Value` (the runtime tagged union)
```
Value = Int(i32) | Float(f64) | Str(string) | Bool(bool) | Null
      | Type(SimpType)                          -- a type used as a value, §Phase4.3.2
      | Ref(loc: string, store: Store)           -- an out-parameter binding, see Phase 8
      | Arr(TypedArray)
      | Struct(typeName: string, fields: MutableMap<string, Value>)
      | Map(entries: MutableMap<Value,Value>, keyType: SimpType, valueType: SimpType)
      | Pair(fst: Value, snd: Value)
```
`TypedArray` = a mutable, growable, ordered sequence of `Value` plus an
*optional* declared element `SimpType` (used only so an empty array can
still report/enforce a type — see 5.3's `getType`). Per Phase 0: `Arr`/
`Struct`/`Map` must alias (reference semantics) on copy; everything else
copies by value.

### 5.2 `Store` (the scope chain)
A mutable name→`Value` map plus a set of const-protected names, plus an
*optional* parent `Store` reference. Operations:
- `load(name)`: search this scope, then walk up parents; throw a
  `NameError` "unbound" (Phase 5.5) if never found in the whole chain.
- `store(name, value)`: search this scope, then walk up parents for an
  *existing* binding of `name`; if found anywhere in the chain, **mutate
  it in place at whichever scope owns it** (this is what makes ordinary
  `:=` behave like "assign to whatever `name` already refers to,
  wherever that is," not "always shadow in the current scope"); if not
  found anywhere, create a new binding **in the current scope only**.
  Throw if the name is const-protected at its owning scope.
- `declareConst(name, value)`: always binds in the *current* scope
  (never walks up), and marks it const-protected there.
- `child()`: a fresh `Store` whose parent is `this`.
- The literal name `"_"` is special: both `store` and `declareConst`
  silently no-op on it (never actually stored anywhere) — this is the
  language's "discard the result" sink, used pervasively as
  `_ := someCallForSideEffectsOnly();`.

**Why now, before the evaluator:** every single evaluator function below
takes a `Store` as its execution context; you cannot write `evalExpr`
without this existing first.

### 5.3 Shared runtime-utility functions
Implement these next — they have no dependency on control-flow/statement
execution (Phase 6+), only on `Value`/`SimpType`, so they can and should
be written, and unit-tested standalone, before the evaluator proper:

- **`getType(value) -> SimpType`**: the obvious 1:1 mapping for
  primitives; `Struct(name,_) -> Struct(name)`; `Map(_,k,v) -> Map(k,v)`;
  `Pair(f,s) -> Pair(getType(f), getType(s))` (derived from current
  contents each time, not cached); `Type(_) -> Type`; `Ref(loc,store) ->`
  resolve one level via `store.load(loc)` and recurse — but throw if
  *that* also turns out to be a `Ref` (nested refs unsupported); `Arr
  (elements) ->` if empty, `Arr(elements.declaredType ?? Int)` (**an
  untyped empty array defaults to reporting itself as `Int[]`** — a real,
  load-bearing default, not a placeholder, replicate it exactly); if
  non-empty, `Arr(getType(elements[0]))` (**the array's type is derived
  only from its first element** — no homogeneity is ever checked here or
  anywhere else; a mixed-type array is fully constructible), throwing if
  that first element is itself a `Ref`.
- **`isNullable(t) -> bool`**: `false` for `Int`/`Str`/`Bool`/`Float` — all
  four primitives reject `null`. Every other type (`Null` itself, `Type`,
  `Ref`, `Arr`, `Struct`, `Map`, `Pair`, and a generic `Param` — see
  below) is nullable.
- **`containsTypeParam(t) -> bool`**: `true` if `t` is `Param(_)` itself,
  or recursively contains one (`Arr(inner)`, `Ref(inner)`,
  `Pair(fst,snd)`, `Map(k,v)` — recurse into each sub-type); `false` for
  every concrete type. Used by `checkType` (next) to know when a type
  needs resolving against the current generic bindings before it can be
  checked at all — see Phase 5.6 for what those bindings are and how
  they're maintained; **generics here are reified, not erased** (unlike
  a first draft of this feature might reasonably assume from "Java-style
  `<T>` syntax" — the syntax is Java-like, the runtime behavior isn't:
  every generic-typed slot is actually checked, against whatever concrete
  type that specific instance/call was bound to).
- **`resolveTypeParams(t, typeBindings) -> SimpType`**: substitutes every
  `Param(name)` found in `t` (recursively, through `Arr`/`Ref`/`Pair`/
  `Map`) with `typeBindings(name)`. Throws `TypeError` "Unbound type
  parameter" if some `name` isn't in `typeBindings` — by the time this
  is ever called on a real program, a binding should always be present
  (Phase 5.6 guarantees one is pushed before any code that could hit a
  `Param`-typed slot runs); seeing this error indicates a real gap in
  your own binding-frame coverage, not something to paper over by
  falling back to "allow anything."
- **`checkType(value, expected, name, typeBindings) -> void|throw`**: the
  single dynamic type-check gate used everywhere a value flows into a
  typed slot. Takes the current generic type-bindings map as a fourth
  parameter (Phase 5.6) — every caller inside the evaluator passes
  whatever is currently on top of the type-binding stack; standalone
  callers with no generics involved (e.g. unit tests) can omit it,
  defaulting to empty, which is harmless as long as `expected` doesn't
  actually contain a `Param` (if it does with no binding, you get the
  "unbound type parameter" throw above, which is the correct outcome —
  there's no silent fallback). Every failure case below raises a
  `TypeError` specifically, not the generic `Error` type (see Phase 5.5).
  **First**, if `containsTypeParam(expected)`, replace it with
  `resolveTypeParams(expected, typeBindings)` before doing anything else
  — from this point on the check proceeds exactly as if `expected` had
  always been that resolved concrete type. Then: if `getType(value) ==
  Null`, pass iff `isNullable(expected)` (now correctly following
  whatever concrete type `T` resolved to — a `T` bound to `Str` rejects
  `null` exactly like a literal `Str`-typed slot would, a `T` bound to a
  struct type still accepts it). Else if `value` is an *empty* `Arr`: if
  `expected` isn't *some* `Arr(_)` at all, fail outright. Otherwise, check
  the empty array's own **declared element type**, if it has one — an
  empty array only carries a declared type when it came from an `x :
  T[];` statement (Phase 5.3/7.1); a bare `[]` literal never has one. If
  it has no declared type, there's nothing to check it against, so pass
  (permissive — a genuinely untyped empty literal still satisfies any
  array-typed slot). If it *does* have one, resolve it against
  `typeBindings` too (it can itself contain a `Param`, e.g. inside a
  generic method's own `x : T[];`) and require it to equal `expected`'s
  inner type **exactly** — do not skip this check just because the array
  happens to be empty right now: an `Int[]`-declared empty array must
  still fail against a `Str[]`-typed slot, even though it holds zero
  elements to inspect. (An earlier revision of this gate skipped this
  entirely — "any empty array satisfies any array type" — which is a
  real, easy-to-reach hole: any `x : Int[];` passed to a `Str[]`
  parameter before ever being pushed to would sail through unchecked.)
  Else (a non-empty array, or any non-`Arr` value), pass iff
  `getType(value) == expected` **exactly** (recursive structural equality
  on the whole type descriptor) — no numeric widening (`Int` never
  satisfies `Float` or vice versa at this gate — widening only ever
  happens via arithmetic *producing* a new Float value, never via a bare
  type check), no struct subtyping.
- **`deepCopyValue(value, visited) -> Value`**: primitives/`Null`/`Type`
  return as-is (immutable, nothing to copy); `Ref` returns as-is
  **unrecursed** (deep-copying through a ref keeps the same live
  reference, does not clone what it points to); `Arr`/`Map`/`Pair`
  recursively rebuild with freshly-copied contents; `Struct` recursively
  rebuilds its field map, tracking a `visited` set of already-seen
  field-maps by identity and **throwing** ("cyclical references
  unsupported") rather than looping if a struct transitively contains
  itself.
- **`getPrettyPrint(value, structEnv, visited) -> string`**: used by
  `print` and string concatenation (Phase 6). Ints/floats/bools via the
  host language's default numeric/bool-to-string; strings verbatim (no
  quotes); `Null -> "null"`; `Ref(name,_) -> "Ref($name)"`; `Map(_,k,v) ->
  "Map($k -> $v)"` (type names only, **never** the map's actual entries);
  `Type(t) -> "Type.$t"`; `Pair(f,s) -> "($f, $s)"` recursively,
  **threading the same `visited` set through to both `f` and `s`** (not a
  fresh empty one for each — a pair holding a struct or array that cycles
  back to something higher up the same print call still needs to detect
  that, and a naive per-branch reset would silently lose the cycle
  protection at exactly the one point it's needed); `Arr(elems) ->
  "[$e0, $e1, ...]"` recursively, `"[]"` if empty — and **cycle-safe in
  its own right**: track the array's own identity in `visited` before
  recursing into its elements, printing `"[...]"` if the same array is
  re-entered (this matters even without any struct involved at all — a
  bare array holding itself, e.g. via `push(arr, arr)`, is directly
  constructible and must not hang/crash `print`); `Struct(typeName,
  fields) -> "$typeName { $f0: $v0, ... }"` — mask any field the struct's
  declaration marks `private` as the literal text `"???"`
  **unconditionally** (no caller-context check here, unlike actual
  field-access privacy in Phase 8 — printing always masks private fields
  regardless of who's printing), and cycle-safe the same way: track the
  field-map's identity in `visited`, printing `"$typeName { ... }"`
  (literal three dots) if re-entered. Struct and array cycle-tracking
  share one `visited: Set[AnyRef]`, so a struct containing an array
  containing that same struct (or vice versa) is caught too, not just a
  struct cycling directly back to itself.

### 5.4 `FunctionEnv` and `StructEnv`
- `FunctionEnv`: a name→`FnDecl` map (user functions), a name→native-
  callback map (builtins, Phase 9), and a `(structName, methodName) ->
  FnDecl` map (every `impl`-block method, across possibly multiple `impl`
  blocks for the same struct — later registrations under the same key
  silently overwrite earlier ones, matching plain function re-declaration
  behavior).
- `StructEnv`: a name→`StructDef` map, where `StructDef` = `{ fields:
  (name,SimpType,default:Expr?,isPrivate)[], isLocked: bool }`. Needs a
  `preRegister(name)` that inserts a placeholder (empty field list,
  unlocked) — this is what Phase 4.2's parser-time pre-scan calls — and a
  `register(name, def)` that overwrites it with the real definition once
  the evaluator actually processes that struct's declaration (Phase 7).

**Done when:** unit tests for `getType`/`checkType`/`deepCopyValue`/
`getPrettyPrint` pass against a table of representative inputs (including
the empty-array edge cases and a cyclic-struct case for `deepCopyValue`)
with no evaluator/parser involved at all.

### 5.5 Typed runtime errors
Build this now, even though nothing throws one yet — every evaluator
function you write from Phase 6 onward needs this type to already exist
so each of *its own* error sites can be tagged correctly as it's
written, rather than retrofitted afterward across dozens of call sites
(which is exactly the order this reference implementation was built in,
and retrofitting was the more error-prone, harder-to-audit path).

**The error value itself**: a small record `{ errorType: string, msg:
string }`, thrown/raised via whatever your host language's error-
propagation mechanism is (an exception subclass, in most languages).
Every runtime error your evaluator raises — a type mismatch, an
out-of-bounds index, an unbound variable, a builtin's own failure, an
explicit `throw` — should go through this one shape, not a bare
string-only error, so that `try`/`catch` (built in Phase 7.2) has
something to match against.

**The fixed error-type catalog** — a closed set of six names, matching
what a `catch`/`throw TypeName(...)` clause is allowed to name (Phase
4.7):
```
Error       -- the umbrella: matches (is a supertype of) every type below
TypeError   -- a value doesn't match the type expected
ValueError  -- a correctly-typed value that's semantically invalid
IndexError  -- an array index out of bounds
KeyError    -- a map key that doesn't exist
NameError   -- an unbound variable, or an unknown struct/method/field/function
```
This is a **flat** hierarchy — none of the five specific types nest
under each other, they're all direct "subtypes" of `Error` and nothing
else. Matching a caught error's actual type against a `catch` clause's
requested type is exactly:
```
matches(caughtType, wantedType) = (wantedType == "Error") or (caughtType == wantedType)
```
An error not explicitly given a more specific type (see the mapping
below) defaults to plain `Error` — this includes any raw host-language
exception that escapes from somewhere you haven't wrapped (a file I/O
failure, a host runtime exception from deep inside a builtin, etc.): a
bare `catch Error as e` should still catch these, only a *specific*
`catch TypeError`/etc. won't match something that was never tagged more
specifically than the default.

**How to tag each error site** — as you write each phase's evaluator
code (Phase 6 onward), use this mapping for the errors described
elsewhere in this document. This is not exhaustive of every possible
error message (there are far more distinct messages than six types —
many builtin-specific "wrong argument shape" errors in Phase 9, for
instance, are reasonable to leave as plain `Error` unless you want to
invest in tagging them individually too), but covers every *category*
of failure discussed in this spec:
| Failure | Type |
|---|---|
| `checkType`'s null-rejection and type-mismatch throws (Phase 5.3) | `TypeError` |
| Wrong argument count to a function/method call (Phase 7.4) | `TypeError` |
| A `ref`-typed method parameter, or a non-variable argument to a `ref` parameter (Phase 8) | `TypeError` |
| An unsupported operator for the given operand types, "can't call method on non-struct value", static/instance call mismatch (Phase 6.4-6.5, 6.14) | `TypeError` |
| Division or modulo by zero (Phase 6.4) | `ValueError` |
| A missing required field with no default in a struct literal (Phase 6.9) | `ValueError` |
| A failed `assert` builtin call (Phase 9) | `ValueError` |
| An array index out of bounds, anywhere (Phase 6.7, 7.2) | `IndexError` |
| A map `get` on a key that doesn't exist (Phase 9) | `KeyError` |
| An unbound variable (`Store.load` failing, Phase 5.2) | `NameError` |
| An unknown struct type, unknown function, unknown method, or unknown struct field (Phase 5.4, 6.10, 6.14) | `NameError` |

**A subtlety with re-thrown/wrapped errors**: several places in this
spec describe catching a lower-level error just to add context and
re-raise it (e.g. any "load a location, and if that fails wrap the
message" pattern). When you do this, **preserve the original error's
type** rather than defaulting back to plain `Error` — otherwise a
`checkType` failure that happens to get re-wrapped somewhere upstream
would silently stop being catchable as `TypeError` specifically, only as
the generic umbrella. Concretely: your "re-throw with added context"
helper should extract whatever type tag the caught error already carried
(falling back to `Error` only if it didn't have one) and use that same
tag on the new error it raises.

**Why now, before Phase 6:** `checkType` (5.3, just built) is the very
first error-raising function in the whole evaluator, and it needs to
raise a `TypeError`, not a generic `Error`, from the moment you write it
— there's no point writing it untyped and coming back later.

### 5.6 Reified generics: the type-binding stack
Build this now too, alongside 5.5, for the same reason: `checkType`
already expects a `typeBindings` map to be passed in, and every
generic-aware evaluator function you write from Phase 6 onward (struct
literal construction, method dispatch, default-value construction) needs
to push/pop a frame onto this stack at exactly the right moments. Get
the mechanism nailed down before you start writing the functions that
depend on it, rather than threading it through as an afterthought.

**The core design decision, stated plainly:** a struct declared `struct
Stack<T> { ... }` is **reified**, not erased — this is a deliberate
choice, and it means "Java-style `<T>` syntax" describes the spelling
only, not the runtime semantics (Java itself erases). Every value that
flows into a `T`-typed slot (a field, a method parameter, a method
return) is checked against the *concrete* type that specific instance
was bound to at construction, exactly the way `Map(K, V)` already checks
its `get`/`set` calls against its own concrete `K`/`V` (Phase 9) — a
generic struct's type argument gets the same treatment `Map` already
gave its key/value types, just generalized to user-declared structs.

**The data:**
- A generic struct's instances (`Value.Struct`, Phase 5.1) carry an
  extra field: `typeArgs: Map<name, SimpType>` — e.g. a `Stack<Int>`
  instance stores `{"T": Int}`. A non-generic struct's instances simply
  carry an empty map; nothing about non-generic structs changes.
- `StructDef` (Phase 5.4) needs to record the struct's declared type
  *parameter names* (`["T"]` for `Stack<T>`) — captured once, when the
  declaration is parsed (Phase 4.8), and carried unchanged into the
  registered definition. This is the one place type-parameter *names*
  persist past parse time; every other generics-parsing detail (Phase
  4.3 step 1's "is this identifier currently an active type parameter"
  set) is purely local to parsing and discarded once a declaration is
  fully parsed.
- A single mutable stack, parallel to and pushed/popped in lockstep with
  the impl-context stack (Phase 8.2): `typeBindingStack: (structName,
  {name -> SimpType})[]`. `currentTypeBindings` reads the map from
  whatever frame is on top (empty if the stack is empty).

**Where a frame gets pushed** (always in a `try`/`finally` so it's
popped even if the body throws — identical discipline to Phase 8.2's
impl-context stack, and for the same reason: an exception must not leave
a stale frame behind for the next unrelated call to see):
1. **Constructing a struct literal** (Phase 6.9): resolve the binding
   first (see below), push `(typeName, binding)`, evaluate every field
   (so field-default expressions and `checkType` calls see the binding
   via `currentTypeBindings`), build the `Value.Struct` carrying that
   same binding as its own `typeArgs`, pop.
2. **Calling an instance method** (Phase 8.3's `callMethod`): push
   `(typeName, receiver.typeArgs)` — read straight off the receiver
   value, no re-resolution needed, since it was already resolved once
   at that instance's construction time. Pop after the call.
3. **Calling a static method** (Phase 8.3's `callStaticMethod`): push
   `(typeName, binding)` where `binding` comes from resolving the type
   arguments given *at this call site* (`Stack<Int>.new()`) — see below.
   Pop after the call.
4. **Constructing a default value for a bare `x : StructName;`
   declaration** (Phase 7.1's `defaultValueFor`): same as (1), resolved
   with no explicit arguments (so it only succeeds inside an ambient
   context, or throws — see below), pushed for the duration of building
   that struct's field defaults, popped after.

**Resolving a binding** — one shared piece of logic used by all four
call sites above, parameterized only by "were explicit type arguments
given at this exact spot, or not":
- Look up the struct's declared parameter names. If there are none
  (not generic), the binding is trivially empty — none of what follows
  applies, and generic-free code is completely unaffected.
- **If explicit type arguments were given** (`Stack<Int>{...}`,
  `Stack<Int>.new()`): arity-check them against the declared parameter
  count (throw `TypeError` on mismatch), then — **critically** —
  resolve each given argument against `currentTypeBindings` before
  binding it (`resolveTypeParams` from 5.3), *not* the raw parsed type.
  Skipping this step is a real bug, not a hypothetical one: it's exactly
  what happens when a generic struct's own method constructs another
  instance of itself using its *own* type parameter name explicitly —
  `fn static new() -> Stack<T> { return Stack<T>{}; }` — as opposed to
  the equivalent bare `Stack{}` (next bullet). Parsed literally, that
  inner `<T>` is just the placeholder `Param("T")`; only resolving it
  against the ambient binding turns it into the real concrete type the
  *caller* of `new()` chose. Storing the unresolved placeholder instead
  produces an instance that's bound to a type parameter that doesn't
  exist anywhere anymore — every subsequent `checkType` against it then
  fails `resolveTypeParams`'s "unbound type parameter" check, rejecting
  even correctly-typed values. Test both spellings (bare and explicit)
  and confirm they produce identical, correct bindings (Phase 12).
- **If no explicit type arguments were given** (`Stack{}`, written with
  no `<...>` at all): search the *current* `typeBindingStack` for a
  frame whose struct name matches this exact struct, and use its binding
  if found — this is what makes a generic struct's own static factory
  method able to write the bare, unparameterized `Stack{}` and have it
  correctly inherit whatever type the method itself was invoked with
  (case 3 above pushes that frame *before* the method body, containing
  this literal, ever runs). If no such frame exists (the bare literal
  wasn't written inside that struct's own generic context at all — e.g.
  at top-level, or inside some *other* struct's method), this is a
  `TypeError`: a generic struct can never be constructed without a bound
  type, from anywhere, by design — there is no "leave it unbound" option.

**What requires an explicit type argument, and what doesn't** — this is
a deliberate asymmetry, not an oversight: type arguments are **mandatory
at every construction site** (a struct literal or a static call that
produces a new instance) and **mandatory in type-annotation positions**
that reference the struct generically (`self: Stack<T>`, a field's
declared type, a return type) — but **never accepted at a plain instance
method call** (`s.push(1)`, never `s.push<Int>(1)`) or at a non-static,
already-existing-instance's field/method usage in general, since the
binding was already fixed at construction and there's nothing left to
specify. Reject `<...>` anywhere it would be redundant with an already-
bound instance, exactly as strictly as you require it at construction.

---

## Phase 6 — Evaluator: expressions

Build expression evaluation next, **before** statement execution (Phase
7) — even though a few expression forms (block expressions, match-arm
guards) technically need statement execution to already exist, structure
your code so `evalExpr` and `execCmd` are mutually recursive (call each
other), and write the pieces in this order so each one only needs
already-written pieces or a forward-declared stub of the next:

1. **Deref / plain-reference evaluation**: `!name` looks up `name` in the
   current store and, if what's stored there is a `Ref`, follows it one
   level through *its* store; a bare `name`-as-expression (not immediately
   followed by a call) does the same lookup **without** the extra
   ref-following step (it just returns whatever `store.load` gives back,
   which could itself still be a `Ref` value if that's genuinely what's
   stored — this asymmetry between `!name` and bare `name` matters for
   binding ref-parameters, see Phase 8).
2. **Literal evaluation**: `Num/Flt/Str/Bool/Null/TypeLiteral` map
   directly to their `Value` equivalents.
3. **Unary bitwise-complement**: `Int` only, else type-mismatch error.
4. **Binary arithmetic/bitwise/string-concat evaluation** — dispatch on
   the *runtime* types of both evaluated operands (not the static AST
   shape, which was only relevant to Phase 4.4's optional folding):
   - `Int,Int`: `+ - * % / & | ^ << >> >>>` all defined (`/`/`%` by zero
     throws "Division by Zero!").
   - `Float,Float` / `Int,Float` / `Float,Int`: `+ - * /` defined,
     result always widened to `Float` (convert both sides to the host
     float type first); no bitwise ops, no `%` for any Float-involving
     pair.
   - `Str, <anything>`: `+` only, concatenating the string with
     `getPrettyPrint` of the right operand (**not** a `toStr`-specific
     path — same underlying function, see Phase 5.3).
   - `Struct,Struct` **of the same type name**: `+ - * % /` dispatch to
     that struct's `_add`/`_sub`/`_mul`/`_mod`/`_div` method (Phase 8.5) —
     throw if the method doesn't exist; there is no fallback.
   - anything else: "Type mismatch in binary operation".
5. **Comparison evaluation** (a separate function from #4, feeding into
   `BoolExpr.Compare`, not `Expr.BinaryOp`):
   - numeric pairs (any Int/Float combination): widen and compare
     normally for all six operators.
   - `Str,Str` / `Bool,Bool`: `==`/`!=` only, else "unsupported
     operation".
   - `Arr,Arr` and `Pair,Pair`: `==`/`!=` only, via a **shared, cycle-safe**
     recursive `valuesEqual(v1, v2, visited)` helper (the same one the
     struct fallback below and `getPrettyPrint`'s cycle-tracking, 5.3,
     are built from — don't implement a second, separate equality
     routine just for arrays/pairs): compares elementwise/`fst`+`snd`
     recursively, and for `Arr` specifically, tracks the pair of arrays'
     own identity in `visited` *before* recursing into their elements
     (an identity-hash pair, exactly like the struct-field-map tracking
     below) so a value that cycles back to itself through an array
     terminates instead of infinite-looping/stack-overflowing. `Pair` is
     immutable and can't itself cycle back to its own identity, but still
     needs to thread `visited` through to its `fst`/`snd` in case one of
     them is a struct or array that does.
   - `Struct,Struct` **same type name**: `> >= < <=` dispatch to that
     struct's `_gt`/`_gte`/`_lt`/`_lte` method unconditionally (throw if
     missing — no fallback for ordering operators); `==`/`!=` dispatch to
     `_eq`/`_neq` **only if the struct actually defines one**, else fall
     back to the same cycle-safe `valuesEqual`/structural field-by-field
     comparison as above (recursively comparing every field's value,
     **including private ones** — this structural fallback does not
     check field privacy at all).
   - `Struct,Struct` **different type names**: `==`/`!=` only, always
     false/true respectively (never equal by definition); any ordering
     operator throws.
   - anything else: "Type Mismatch".
6. **BoolExpr evaluation** (`evalBool`): `Literal` returns directly;
   `Not` negates a recursive `evalBool`; `And`/`Or` **short-circuit** (do
   not evaluate the right side if the left already determines the
   result); `FromExpr` evaluates the wrapped `Expr` and requires the
   runtime result to be `Bool` (else throws); `Compare` calls #5.
7. **Array literal / index evaluation**: literal wraps each evaluated
   element into a fresh `TypedArray` with **no declared type recorded**
   (only the `l : T[]` statement form, Phase 7, ever sets one); index-read
   requires an `Int` index in bounds, else throws.
8. **Pair literal / field access**: literal wraps two evaluated sides;
   `.fst`/`.snd` on a `Pair` extract directly; any other field name on a
   `Pair`, or field access on anything that isn't a `Pair` or `Struct`,
   throws.
9. **Struct literal evaluation**: **before anything else**, check the
   struct's lock flag against the current "impl context" (Phase 8.3 — if
   this phase isn't written yet, stub it as "always allowed" and come
   back once Phase 8 exists, since structs/locking/impl-context are
   mutually interdependent enough that you may need to implement Phase 8
   partially in parallel with this step). Then, for each declared field
   in **declaration order**: if the literal supplies a value for it,
   evaluate and `checkType` that; else if the field has a default
   expression, evaluate *that* (**in the current call's store, not some
   struct-definition-time closure** — a default expression can reference
   whatever's in scope at each individual construction site, and can
   legitimately differ across calls if it references a mutable outer
   variable — this is surprising but is the target behavior, see Phase
   12); else throw "missing field". **Extra fields in the literal that
   don't correspond to any declared field are silently ignored** — do not
   error on them.
10. **Struct field access (read)**: requires `Struct`, checks the
    field's `private` flag against current impl-context (Phase 8.3),
    else returns the stored value.
11. **Block expression evaluation**: create a child `Store`, execute each
    collected `Cmd` in it via `execCmd` (Phase 7 — forward-reference it),
    then evaluate the trailing result `Expr` in that same child store and
    return its value.
12. **Match evaluation**: evaluate the scrutinee once; for each arm in
    order, attempt to match its pattern against the value (wildcard
    always matches and binds nothing; literal matches iff the evaluated
    literal equals the value; a plain variable pattern always matches and
    binds the whole value under that name; a pair pattern matches iff the
    value is a `Pair` and both sub-patterns match its `fst`/`snd`
    recursively; a struct pattern matches iff the value is a `Struct` of
    the exact same type name and, for every named sub-pattern, the
    field exists and its sub-pattern matches — **checking field privacy
    on every field touched during this match, even just to test it**,
    Phase 8.3); if a pattern matches, and it has a guard, evaluate the
    guard as a `BoolExpr.FromExpr` in a child store with the pattern's
    bindings pre-populated — only actually commit to this arm if the
    guard is true (else keep searching later arms, even ones that come
    textually after this "matched but guard failed" one); the first arm
    that both matches its pattern *and* passes its guard (or has no
    guard) is used: evaluate its body in a fresh child store with its
    pattern's bindings populated. No matching+passing arm at all throws
    "non-exhaustive".
13. **Function call evaluation**: look up the name in the **builtin**
    table first, unconditionally — if found, evaluate all arguments and
    invoke the native callback directly. **Only if no builtin exists
    under that exact name** does it fall back to the user-function table
    (Phase 7's calling convention). This means a user `fn` declaration can
    never shadow a same-named builtin — it becomes silently unreachable
    dead code, not an error.
14. **Method call evaluation**: evaluate the receiver expression first.
    If it evaluates to `Type(Struct(typeName))` (the "bare type name"
    marker from Phase 4.3.2), this is a **static** call: evaluate the
    arguments with no receiver prepended, look up
    `(typeName, methodName)`, throw if the found method is *not* marked
    static, else dispatch (Phase 8.4). If it evaluates to
    `Struct(typeName, _)`, this is an **instance** call: prepend the
    receiver value itself to the evaluated argument list, look up
    `(typeName, methodName)`, throw if the found method *is* marked
    static, else dispatch. Anything else as a receiver: throw "can't call
    method on non-struct value". In both cases, a missing `(typeName,
    methodName)` entry throws "no method found" **before** the
    static/instance mismatch check runs (get this ordering right — "not
    found" always wins over "found, but wrong calling convention").

**Done when:** you can evaluate arithmetic, string, comparison, array,
pair, and match expressions correctly in isolation (feed it hand-written
`Expr`/`BoolExpr` trees directly, bypassing the parser, to unit-test each
dispatch branch above one at a time) — struct/method/function pieces can
stay stubbed until Phases 7–8 exist, since they're mutually referential.

---

## Phase 7 — Evaluator: statements & functions

### 7.1 `defaultValueFor(type, store) -> Value`
Needed by both `l : T` (below) and struct-literal field defaults
(Phase 6.9, if you deferred it): `Int->0, Float->0.0, Str->"", Bool->
false, Void->Null`; `T[] -> an empty Arr whose TypedArray records
declaredType = T` (**the only place an empty array ever gets a non-
default declared type**); `Map(K,V) -> an empty Map`; `(A,B) -> Pair(
defaultValueFor(A), defaultValueFor(B))`; `struct S -> a fresh Struct`
with every field set to its own default-expression (evaluated in the
*current* store, same rule as Phase 6.9) if present, else recursively
`defaultValueFor` of that field's type; `Void`/`ref T` as a *value*-typed
target throw (you cannot default-construct a `Void` or a reference); a
generic `Param(_)` (or anything wrapping one, e.g. `Param("T")[]`'s
element position) → `Null` — there's no principled zero-value for an
unknown, erased type, so `null` is the permissive default (consistent
with `checkType`'s Phase 5.3 rule that a generic slot accepts `null`
unconditionally).

### 7.2 Statement execution (`execCmd`)
- `Skip`: no-op.
- `Scope(body)`: execute `body` in a **child** store.
- `Assign(loc, expr)`: evaluate `expr`; try loading `loc` first — if that
  succeeds and yields a `Ref`, write through it into the ref's own store
  instead; otherwise (load failed, or it wasn't a `Ref`) do a plain
  `store.store(loc, value)` (walk-up-or-create, per Phase 5.2 — this is
  what makes assigning to a `ref`-bound parameter transparently write
  back to the caller, see Phase 8).
- `ConstAssign(loc, expr)`: evaluate, `store.declareConst`.
- `TypeDecl(loc, t)`: `store.store(loc, defaultValueFor(t, store))`.
- `FieldAssign` / `FieldIndexAssign(Nested)` / `ArrAssign(Nested)`: load
  the target struct/array, re-check privacy on every struct-field touch,
  type-check the new value against the field's declared type for plain
  field assignment (**but not for array-index assignment — no element-
  type check is ever performed there**, so writing a mismatched-type
  value into an array slot succeeds silently), bounds-check array
  indices, then mutate the underlying collection **in place** (visible
  through every other alias of the same struct/array).
- `If(cond, then, else)`: evaluate `cond` via `evalBool`, execute
  whichever branch in a **child** store.
- `While(cond, body)`: re-evaluate `cond` before every iteration
  (including the first); each iteration's body runs in a **fresh** child
  store (a new one every time, not reused across iterations); catch a
  break-signal to stop the loop, catch a continue-signal to just skip to
  the next condition-check (see 7.3 for how these signals propagate).
- `For(loc, iterable, body)`: evaluate `iterable`, require an `Arr`;
  iterate by index, **re-reading the array's current length on every
  iteration** (not a length snapshot taken once up front) — if the body
  mutates the same array (e.g. via `push`), later iterations must see
  the updated length live; bind the loop variable via `declareConst` in
  a fresh child store per iteration; same break/continue handling as
  `While`.
- `Print(expr)`: evaluate, print `getPrettyPrint(...)` + newline.
- `Return`/`Break`/`Continue`: raise the corresponding signal (7.3).
- `Seq(a, b)`: execute `a` then `b` **in the same store, no new child
  scope** — this is the one construct that does *not* introduce scoping.
- `Try(tryBody, catches)`: execute `tryBody` in a **child** store, inside
  whatever your host's "catch an error" construct is. If it completes
  without an error, that's it — no `catches` clause runs. If it raises a
  genuine runtime error (Phase 5.5's typed error record — a type
  mismatch, an out-of-bounds index, a builtin's own error, a user
  `Throw`, etc.), extract its `errorType` (defaulting to `"Error"` if it
  wasn't one of your typed errors — e.g. a raw host exception that leaked
  through unwrapped), then walk `catches` **in the order they were
  written** looking for the first clause whose `errorType` matches per
  Phase 5.5's `matches(caughtType, wantedType)` rule. If none match,
  **re-raise the original error unchanged** — it keeps propagating
  outward exactly as if this `Try` weren't there at all (up to an
  enclosing `try`, or a crash if there isn't one). If one matches, create
  a **separate fresh child** store (of the *original* store `Try` was
  invoked in, not of `tryBody`'s now-abandoned child store), bind the
  error's message as a `Str` under that clause's bind-variable (if it has
  one — a clause with no `as` binds nothing) via `declareConst`, and
  execute that clause's body in the new store. **Critical: this must
  catch only genuine runtime errors, never the `Return`/`Break`/
  `Continue` signals from 7.3** — those must still unwind straight
  through an enclosing `try`, exactly as they unwind through an
  `if`/`while`/block. If your host language uses exceptions for both
  (runtime errors *and* the control-flow signals), give the control-flow
  signals a distinct exception type/class that your `try`
  implementation's catch clause deliberately does not match (this is why
  Phase 7.3 calls for the signals to **not** extend whatever your generic
  runtime-error exception type is, if you're using exceptions for both
  mechanisms) — getting this wrong means a `return` written inside a
  SIMP+ `try` block would be silently swallowed and misreported as a
  caught error instead of actually returning from the function.
- `Throw(errorType, expr)`: evaluate `expr`; if it's a `Str`, use it
  directly as the error message, otherwise pretty-print it (Phase 5.3's
  `getPrettyPrint`); raise a typed error (Phase 5.5) carrying that
  message, tagged with `errorType` if present, else the umbrella
  `"Error"`.

### 7.3 Non-local control flow
Implement `return`/`break`/`continue` as whatever your host language's
idiomatic non-local-exit mechanism is (exceptions in a GC'd language;
an explicit `enum Signal { Return(Value?), Break, Continue }` propagated
as a return-code/out-parameter through every `execCmd` call in a
non-exception language). The unwind boundaries are:
- `Return` unwinds exactly to the nearest enclosing **function-call**
  boundary (7.4) — it passes straight through any number of intervening
  loops/blocks/ifs without being caught by them.
- `Break`/`Continue` unwind exactly to the nearest enclosing **loop**
  boundary (`While`/`For` in 7.2) — a `Return` inside a loop is *not*
  intercepted by the loop; it keeps unwinding past it to the function
  boundary.
- `Break`/`Continue` used with no enclosing loop, or `Return` with no
  enclosing function call, are **not** caught anywhere and should
  propagate all the way out as an unhandled/crash condition, matching the
  reference implementation — or, better, make this a dedicated parse-time
  or pre-execution static check instead (a simple loop-depth/function-
  depth counter suffices, since it requires no data-flow analysis) if you
  want a cleaner error than "crash"; either choice is defensible, just
  make it deliberately (see Phase 12).
- If you implement these three signals as exceptions, make their
  exception type/class **distinct from and not a subclass of** whatever
  general-purpose "runtime error" exception type the rest of the
  evaluator throws (type mismatches, out-of-bounds, `Throw`, etc.) — this
  is what lets `try`/`catch` (7.2) catch only genuine errors while
  letting `return`/`break`/`continue` unwind straight through untouched.

### 7.4 Function calling convention
Two argument-binding strategies, used in different places:
- **From-`Expr`-arguments** (used only for plain free-function calls,
  Phase 6.13): for each declared parameter, if its type is `ref T`, the
  *argument expression at that position* must itself be a bare-variable-
  reference AST node (not a dereference, not a computed value) — bind a
  `Ref(loc, callerStore)` under the parameter name in a **brand-new,
  parentless** store, after checking the *currently-stored* value there
  against `T`; otherwise (non-`ref` param), evaluate the argument
  expression in the *caller's* store, `checkType` it against the
  declared param type, and bind the resulting value under the parameter
  name in the new store.
- **From-already-evaluated-`Value`-arguments** (used only for method
  calls, Phase 8.4): same as above but every argument is already a
  `Value`, no `Expr`/ref-detection logic applies at all — **a `ref`-typed
  parameter on a method is a hard error**, not silently ignored; reject
  it the moment such a method is invoked (or, better, at declaration
  time).
- Both strategies throw immediately on arity mismatch (no partial
  application, no default parameter values, no variadics).
- **Every function/method call creates a brand-new store with *no*
  parent** — functions never close over the caller's local variables;
  the only names visible inside a call are its own parameters plus
  whatever it declares for itself. This is the single most important
  scoping fact for the whole language: there are no closures.
- After binding params, execute the body (7.2); catch the `Return`
  signal from 7.3 to obtain the function's result. A non-`Void`-declared
  function that falls off the end of its body with no `Return` signal at
  all is an error ("no return statement"); a bare `return;` (no value)
  inside a non-`Void` function, or `return <value>;` inside a `Void`
  function, are both errors; whatever value *is* returned gets
  `checkType`-ed against the declared return type before being handed
  back to the caller.

**Done when:** you can run whole `.simp` files that use only free
functions, loops, arrays, and basic control flow (no structs/methods/
imports yet) and get correct output — e.g. a simple recursive Fibonacci
or a string-processing script — end to end through lexer → parser →
this phase.

---

## Phase 8 — Structs, `impl` blocks, methods

Depends on Phase 7 (method calls reuse its "from-Value-arguments" calling
convention) and completes Phase 6's stubbed-out struct/method pieces.

### 8.1 Struct & impl registration
When your top-level program-execution loop (Phase 10) processes a
`StructDecl`, call `StructEnv.register` (overwriting whatever placeholder
Phase 4.2's pre-scan installed) with the real field list and lock flag.
When it processes a `PImpl(structName, methods)` entry, register every
method into `FunctionEnv`'s `(structName, methodName) -> FnDecl` table —
**this must happen in top-to-bottom program order, at evaluation time,
not eagerly for the whole file up front** — calling a method whose
`impl` block appears later in program order than the call site will fail
even though the struct's *name* was already visible everywhere (from the
Phase 4.2 pre-scan). Multiple `impl` blocks for the same struct, and/or
re-declaring the same method name within one, all funnel into the same
table with later registrations overwriting earlier ones under an
identical `(structName, methodName)` key.

### 8.2 The "current impl context" stack
Maintain a single mutable stack of struct type names, pushed with the
struct's `typeName` on entering `callMethod`/`callStaticMethod` (8.4,
below), popped on exit (in a `finally`/`defer`-equivalent so it pops even
if the call throws). **Plain free-function calls never push or pop this
stack.** Three checks all consult only the **top** of this stack:
- `checkFieldPrivacy(typeName, field)`: if the field is declared
  `private` and the stack's top isn't exactly `typeName`, throw.
- `checkMethodPrivacy(typeName, methodName)`: same, for a `private`
  method.
- `checkStructLock(typeName)`: if the struct is declared `locked` and
  the stack's top isn't exactly `typeName`, throw (on struct-literal
  construction, Phase 6.9).

The consequence worth internalizing precisely (it's easy to get subtly
wrong): since only free-function calls leave the stack untouched, a
struct's private/locked access is preserved across any number of nested
*free-function* calls made from inside one of its own methods (the stack
still shows that struct on top when such a nested free function itself
calls back into another method of the *same* struct), but is immediately
lost the moment control passes through a call to a method of a
*different* struct (which pushes that other struct's name on top instead)
— there is no "friend" mechanism and no transitivity across struct
boundaries, only this single-frame, method-call-boundary-only stack.

### 8.3 Method dispatch
`callMethod(typeName, methodName, argValues)` (instance path) and
`callStaticMethod(typeName, methodName, typeArgs, argValues)` (static
path — note it additionally takes the type arguments given at the call
site, e.g. the `Int` in `Stack<Int>.new()`) both: look up `(typeName,
methodName)` in the method table (throw "no method found" if absent,
**before** checking anything else); then check the found method's
static/instance flag matches the path being used (throw a specific
"static method called on instance" / "instance method called
statically" error if not); then run `checkMethodPrivacy`; then push
`typeName` onto the impl-context stack **and** push a matching frame onto
the type-binding stack (Phase 5.6) — for the instance path, the pushed
binding is read straight off the receiver's own `typeArgs` (already
resolved, from whenever that instance was constructed); for the static
path, it's resolved fresh from `typeArgs` given at *this* call site
(arity-checked against the struct's declared parameter count, and
each argument re-resolved against whatever binding was already active
before this call, per Phase 5.6's explicit-argument-resolution rule) —
then run the 7.4 "from-Value-arguments" calling convention against the
method's declared params and the given `argValues` (for the instance
path, `argValues` already has the receiver struct value prepended as the
first entry — by whichever param name the method declares first,
conventionally but not structurally `self`; for the static path, no such
prepending happens); then pop **both** stacks (even on throw) — they're
pushed and popped together, in lockstep, at exactly the same two
boundaries (method-call entry/exit), so treat them as one combined
"enter/exit a struct's own context" operation with two parallel pieces
of state, not two independently-timed mechanisms.

### 8.4 Operator-overload dispatch table
A fixed mapping consulted only when both operands of a binary op or
comparison are `Struct` values of the **same** type name (Phase 6.4/6.5):
`+ -> _add`, `- -> _sub`, `* -> _mul`, `/ -> _div`, `% -> _mod`,
`> -> _gt`, `>= -> _gte`, `< -> _lt`, `<= -> _lte`, `== -> _eq` (only
consulted if defined; else falls back to deep structural equality, Phase
6.5), `!= -> _neq` (same fallback rule). Each, if the struct defines it,
is an ordinary method (subject to the same privacy/dispatch machinery
above) taking `(self: S, other: S) -> T`.

**Done when:** you can run programs using struct declarations (with
`private`/`locked` fields and static/instance methods) and get identical
observable behavior to the reference for the polymorphism, operator-
overload, and privacy examples in [SYNTAX.md](SYNTAX.md).

---

## Phase 9 — Builtins

Implement the full catalogue in **[BUILT-IN-FUNCTIONS.md](BUILT-IN-FUNCTIONS.md)**
— every entry there needs an exact argument-count/type-shape dispatch
(matching argument shapes exactly; an unrecognized shape should throw a
builtin-specific `"<fn> expects ..."`-style error) and matching side
effects. This phase has no dependency on Phase 8 (register builtins as
soon as `FunctionEnv`/`Value` exist, Phase 5) but is placed here because
it's easiest to test against real `.simp` programs once the rest of the
evaluator works. Cross-cutting rules to get right, not obvious from the
signature table alone:
- `push`/`pop` mutate their array argument **in place**; `push` also
  type-checks the pushed value against the array's `declaredType` if one
  is set (an array with no declared type — the common case, since only
  `l : T[];` sets one — accepts anything). `back` peeks without
  mutating. `slice`/`reverse`/`flatten`/`sum`/`zip`/`toArr`/`split`/
  `range` all construct and return a **new** array/string, leaving the
  input(s) untouched.
- `deepCopy`/`isNull` are the only two builtins fully polymorphic over
  every `Value` variant.
- `pow(Int, Int)` with a non-negative exponent computes an **exact**
  integer result via fast exponentiation (not via a floating-point
  `pow` — this avoids rounding error for integer powers); a negative-
  exponent Int/Int `pow` falls through to a float computation and
  returns a `Float`.
- `random(min, max)` is `min`-inclusive, `max`-**exclusive**.
- `toBinary` on a negative `Int` should match your host's 32-bit two's-
  complement binary-string convention (e.g. Java/Scala's
  `Integer.toBinaryString`), not a sign-prefixed magnitude, if you want
  exact parity.

**Done when:** every example program in `examples/` that exercises
builtins runs and produces the same printed output as the reference
implementation.

---

## Phase 10 — Program driver & imports

### 10.1 Top-level program evaluation
Walk the `Program` list **in source order**, dispatching each item:
`PDecl(FnDecl)` → register into `FunctionEnv`; `PDecl(StructDecl)` →
register into `StructEnv` (8.1); `PImpl` → register methods (8.1);
`PDecl(ImportDecl)` → process the import (10.2); `PCmd` → `execCmd`
against the shared top-level `Store`; `PExpr`/`PBool` (REPL-only
convenience forms, Phase 11) → evaluate and print the result.
**Registration is genuinely sequential** — a call to a function/method
declared later in the file than the call site will fail at the point of
the call (except struct *names*, which are all pre-visible per Phase
4.2), so don't be tempted to "hoist" all declarations before running any
code — that would silently accept programs the reference implementation
rejects.

### 10.2 Imports
`import "path" [as alias]`:
- Resolve `path` relative to the **importing file's own directory**
  (propagate "current directory" recursively for nested imports —
  a transitively-imported file's own imports resolve relative to *its*
  location, not the original top-level file's).
- Canonicalize the resolved path for cycle detection; if it's already
  "in progress" (currently being processed, tracked via a simple
  in-progress set pushed/popped around the whole import), throw a
  circular-import error.
- If this exact `(path, alias)` pair has already been **fully**
  completed previously, silently no-op (don't re-parse, don't
  re-register, no error) — but if the same path was already imported
  under a *different* alias, this is a fresh import that *does* run
  fully again, registering everything a second time under the new alias
  too.
- Parse the imported file **completely independently** — its own
  lexer/parser run, with its own fresh, empty `StructEnv` (not the
  importer's) — so identifiers inside it resolve only against its own
  file's declarations while it's being parsed.
- `FnDecl`/`StructDecl`/`ImportDecl`/`PImpl` top-level items are all legal
  inside an imported file — **an `impl` block is imported along with its
  struct**, methods and all, not just the bare field layout. (An earlier
  revision of this design rejected `PImpl` here entirely, meaning a
  struct's methods were silently lost across an import boundary — that
  was a real, needless limitation, not a deliberate one: nothing about
  method dispatch is actually import-unaware, since methods are looked up
  dynamically by the *value's own runtime type name* — Phase 8.3 — which
  works identically whether that name happens to contain `::` or not.)
- Before registering anything, collect two sets from the freshly-parsed
  program: every name declared by a top-level `FnDecl`, and every name
  declared by a top-level `StructDecl`. Bundle these two sets plus the
  alias into one small "import context" value — every qualification step
  below takes it as a parameter, so name this once and thread it through,
  rather than re-deriving it per declaration.
- **Qualify every type**, not just every call — this is the part easy to
  under-scope: a method's `self: Stack<T>` parameter, parsed while still
  inside the imported file's own unqualified namespace, produces a bare
  `Struct("Stack")` type. If you only qualify *function calls* and never
  touch *type annotations*, that `self` parameter's declared type never
  matches the runtime value's actual type once it's constructed as
  `alias::Stack` — every single call to every method would then fail its
  own `self`-parameter type check. Write one recursive `qualifyType`
  function: given a `SimpType` and the import context, rewrite any
  `Struct(name)` where `name` is in the context's struct-name set to
  `Struct("$alias::$name")`, recursing into `Arr`/`Ref`/`Pair`/`Map`'s
  inner types (a `Param` or a primitive passes through unchanged). Apply
  it to **every** function's and every method's parameter types and
  return type, and every struct's field types, not just to expressions.
- **Qualify the full `Cmd`/`Expr`/`BoolExpr`/`Pattern` tree, exhaustively**
  — every single constructor of all four, recursing into every sub-
  position that can contain another `Expr`/`Cmd`/type. This replaces an
  earlier, narrower design that only descended into
  `Seq`/`If`/`While`/`Return`/`Assign` commands and `BinaryOp`
  expressions, missing (among others) any call made from inside a `for`
  loop, a `print`, a method call's own arguments, a struct-literal field
  value, a nested block expression, or a `match` arm — a recursive call
  made from inside any of those shapes would resolve against the wrong,
  unqualified, global name, either silently calling an unrelated function
  if one coincidentally exists under that bare name in the *importing*
  program, or throwing "not found." Concretely, qualify:
  - Every `Expr.FnCall(name, args)` where `name` is in the declared-
    function-name set → rename to `$alias::$name`, and recurse into
    `args` regardless.
  - Every `Expr.StructLiteral(typeName, fields, typeArgs)` and
    `Expr.StructTypeRef(typeName, typeArgs)` where `typeName` is in the
    declared-struct-name set → rename `typeName`, and additionally run
    `qualifyType` over each entry of `typeArgs` (a type argument can
    itself reference another locally-declared struct) and recurse into
    each field value.
  - Every `Pattern.PStruct(typeName, fields)` (inside a `match` arm) the
    same way, recursing into sub-patterns and any literal-pattern
    expression.
  - Every plain `SimpType` appearing anywhere (a `Cmd.TypeDecl`'s type, a
    struct field's declared type, a function/method's param types and
    return type) via `qualifyType`.
  - Everything else structurally: every `Cmd` variant recurses into
    whichever `Expr`/`BoolExpr`/nested-`Cmd` fields it has (a `Cmd.Try`
    qualifies its try-body and every catch clause's body; a `Cmd.For`
    qualifies its iterable expression *and* its body — this specific one
    was the concrete gap named above); every `Expr` variant recurses into
    its sub-expressions (a `Block`'s commands and result, a `Match`'s
    scrutinee/arms/guards, a `MethodCall`'s receiver and arguments — note
    a method *name itself* is never qualified, since method dispatch
    doesn't use qualified names, only the receiver's struct-literal/
    type-ref *type* does); every `BoolExpr` variant recurses the same way
    `Expr`'s `&&`/`||`/comparison/negation cases do. Write this as one
    exhaustive match per type with **no wildcard fallback case** — let
    your compiler tell you if you've missed a constructor, rather than
    silently passing an un-recursed node through unchanged.
- Register each qualified function into the **importer's** shared
  `FunctionEnv` under `"$alias::$name"`, each qualified struct definition
  into `StructEnv` under `"$alias::$name"` (field types and field-default
  expressions qualified the same way), and — the new part — each
  qualified method into `FunctionEnv`'s method table under the key
  `("$alias::$structName", methodName)` (the **method name itself stays
  bare** — only the struct-name half of the dispatch key is qualified,
  matching how dispatch already works: it's keyed by the constructed
  value's own runtime type name, which is exactly `$alias::$structName`
  once you construct one via the qualified path).
- `namespace::name(...)` (qualified function call) resolves directly
  against `FunctionEnv` under that qualified key, with **no existence
  check at parse time** — it's built unconditionally into an
  `Expr.FnCall`, and only fails, if it fails, at evaluation time. The
  qualified struct-construction forms (below) must work the **same way,
  for a reason that isn't obvious until you actually try it**: a
  register-then-parse ordering trap. Do not guard qualified
  struct-literal/type-ref parsing behind a `structEnv.exists("$alias::
  $name")` check — parsing the *entire* importing file completes fully
  *before* any of its top-level items (including its own `import`
  statements) are evaluated, so at the moment the parser reaches, say,
  `alias::Stack<Int>{...}`, on some line below `import "..." as alias;`
  in the very same file, `alias::Stack` has certainly not been
  registered into `StructEnv` yet — evaluation of the `import` line
  itself hasn't happened. An existence-guarded parse would make the
  qualified-construction syntax permanently unusable in exactly the
  single-file "import, then immediately construct" pattern that's the
  entire point of the feature, throwing a confusing "expected name after
  '::'" parse error instead. This is safe to do unconditionally (unlike
  the *unqualified* struct-literal case, Phase 4.3.2, which genuinely
  needs its existence check to disambiguate a struct literal from a bare
  variable reference followed by an unrelated `{`-block) because there is
  no other valid parse for `namespace::Name` followed by `{`/`<`/`.` in
  this grammar at all — a qualified name with nothing sensible following
  is already a hard parse error regardless, so treating `{`/`<`/`.` as
  "this is a struct construct" unconditionally loses nothing and fixes
  a real, previously totally-broken code path.
- `namespace::StructName{...}` / `namespace::StructName<T0,...>{...}`
  (qualified struct literal, with or without generic type arguments) and
  `namespace::StructName.method(...)` / `namespace::StructName<T0,...>
  .method(...)` (qualified static call, with or without generic type
  arguments) all parse the same way their non-namespaced equivalents do
  (Phase 4.3.2), just building the qualified name throughout. There's no
  nested/multi-level namespacing (`a::b::c` isn't meaningful; exactly one
  `::` is recognized).

**Done when:** a small multi-file example produces correct output for
*all* of: a plain recursive function call across the boundary; a
recursive call made from inside a `for` loop in the imported file (the
narrow-rewrite gap, above); and — the harder case — importing a `locked`,
generic struct with a full `impl` block (a static factory method and at
least one instance method), constructing it via
`alias::StructName<Concrete>.method()`, and confirming type enforcement
still rejects a wrong-typed value passed to one of its methods *through*
the import boundary, exactly as it would for a locally-declared struct.

---

## Phase 11 — CLI / REPL (optional, only for full parity with the reference tool)

A minimal driver: read a filename argument, or start an interactive loop
if none given. For a REPL, the one piece of real complexity is deciding
when a multi-line input is "complete enough to evaluate" — the reference
implementation's heuristic is: brace-balanced (`{` count == `}` count)
**and**, if there are any braces at all, not "an `if` with no matching
`else` yet" (a crude token-presence check, not real parse-completeness
detection — replicate it as-is, or design something better if REPL
fidelity to the reference tool isn't a goal). Otherwise this phase is
straightforward glue over everything already built and imposes no new
language-semantic requirements.

---

## Phase 12 — Conformance checklist

Before considering the implementation "done," verify each of the
following deliberately (each is a specific point where a naive fresh
implementation is likely to diverge from the reference without realizing
it) — write one small test program per item:

1. `a < b < c` throws a runtime type error (right-recursive chained
   comparison — Phase 4.3.7), it does not silently mean `(a<b) and (b<c)`.
   **Keep as-is** — this is not a bug to fix, it's the specified behavior.
2. `&&`/`||` work as **ordinary expression operators everywhere** — a
   function-call argument, an array-literal element, a struct-literal
   field value, all without needing a block-expression workaround (Phase
   4.5) — not just in `if`/`while` conditions/assignment RHS/etc. Confirm
   this *and* confirm the easy-to-miss companion case: a condition that
   mixes a comparison with `&&`/`||` inside `if`/`while` still parses
   correctly (`while len(a) > 0 && len(b) > 0 do {...}` must parse as
   `(len(a) > 0) && (len(b) > 0)`, not have the first comparison's
   right-hand side swallow the rest of the condition — Phase 4.5's trap).
3. `/* outer /* inner */ still commented */` is **one comment** from the
   first `/*` to the *second* `*/` — block comments nest (Phase 2 step 2).
   **Fixed** — an earlier revision ended at the first `*/`.
4. `x - 1` lexes as subtraction; `[-1, 2]`, `f(-1)` lex the `-1` as one
   negative literal token (Phase 2 step 4). **Keep as-is.**
5. An empty `Int[]`-declared array does **not** satisfy a `Str[]`-typed
   slot — `checkType` now checks a declared-but-empty array's element
   type strictly, only staying permissive for a genuinely undeclared
   bare `[]` literal with nothing to check against (Phase 5.3). **Fixed**
   — an earlier revision let any empty array satisfy any array type.
6. `break`/`continue`/`return` used outside any loop/function — an
   unhandled crash (matching the reference, Phase 7.3). **Leave as-is** —
   not treated as a bug to clean up into a parse-time error.
7. `arr1 == arr2` / `pair1 == pair2` on values containing a cycle back to
   themselves terminate correctly (`Arr`/`Pair` equality routes through
   the same cycle-safe `valuesEqual` machinery struct-field equality
   already used, Phase 6.5) — as does `print` on a self-referential array
   (`getPrettyPrint`'s array case now tracks its own identity in
   `visited`, matching what struct printing already did, Phase 5.3), and
   `Pair` equality is supported at all (it previously fell through to
   "Type Mismatch" unconditionally — there was no `Pair` case anywhere in
   the comparison logic, not just a cycle-safety gap). **Fixed.**
8. Declaring a user function named identically to a builtin (e.g. `fn
   len(...)`) — confirm it's simply unreachable dead code, not an error,
   and not actually callable (Phase 6.13). **Expected, confirmed correct
   — this should indeed be unreachable, not a bug.**
9. `private` alone (no `locked`) does **not** stop `S{...}` construction
   from outside the struct's `impl` block — only `locked` does (Phase
   8.2's `checkStructLock` is a wholly separate gate from field
   privacy). **As expected.**
10. A free function called from inside one of struct `S`'s methods,
    which itself then calls back into another method of `S`, still has
    `S`-level private-field access at that inner call site (the impl-
    context stack survives unchanged through intervening free-function
    frames — Phase 8.2's precise transitivity rule). **As expected.**
11. A recursive call inside an imported file's `for` loop, method body,
    `print` statement, or any other previously-un-rewritten shape now
    correctly resolves to the qualified name (Phase 10.2's `qualifyBody`/
    `qualifyExpr` — now a full, exhaustive traversal of every `Cmd`/
    `Expr`/`BoolExpr`/`Pattern` constructor, replacing an earlier revision
    that only descended into `Seq`/`If`/`While`/`Return`/`Assign`/
    `BinaryOp`). **Fixed.**
12. A `for` loop whose body `push`es onto the very array it's iterating
    sees the newly-pushed elements on later iterations (live length
    re-check, not a snapshot — Phase 7.2). **As expected.**
13. A struct default-field expression that references an outer mutable
    variable produces a different value on two separate constructions
    of the same struct type, if that outer variable changed between them
    (Phase 6.9/7.1 — defaults are evaluated per-call, not once at
    struct-definition time). **As expected.**
14. A `return` inside a `try` block still returns from the enclosing
    function — it is not caught or reported as an error by any of the
    `try`'s `catch` clauses, even a `catch Error as e` umbrella (Phase
    7.2/7.3's exception-type-separation requirement). Same for
    `break`/`continue` inside a `try` that's itself inside a loop.
15. `throw ValueError("msg")` inside a `try { ... } catch ValueError as e
    { ... }` is caught, with `e` holding exactly `"msg"`; the same
    `throw` inside a `try` with only `catch KeyError as e { ... }` (no
    matching clause) is **not** caught — it keeps propagating outward as
    if the `try` weren't there — and an untyped `throw "msg"` (raises
    `Error`) is caught only by a `catch Error as e` clause, never by a
    `catch ValueError`/etc. An uncaught `throw` of either form (no
    enclosing `try`, or no matching clause anywhere up the chain) still
    crashes the program with that message, exactly like any other
    uncaught runtime error (Phase 7.2, 5.5).
16. Two `catch` clauses on the same `try`, ordered `catch KeyError as e
    {...} catch IndexError as e2 {...}`, each actually independently
    reachable — trigger a `KeyError` and confirm only the first clause
    runs; trigger an `IndexError` and confirm only the second runs
    (Phase 7.2's "first matching clause, in written order" rule — a
    naive implementation might instead check all clauses for the *best*
    match, or match the *last* one, both of which are wrong here).
17. `catch Error as e` placed *before* a more specific `catch TypeError
    as e2` on the same `try` makes the second clause unreachable dead
    code — the umbrella matches first and the specific clause never
    runs (Phase 5.5's flat-hierarchy `matches` rule: `Error` matches
    everything, with no notion of "more specific wins"). This spec does
    not require detecting/warning about this at parse time (the
    reference implementation doesn't either) — just confirm your
    matching logic actually produces this behavior rather than some
    "most specific match wins" logic you might be tempted to add.
18. `catch Nonsense as e { ... }` (an error-type name outside the fixed
    six-name set) is a parse error, not a runtime failure to match
    anything — validate the type name eagerly, at parse time (Phase
    4.7).
19. Tokenizing `Stack<T>` with **zero surrounding whitespace** produces
    four separate tokens (`Variable(Stack)`, `Lt`, `Variable(T)`, `Gt`),
    not one swallowed identifier — this is the identifier/keyword
    word-boundary trap called out in Phase 2, step 5's addendum; it's
    easy to pass every other test and still have this one silently
    broken, since ordinary spaced-out comparisons never trigger it.
20. Inside `struct Stack<T> { items: T[] := [] } impl Stack<T> { fn
    push(self: Stack<T>, v: T) -> Void {...} }`, calling
    `Stack<Int>.new()` then `.push(1)` succeeds, but `.push("two")` on
    that *same* instance throws a `TypeError` — this is the whole point
    of reified generics (Phase 5.6): a value flowing into a `T`-typed
    slot is checked against *that instance's own bound type*, not waved
    through. Also confirm a second, independently-constructed
    `Stack<Str>` instance still happily accepts strings at the same
    time — one instance's binding must never leak into or override
    another's.
21. `Stack<Int>{items: []}` and `Stack<Int>.new()` (type arguments at a
    construction/static-call site) are **required**, not rejected: a
    bare `Stack{items: []}` or `Stack.new()` for a generic struct is a
    `TypeError` unless written *inside that struct's own `impl` block*
    with an ambient binding already on the type-binding stack (item 22).
    A **non**-generic struct is entirely unaffected either way — `Point
    {x: 1, y: 2}` never accepts or requires `<...>`.
22. `fn static new() -> Stack<T> { return Stack{}; }` — a bare,
    unparameterized literal — correctly inherits the caller's chosen
    type: `Stack<Int>.new()` produces an `Int`-bound stack, and
    `Stack<Str>.new()` (a *separate* call) produces a `Str`-bound one,
    from that one identical line of code (Phase 5.6's ambient-binding
    inheritance). Confirm the **explicit** spelling of the same thing —
    `return Stack<T>{};`, referencing the method's own type parameter by
    name — produces an *identical* binding, not a broken one: this is
    exactly the case that's easy to get wrong, because a naive
    implementation will parse `<T>` into the placeholder `Param("T")`
    and bind the new instance to that unresolved placeholder literally,
    instead of resolving it through the current ambient binding first.
    A `Stack` instance bound to a dangling `Param("T")` instead of a
    real concrete type fails every subsequent `checkType` against it
    (Phase 5.3's "unbound type parameter" throw) — including checks that
    should have trivially succeeded, like pushing a plain `Int` onto
    what was supposed to be a `Stack<Int>`. Test both spellings and
    diff their resulting bindings, not just their pass/fail outcome.
23. A generic struct's static method called with *no* type arguments at
    all (`Stack.new()` for a generic `Stack` — there's no receiver to
    inherit a binding from the way a bare struct literal can) is a
    `TypeError`, distinct from item 21's literal case
    only in the wording of the message, not the underlying rule: a
    generic struct's static call site always needs its own explicit
    `<...>`.

Each item above should have a corresponding automated test in your test
suite before calling the implementation complete.
