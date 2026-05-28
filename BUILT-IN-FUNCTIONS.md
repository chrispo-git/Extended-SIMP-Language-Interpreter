# Built-in Functions
Each of these functions is available by default in SIMP.

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
| `substr` | `substr(Str, Int, Int) -> Str` | Gets a subString of the target String |
| `indexOf` | `indexOf(Str, Str) -> Int` | Gets the index of a subString, or -1 if not found |
| `isInt` | `isInt(x) -> Bool` | Returns true if the argument is an Int |
| `isStr` | `isStr(x) -> Bool` | Returns true if the argument is a String |
| `isBool` | `isBool(x) -> Bool` | Returns true if the argument is a Bool |
| `toStr` | `toStr(Bool \| Int) -> Str` | Converts a Bool or Int into a String |
| `toInt` | `toInt(Str) -> Int` | Converts a String into an Int |
| `toBool` | `toBool(Str) -> Bool` | Converts a String into a Bool |
| `abs` | `abs(Int) -> Int` | Absolute value of an Int |
| `max` | `max(Int, Int) -> Int` | Maximum of two Ints |
| `min` | `min(Int, Int) -> Int` | Minimum of two Ints |
| `clamp` | `clamp(Int, Int, Int) -> Int` | Clamps an Int between min and max values |
| `pow` | `pow(Int, Int) -> Int` | Integer power |
| `assert` | `assert(Bool, [Str]) -> Bool` | If false, throws an error with optional message |