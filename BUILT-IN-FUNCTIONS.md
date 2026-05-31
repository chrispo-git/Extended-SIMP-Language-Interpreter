# Built-in Functions
Each of these functions is available by default in SIMP.

| Function | Signature | Description |
|----------|-----------|-------------|
| `len` | `len(Str \| Arr) -> Int` | Gets the length of a String / Array |
| `upper` | `upper(Str) -> Str` | Makes a String uppercase |
| `lower` | `lower(Str) -> Str` | Makes a String lowercase |
| `trim` | `trim(Str) -> Str` | Trims leading/trailing whitespace from a String |
| `reverse` | `reverse(Str \| Arr) -> Str \| Arr` | Reverses a String / Array |
| `contains` | `contains(Str\| Arr, Str\| x) -> Bool` | Checks if a String / Array contains a value |
| `startsWith` | `startsWith(Str, Str) -> Bool` | Checks if a String starts with a value |
| `endsWith` | `endsWith(Str, Str) -> Bool` | Checks if a String ends with a value |
| `replace` | `replace(Str, Str, Str) -> Str` | Replaces all occurrences of a target sequence |
| `substr` | `substr(Str, Int, Int) -> Str` | Gets a subString of the target String |
| `slice` | `substr(Arr, Int, Int) -> Arr` | Gets a subset of the target Array |
| `indexOf` | `indexOf(Str, Str) -> Int` | Gets the index of a subString, or -1 if not found |
| `isInt` | `isInt(x) -> Bool` | Returns true if the argument is an Int |
| `isStr` | `isStr(x) -> Bool` | Returns true if the argument is a String |
| `isBool` | `isBool(x) -> Bool` | Returns true if the argument is a Bool |
| `toStr` | `toStr(Bool \| Int) -> Str` | Converts a Bool or Int into a String |
| `toInt` | `toInt(Str) -> Int` | Converts a String into an Int |
| `toBool` | `toBool(Str) -> Bool` | Converts a String into a Bool |
| `toArr` | `toArr(Str) -> Str[]` | Converts a string into an array of single character strings |
| `abs` | `abs(Int) -> Int` | Absolute value of an Int |
| `max` | `max(Int, Int) -> Int` | Maximum of two Ints |
| `min` | `min(Int, Int) -> Int` | Minimum of two Ints |
| `clamp` | `clamp(Int, Int, Int) -> Int` | Clamps an Int between min and max values |
| `pow` | `pow(Int, Int) -> Int` | Integer power |
| `assert` | `assert(Bool, <Str>) -> Bool` | If false, throws an error with optional message |
| `input` | `input(<Str>) -> Str` | Allows the user to input a String |
| `inputInt` | `inputInt(<Str>) -> Int` | Allows the user to input an Int |
| `inputBool` | `inputBool(<Str>) -> Bool` | Allows the user to input a Bool (Done in the form y/n, yes/no, 1/0, t/f, or true/false) |
| `range` | `input(Int, <Int>, <Int>) -> Arr` | Returns an array of the range, 0-end, start-end, or start-end with a step value|
| `readFile` | `readFile(Str) -> Arr` | Reads text file |
| `writeFile` | `writeFile(Str, Arr) -> Bool` | Writes to text file, returns true if it succeeds, false if it fails |
| `typeOf` | `typeOf(Var) -> Str` | returns the type of a variable as a string
| `deepCopy`| `deepCopy(Var) -> Var`| deep copies a variable
| `push` | `push(T[], T) -> T[]` | Appends a value to the end of an array and returns it |