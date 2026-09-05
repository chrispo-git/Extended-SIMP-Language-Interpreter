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
2. **Comments**: `//` skips to end of line. `/* ... */` — a single
   boolean "in comment" flag toggled on `/*`, cleared on the next `*/`;
   **do not implement nesting** — a `/* /* */ */` must end at the first
   `*/`, replicating the reference implementation's non-nesting behavior
   exactly (§ see Phase-12 conformance item: "block comments don't nest").
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
   locked import as const null match case print skip return true false
   Int Str Bool Float Void Map`), check "does the identifier-scan-from-
   here exactly equal this word" (whole-word match: the character right
   after the word must not itself be a letter/digit/`_`, else it's a
   longer identifier, e.g. `structs` ≠ `struct`+`s`). Order doesn't matter
   between keywords (they're mutually exclusive by exact string), but
   **all keyword checks must run before the generic identifier-fallback
   rule** (`[A-Za-z_][A-Za-z0-9_]*` → `Variable(name)`), which is your
   last-resort case.
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
     struct (per 4.2's registry) is immediately followed by `{`.
   - `StructName.` — only when a `Variable` naming a known struct is
     immediately followed by `.` (**not** `{`) — this produces the
     "bare type name" marker expression that static-method-call syntax
     needs (postfix parsing, 4.3.4, then attaches `.methodName(...)` on
     top of it exactly like it would for an instance).
     **Order this check and the previous one so struct-literal `{`
     lookahead is tried first, dot-marker second** — both fire on
     `Variable(name)` where `name` is a known struct, differing only in
     the very next token (`{` vs `.`), so they don't actually conflict,
     but keep them as two distinct guarded cases rather than one,
     for clarity.
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
7. **`parseExpr`** (the "top" of the plain-expression grammar — this is
   what call-arguments, array-literal elements, struct-literal field
   values, index expressions, etc. all call): `parseAddSub()`, then a
   left-associative loop consuming `& | ^ << >> >>>` (bitwise — note:
   **looser than `+ -`**, not tighter as in C-like languages), each time
   parsing another `parseAddSub()` on the right; **then, once that loop
   is done, check once (not in a loop) for a trailing comparison operator
   `> < >= <= == !=`** — if present, parse the right-hand side by calling
   **`parseExpr()` again** (not `parseAddSub`), producing a
   right-recursive (not left-associative, not "flat chain") comparison
   node wrapped as a boolean-lifted expression. This means a chained
   comparison `a < b < c` parses as `a < (b < c)` — replicate this
   exactly, don't "fix" it into a flat left-to-right chain, since that
   would silently accept/evaluate differently on any real SIMP+ program
   containing a chained comparison (which will type-error at *runtime*
   either way, but a VM claiming compatibility should error in the same
   shape).

This completes the plain `Expr` grammar. **`&&`/`||` are deliberately
*not* part of it** — see 4.5.

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

### 4.5 Boolean-expression parser (the second, parallel grammar)
Two entry points, both used only from specific higher-level call sites
(assignment RHS, `return` value, `const` initializer, and `if`/`while`
conditions — see 4.7):
- **`parseBoolExpr`** (used for assignment RHS / return / const — this is
  the one that lets `&&`/`||` appear at these specific "top level"
  positions, and *nowhere else* in the grammar): `parseExpr()`, then a
  left-associative loop consuming `&& ||`, each time parsing another
  `parseExpr()` and wrapping both sides through a helper that turns a
  plain `Expr` into a `BoolExpr` (if it's already a `BoolLift`, unwrap it;
  if it's a literal `Bool`, turn it into `BoolExpr.Literal`; otherwise
  wrap it as `BoolExpr.FromExpr`) before combining with `And`/`Or`, then
  re-wraps the final combined `BoolExpr` back into an `Expr` via
  `BoolLift` so it can be used anywhere an `Expr` is expected.
- **`parseBool`** (used only for `if`/`while` conditions, and internally
  by `¬`'s operand): a separate atomic-boolean parser
  (`parseAtomicBool`) handling literal `true`/`false` (optionally
  followed by `==`/`!=` against another expression), `¬` (recurse into
  `parseBool`), a parenthesized `(B)`, or a plain expression position
  (deref/literal/variable-call/variable-index/variable-dot) optionally
  followed by a comparison operator — then the same left-associative
  `&&`/`||` loop as above, but combining `BoolExpr`s directly (no
  `Expr`-wrapping needed since we're already in `BoolExpr` land here).

**Why these two, and why only now:** `parseExpr` (4.3.7) must exist first
since both entry points call it. Every place in the `Cmd` grammar that
accepts "a boolean-ish thing" needs to pick the *correct* one of these
two entry points — get this wrong and you'll accidentally make `&&`/`||`
legal in more places than the reference implementation allows, which is a
real compatibility divergence, not a harmless permissiveness (see Phase
12's conformance list).

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
- `struct` → advance, a name, then `{ field, ... }` where each field is
  an optional `private` marker, a name, `:`, a type, and an optional
  `= <Expr>` default, comma-separated, until `}` (a struct with zero
  fields — `{}` — is valid).
- `locked struct` → same as `struct` but with a lock flag set; require
  the `struct` keyword to immediately follow `locked`.
- `import "path"` optionally followed by `as alias` (default alias, if
  omitted: the path's filename with its directory stripped and
  **everything from the first `.` onward** stripped too — i.e. splitting
  on `.` and keeping only the first segment, so `a.b.simp` aliases to
  `a`, not `a.b` — replicate this exactly, it is almost certainly not
  what a user would expect from a file named `a.b.simp`, but is the
  reference behavior).
- `impl StructName { ... }` → repeatedly parse declarations via the `fn`
  rule above **only** (anything else inside an `impl` block, including a
  nested `struct`/`impl`/`import`, is a parse error) until `}`.

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
- `load(name)`: search this scope, then walk up parents; throw "unbound"
  if never found in the whole chain.
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
- **`isNullable(t) -> bool`**: `false` only for `Int`/`Str`/`Bool`; **note
  `Float` is *not* in this exclusion list** (so `null` currently type-
  checks successfully against a `Float`-typed slot, unlike the other
  three primitives — decide whether to replicate this apparent
  inconsistency or fix it, and document your choice; see Phase 12).
  Every other type is nullable.
- **`checkType(value, expected, name) -> void|throw`**: the single
  dynamic type-check gate used everywhere a value flows into a typed
  slot. If `getType(value) == Null`, pass iff `isNullable(expected)`.
  Else if `value` is an *empty* `Arr`, pass iff `expected` is *any*
  `Arr(_)` (**inner element type is never checked for an empty array** —
  a genuine hole: an empty `Int[]` satisfies a `Str[]`-typed slot with no
  error). Else, pass iff `getType(value) == expected` **exactly**
  (recursive structural equality on the whole type descriptor) — no
  numeric widening (`Int` never satisfies `Float` or vice versa at this
  gate — widening only ever happens via arithmetic *producing* a new
  Float value, never via a bare type check), no struct subtyping.
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
  `Type(t) -> "Type.$t"`; `Pair(f,s) -> "($f, $s)"` recursively;
  `Arr(elems) -> "[$e0, $e1, ...]"` recursively, `"[]"` if empty;
  `Struct(typeName, fields) -> "$typeName { $f0: $v0, ... }"` — mask any
  field the struct's declaration marks `private` as the literal text
  `"???"` **unconditionally** (no caller-context check here, unlike
  actual field-access privacy in Phase 8 — printing always masks private
  fields regardless of who's printing), and be **cycle-safe**: track a
  `visited` set of field-maps already entered (by identity) higher up the
  same print call, printing `"$typeName { ... }"` (literal three dots) if
  re-entered, rather than recursing forever.

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
   - `Arr,Arr`: `==`/`!=` only, via whole-array structural/elementwise
     equality (**no cycle protection** — a genuinely cyclic array
     structure would infinite-loop/stack-overflow here; decide whether to
     accept that risk for parity or add protection, see Phase 12).
   - `Struct,Struct` **same type name**: `> >= < <=` dispatch to that
     struct's `_gt`/`_gte`/`_lt`/`_lte` method unconditionally (throw if
     missing — no fallback for ordering operators); `==`/`!=` dispatch to
     `_eq`/`_neq` **only if the struct actually defines one**, else fall
     back to a deep, cycle-safe structural field-by-field comparison
     (recursively comparing every field's value, **including private
     ones** — this structural fallback does not check field privacy at
     all) using an identity-hash-pair `visited` set to stay cycle-safe
     (unlike plain array equality above).
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
target throw (you cannot default-construct a `Void` or a reference).

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
`callStaticMethod(...)` (static path) both: look up `(typeName,
methodName)` in the method table (throw "no method found" if absent,
**before** checking anything else); then check the found method's
static/instance flag matches the path being used (throw a specific
"static method called on instance" / "instance method called
statically" error if not); then run `checkMethodPrivacy`; then push
`typeName` onto the impl-context stack; then run the 7.4 "from-Value-
arguments" calling convention against the method's declared params and
the given `argValues` (for the instance path, `argValues` already has the
receiver struct value prepended as the first entry — by whichever param
name the method declares first, conventionally but not structurally
`self`; for the static path, no such prepending happens); then pop the
stack (even on throw).

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
- Only `FnDecl`/`StructDecl`/`ImportDecl` top-level items are legal
  inside an imported file; anything else (a bare statement, or —
  concretely — an `impl` block) is an error. **This means structs with
  methods currently cannot be imported and keep their methods** in the
  reference implementation (only their bare field layout transfers) —
  decide up front whether your implementation will faithfully reproduce
  this limitation or fix it by also handling `PImpl` entries inside
  imports (a strictly more useful behavior, and a good candidate
  deviation to make deliberately and document, rather than replicate).
- Re-register every top-level function/struct from the imported file
  into the **importer's** shared `FunctionEnv`/`StructEnv`, under the
  qualified name `"$alias::$name"`.
- Rewrite recursive/self-referential calls inside each re-registered
  function's body so they call the qualified name too — **the reference
  implementation's rewrite pass is narrow**: it only descends into
  `Seq`/`If`/`While`/`Return`/`Assign` commands and `BinaryOp` expressions
  looking for `FnCall`s to requalify; it does **not** descend into `For`
  loops, `Print`, field/array-assignment commands, method calls, struct
  literals, match expressions, block expressions, or into a call's own
  argument expressions. A recursive call made from inside any of those
  un-rewritten shapes, in an imported file, will resolve against the
  wrong (unqualified, global) name at the call site — silently calling an
  unrelated function if one happens to exist under that bare name in the
  importer's own program, or throwing "not found" otherwise. Pick one,
  deliberately: (a) replicate this narrow rewrite exactly, for behavioral
  parity with any existing multi-file SIMP+ programs that happen to avoid
  triggering it, or (b) implement a **full** recursive rewrite over every
  `Expr`/`Cmd` shape (strictly more correct, but a genuine, documented
  behavior change from the reference implementation).
- `namespace::name(...)` / `namespace::StructName{...}` call/construction
  syntax (Phase 4.3.2) resolves directly against these qualified keys in
  the shared `FunctionEnv`/`StructEnv` — there's no nested/multi-level
  namespacing (`a::b::c` isn't meaningful; exactly one `::` is
  recognized).

**Done when:** a small multi-file example (one file importing another,
with at least one recursive call and one non-recursive call across the
boundary) produces correct output.

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
2. `&&`/`||` are rejected by the parser inside a function-call argument,
   array-literal element, or struct-literal field value unless wrapped
   in a block expression (Phase 4.5) — verify your parser actually
   rejects these positions, don't accidentally make the grammar more
   permissive than the reference.
3. `/* /* */ */` — the second `*/` is dangling/unconsumed input (block
   comments don't nest, Phase 2 step 2).
4. `x - 1` lexes as subtraction; `[-1, 2]`, `f(-1)` lex the `-1` as one
   negative literal token (Phase 2 step 4).
5. An empty `Int[]`-declared array satisfies a `Str[]`-typed slot with no
   error (Phase 5.3's `checkType` hole) — decide and document if you're
   keeping or closing this hole.
6. `f: Float := null` — decide and document whether `Float` rejects
   `null` like `Int`/`Str`/`Bool` do, or accepts it like every other
   type (the reference accepts it — Phase 5.3's `isNullable`).
7. Top-level `Arr == Arr`/`Pair == Pair` on a value containing a cycle —
   decide and document whether this is cycle-safe (it isn't, in the
   reference) versus struct-field equality and struct printing, which
   *are* cycle-safe (Phase 6.5, Phase 5.3's `getPrettyPrint`).
8. `break`/`continue`/`return` used outside any loop/function — decide
   and document whether this is a clean parse/compile-time error (an
   improvement) or an unhandled-crash (matching the reference, Phase
   7.3).
9. Declaring a user function named identically to a builtin (e.g. `fn
   len(...)`) — verify it's simply unreachable dead code, not an error,
   and not actually callable (Phase 6.13).
10. `private` alone (no `locked`) does **not** stop `S{...}` construction
    from outside the struct's `impl` block — only `locked` does (Phase
    8.2's `checkStructLock` is a wholly separate gate from field
    privacy).
11. A free function called from inside one of struct `S`'s methods,
    which itself then calls back into another method of `S`, still has
    `S`-level private-field access at that inner call site (the impl-
    context stack survives unchanged through intervening free-function
    frames — Phase 8.2's precise transitivity rule).
12. A recursive call inside an imported file's `for` loop or method body
    resolves against the wrong (unqualified) name (or your deliberately-
    chosen fix for this — Phase 10.2's narrow-rewrite limitation).
13. A `for` loop whose body `push`es onto the very array it's iterating
    sees the newly-pushed elements on later iterations (live length
    re-check, not a snapshot — Phase 7.2).
14. A struct default-field expression that references an outer mutable
    variable produces a different value on two separate constructions
    of the same struct type, if that outer variable changed between them
    (Phase 6.9/7.1 — defaults are evaluated per-call, not once at
    struct-definition time).

Each item above should have a corresponding automated test in your test
suite before calling the implementation complete.
