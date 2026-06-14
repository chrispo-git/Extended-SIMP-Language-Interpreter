# Built-in Functions
Each of these functions is available by default in SIMP+.

## General
| Function | Signature | Description |
|----------|-----------|-------------|
| `assert` | `assert(Bool, <Str>) -> Bool` | If false, throws an error with optional message |
| `deepCopy` | `deepCopy(x) -> x` | Returns a deep copy of a value |
| `console` | `console(Str) -> Str` | Runs a console command |

---

## I/O
| Function | Signature | Description |
|----------|-----------|-------------|
| `input` | `input(<Str>) -> Str` | Reads a String from stdin with optional prompt |
| `inputInt` | `inputInt(<Str>) -> Int` | Reads an Int from stdin with optional prompt |
| `inputBool` | `inputBool(<Str>) -> Bool` | Reads a Bool from stdin (y/n, yes/no, 1/0, t/f, true/false) |
| `readFile` | `readFile(Str) -> Str[]` | Reads a text file and returns lines as an array |
| `writeFile` | `writeFile(Str, Str[]) -> Bool` | Writes lines to a text file, returns true on success |
| `clearScreen` | `clearScreen() -> Void` | Clears the screen |
| `hideCursor` | `hideCursor() -> Void` | Hides the cursor |
| `showCursor` | `showCursor() -> Void` | Shows the cursor |
| `moveCursor` | `moveCursor(Int, Int) -> Void` | Moves the terminal cursor to a position (row, col) |
| `sleep` | `sleep(Int) -> Void` | Pauses execution for x milliseconds |
| `readKey`| `readKey() -> Str` | Reads the key currently being pressed |

---

## String Manipulation
| Function | Signature | Description |
|----------|-----------|-------------|
| `len` | `len(Str) -> Int` | Gets the length of a String |
| `upper` | `upper(Str) -> Str` | Makes a String uppercase |
| `lower` | `lower(Str) -> Str` | Makes a String lowercase |
| `trim` | `trim(Str) -> Str` | Trims leading/trailing whitespace from a String |
| `reverse` | `reverse(Str) -> Str` | Reverses a String |
| `contains` | `contains(Str, Str) -> Bool` | Checks if a String contains a value |
| `startsWith` | `startsWith(Str, Str) -> Bool` | Checks if a String starts with a value |
| `endsWith` | `endsWith(Str, Str) -> Bool` | Checks if a String ends with a value |
| `replace` | `replace(Str, Str, Str) -> Str` | Replaces all occurrences of a target sequence |
| `substr` | `substr(Str, Int, Int) -> Str` | Gets a substring of the target String |
| `indexOf` | `indexOf(Str, Str) -> Int` | Gets the index of a substring, or -1 if not found |
| `split` | `split(Str, Str) -> Str[]` | Splits a string by a delimiter |
| `toArr` | `toArr(Str) -> Str[]` | Converts a string into an array of single character strings |
| `ord` | `ord(Str) -> Int` | Returns the ordinal value of a character |
| `chr` | `chr(Int) -> Str` | Returns a character from the ordinal value |

---

## Maths Functions
| Function | Signature | Description |
|----------|-----------|-------------|
| `abs` | `abs(Int \| Float) -> Int \| Float` | Absolute value of a number |
| `max` | `max(Int \| Float, Int \| Float) -> Int \| Float` | Maximum of two numbers |
| `min` | `min(Int \| Float, Int \| Float) -> Int \| Float` | Minimum of two numbers |
| `clamp` | `clamp(Int \| Float, Int \| Float, Int \| Float) -> Int \| Float` | Clamps a number between min and max values |
| `pow` | `pow(Int \| Float, Int \| Float) -> Int \| Float` | Base to the power of an exponent |
| `sqrt` | `sqrt(Int) -> Int` | Integer square root |
| `floor` | `floor(Float) -> Float` | Returns the floor of a float |
| `ceil` | `ceil(Float) -> Float` | Returns the ceiling of a float |
| `round` | `round(Float) -> Float` | Returns the nearest integer value of a float |
| `ln` | `ln(Int \| Float) -> Float` | Natural logarithm (base e) |
| `log10` | `log10(Int \| Float) -> Float` | Logarithm base 10 |
| `log` | `log(Int \| Float, Int \| Float) -> Float` | Logarithm of the 1st argument with the 2nd as the base |
| `sin` | `sin(Float) -> Float` | Sine of a float (radians) |
| `cos` | `cos(Float) -> Float` | Cosine of a float (radians) |
| `tan` | `tan(Float) -> Float` | Tangent of a float (radians) |
| `asin` | `asin(Float) -> Float` | Arcsine of a float |
| `acos` | `acos(Float) -> Float` | Arccosine of a float |
| `atan` | `atan(Float) -> Float` | Arctangent of a float |
| `pi` | `pi() -> Float` | Returns the value of π |
| `e` | `e() -> Float` | Returns the value of Euler's Number |
| `random` | `random(Int, Int) -> Int` | Returns a random integer within the range min (inclusive) to max (exclusive) |

---

## Type Casting and Type Checking
| Function | Signature | Description |
|----------|-----------|-------------|
| `isInt` | `isInt(x) -> Bool` | Returns true if the argument is an Int |
| `isStr` | `isStr(x) -> Bool` | Returns true if the argument is a String |
| `isBool` | `isBool(x) -> Bool` | Returns true if the argument is a Bool |
| `isFloat` | `isFloat(x) -> Bool` | Returns true if the argument is a Float |
| `isNull` | `isNull(x) -> Bool` | Returns true if the argument is Null |
| `typeOf` | `typeOf(x) -> Str` | Returns the type of a variable as a string |
| `toStr` | `toStr(x) -> Str` | Converts a value into a String |
| `toFloat` | `toFloat(Int) -> Float` | Converts an Int into a Float |
| `toInt` | `toInt(Str) -> Int` | Converts a String into an Int |
| `toBool` | `toBool(Str) -> Bool` | Converts a String into a Bool |
| `toBinary` | `toBinary(Int) -> Str` | Returns the binary representation of an integer |

---

## Array Manipulation
| Function | Signature | Description |
|----------|-----------|-------------|
| `len` | `len(T[]) -> Int` | Gets the length of an array |
| `reverse` | `reverse(T[]) -> T[]` | Reverses an array |
| `contains` | `contains(T[], x) -> Bool` | Checks if an array contains a value |
| `slice` | `slice(T[], Int, Int) -> T[]` | Gets a subset of the target array |
| `push` | `push(T[], T) -> T[]` | Appends a value to the end of an array |
| `flatten` | `flatten(T[][]) -> T[]` | Flattens a nested array one level deep |
| `sum` | `sum(Int[] \| Float[]) -> Int \| Float` | Sums all values in an array |
| `range` | `range(Int, <Int>, <Int>) -> Int[]` | Returns an array of integers. 0 to end, start to end, or start to end with step |
| `zip` | `zip(A[], B[]) -> (A, B)[]` | Zips two arrays into an array of pairs |

---

## Maps
There are a small set of special functions that allow you to instantiate and manipulate a Map object.

| Function | Signature | Description |
|----------|-----------|-------------|
| `newMap` | `newMap(T1, T2) -> Map(T1, T2)` | Instantiates a new map of keys type T1 to values T2 |
| `get` | `get(Map(K, V), K) -> V` | Gets a value from a map by key |
| `set` | `set(Map(K, V), K, V) -> Void` | Sets a value in a map |
| `hasKey` | `hasKey(Map(K, V), K) -> Bool` | Returns true if the map contains the given key |
| `remove` | `remove(Map(K, V), K) -> Void` | Removes a key from a map |
| `keys` | `keys(Map(K, V)) -> K[]` | Returns an array of all keys in the map |

