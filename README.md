## What is SMFL?

Striker's Minimal Functional Language (SMFL) is a minimal functional scripting language based off of canonical structured data files such as TOML, JSON, or YAML. It is written in Java and can interact with and use functions imported from Java. Functions can also be defined and called within data files, along with data objects that are used by functions.

TOML will be used for all SMFL examples in this README, but SMFL can read from any file with a similar "maps in maps" style of hierarchy. 

SMFL is not intended to be used as a standalone language, but instead as a supplement to Java. By itself, it only has basic arithmetic and flow abilities and no mechanism for input or output. It is instead designed to be used as snippets employed by a larger program that can be modified *without* recompilation, allowing for quick iteration or even on-the-fly code generation.

Most importantly, this README is more of a concept layout than an actual description of SMFL's current functionality. As of `v0.6.1` on July 24th, 2026, the code has many bugs and is still incomplete, although it does have some basic functionality. Additionally, SMFL is largely just a project to push my coding skills to their limits and prove to myself (and the computer science department at my college) that I am ready to move on to more advance topics.

## SMFL Objects

Data objects are the most straight-forward element of SMFL. On compilation, all values from key-value pairs are converted into a `ScriptObject` corresponding to their type. Integers and floats are converted to `ScriptNumber`, booleans are converted to `ScriptBoolean`, and lists are converted into `ScriptArray`. These are the "simple" objects that behave exactly as you would expect they would. The only slight wrinkle is that each element within a list will also be converted to a `ScriptObject` accordingly. Lists with different types of elements are supported.

```toml
[data.ex1]
num1 = 0.5                    # ScriptNumber(0.5)
num2 = 7                      # ScriptNumber(7.0)
bool = true                   # ScriptBoolean(true)
array = [1, [true, false]]    # ScriptArray(ScriptNumber(1), ScriptArray(ScriptBoolean(true), ScriptBoolean(false)))
```

### Structures

Structures are SMFL's equivalent of maps. As their name implies, they make up the entire structure of code in SMFL. While they can simply act as a map of other data types (or other structures), they also are used both for function calls and function definition, which will be described in more detail in a later section.

### Strings

Two types are notably omitted from the "simple" category: Strings and Maps. Both are the driving forces behind what elevates SMFL from a file reader to an actual scripting language. Maps will be covered in a later section.

Strings serve three main purposes: they can store text, but they can also reference other pieces of data and perform calculations.

#### Text Strings

Text strings do what their name implies: they store text. The only real difference from strings in a normal file is that some characters are considered special characters and thus must be escaped like `\\$`.

> Special characters are: `$`, `@`, `{`, `}`, `+`, `-`, `*`, `/`, `%`, `=`, `!`, `<`, `>`, `&`, `|`

#### Formatted Strings

Formatted strings, or fstrings, are a variation on text strings that allow for the insertion of references to data elsewhere in the file. If any section of a string is enclosed with unescaped curly braces, the section within will be treated as a separate string of any type. Its value will be converted back into a string and inserted into the original string.

```toml
[data.ex2]
num = 3
array = [1, 2, 3]
string = "Num is {$num}. Array is {$array}." # ScriptString("Num is 3. Array is [1, 2, 3]")
```

Formatted strings can be put within formatted strings. If the value of an insert to a formatted sting changes, the string will also change accordingly.

#### References

References allow different sections of code to share data. They are also the mechanism by which functions are used. A reference can be thought of as creating a local pointer to a certain object. If that object changes, the reference will also change. 

References are created with strings in a certain pattern, similar to an address in a file system. A prefix (`$` or `@`) starts a reference. 

- `$` will be used in most cases. It indicates that the reference is read-only, meaning if `b` is a reference to `a`, when `a` changes it will cause `b` to change, but changing `b` will not affect `a`.
- `@` starts a write-only reference. This is not a reference to a value, but instead a mutator for that value. The `@` prefix is most commonly used with the `link` command.

After the prefix is a chain of directions to the value that is being referenced. This is best explained through example:

```toml
[data.ex3]
value = 3
ref = "$value"
```

As `ref` and `value` have the same scope, `ref` can reference `value` directly.

```toml
[data.ex4]
a = { b = { c = 3 } }
d = { e = { f = "$a.b.c" } }
```

As `c` and `f`'s scope are separated, `f` must start the reference from the lowest shared scope, in this case `data.ex4`, and then move down scopes until it reaches `c`'s scope.

Items in arrays can also be referenced by their indexes. For example, `$array.1` would reference the first item of the array. If an item was inserted at position 1, the reference would not switch to referencing that item, it would maintain its original reference even as the item referenced moves to index 2.

### Operation Strings

In addition to everything else they can do, strings can also be used for simple arithmetic, comparison, and boolean logic along with other simple operations. In-string operations will only operate on references (for example, `$a + $b` is valid but `$a + 2` is not). Order of operations and parentheticals are handled as expected.

> #### `$a + $b`
> 
> - If `a` and `b` are both numbers, sums them.
> - If `a` and `b` are both strings, both arrays, or both structures, concats them.
> - If `a` is an array returns a new array that is a copy of `a` with `b` appended to the end.
> - If `b` is an array returns a new array that is a copy of `b` with `a` appended to the front.
> 
> #### `$a - $b`
> 
> - If `a` and `b` are both numbers, subtracts `b` from `a`.
> - If `a` and `b` are both strings, removes all substrings from `a` that match `b`.
> - If `a` is an array and `b` is a number, returns a copy of `a` with the item at index `b` removed.
> - If `a` is a structure and `b` is a string, returns a copy of `a` with the key `b` removed.
> 
> #### `$a * $b`
> 
> - If `a` and `b` are both numbers, multiplies them.
> - If `a` is a string or array and `b` is a number, returns `a` repeated `b` times (rounded down if `b` is not an integer, 0 if `b` is negative).
>
> #### `$a / $b`
> 
> - If `a` and `b` are both numbers, divides `a` by `b`.
> 
> #### `$a // $b`
> 
> - If `a` and `b` are both numbers, converts both to integers and divides `a` by `b` using integer division.
> 
> #### `$a % $b`
> 
> - If `a` and `b` are both numbers, returns `a` modulus `b`.
> 
> #### `$a ** $b`
> 
> - If `a` and `b` are both numbers, raises `a` to the power of `b`.
> 
> #### `$a == $b`
> 
>  Tests if `a` is equal to `b`.
> 
> #### `$a != $b`
> 
> Tests if `a` is not equal to `b`
> 
> #### `$a > $b`
> 
> Tests if `a` is "greater" than `b` using the comparison number system.
>
> #### `$a < $b`
>
> Tests if `a` is "less" than `b` using the comparison number system.
>
> #### `$a >= $b`
>
> Tests if `a` is "greater" than or equal to `b` using the comparison number system.
>
> #### `$a <= $b`
>
> Tests if `a` is "less" than or equal to `b` using the comparison number system.
> 
> #### `$a && $b`
> 
> - If `a` and `b` are both booleans, ands them.
> 
> #### `$a || $b`
> 
> - If `a` and `b` are both booleans, ors them.
> 
> #### `!$a`
> 
> - If `a` is a boolean, returns its inverse.

### Comparison Numbers

All data objects in SMFL are assigned a comparison number to allow for easy comparison.

- For numbers, the comparison number is just equal to the number itself.
- For booleans, the comparison number is 1 if true, 0 if false.
- For strings, if `l` is the length of the string, `c` is the numerical value of the `i`th character and `n` is the comparison number, `n = l + 0.01 * Σ ((l - i) * c)`.
- Arrays follow a similar formula to string, with `c` equal to the comparison number of the `i`th item in the array.
- For structures, if `l` is the number of items in the structure, `c` is the comparison number of the `i`th item,and `n` is the comparison number, `n = l + 0.01 * Σ (c)`.

## Functions

Functions are, of course, the most important element of any functional programming language. Functions can be referenced just like any data type. This fact is key both to allowing functions to be called and allowing for the functional mainstay of passing functions as parameters for other functions. 

Functions can be defined in-file, but they can also be imported from other SMFL files and from Java. The ability to import Java functions is crucial as it allows the two languages to interact and lends SMFL lots of missing functionality such as input, output, and file manipulation. SMFL does have a small set of core functions that do not need to be imported, but these are largely the bare minimum for flow control and processing.

### Function Calls

A function – be it a core function, an imported function, or a function defined in-file – is called through a specific structure that includes a `$` reference to the function being called and a map or array with arguments. It will loom something like:

```toml
[data.ex5]
function_call_resullt = {
    run.func = "$range",
    run.args = {
        start = 10,
        end = 0,
        step = -1
    }
}
```

The `run` structure is what triggers the function call. Without that keyword, this would merely act as a data structure. When the run structure is detected, it will search for a `func` child. If one is not found, an error will be thrown.

Arguments can be passed through a map or omitted entirely. All functions have default values for each of their arguments. Any arguments passed will override the corresponding default. If no arguments are passed, the function will be called with default parameters. 

It is important to note that just like how references update when the data they point to updates, the output of a function call will change if any items its `args` references or its `func` changes.

### Function Definition

Function definition, just like function calling, is done through a structure. A function definition will look something like:

```toml
[func_name] 
# The existence of a "lambda" child of any structure is what identifies it as a function definition
lambda.docs = "This is the documentation for this function."
a = 1
b = 2
c = "$a * $b"

# The value of lambda.return is the return value of the function
lambda.return = {
    run.func = "$range",
    run.args = {
        end = "$c"
    }
}
```

A function can be referenced by its name. All keys, excluding `lambda`, act as arguments that can be passed to the function. For the example above, that means that `a`, `b`, and `c` are all arguments. For example, if the arguments `a = 2, b = 4` were passed, the result would be `[0, 1, 2, 3, 4, 5, 6, 7]`. 

If there are arguments that should not be able to be passed, their keys can be added to the `lambda.private` array. If any arguments with a keyword in the `lambda.private` array are passed to a function, they will be ignored. If the line `lambda.private = ["b", "c"]` were added to the example above, passing `a = 2, b = 4` would result in an output of `[0, 1, 2, 3]` as the passed value of `b` was ignored.

It is important to note that functions are not necessarily linear. They can be seen as working backwards from the `lambda.return` value through that values dependedcies. This means that the sequence of lines in a function is largely irrelevant, as long as the dependency order is logical.

Lambda structures can also be passed directly into function call structures in place of a reference to a function.

### Core Functions

SMFL has seven core functions. These functions can be referenced directly with a `$` and their name. They cannot be mutated with the `@` prefix. Even any data or function is given a name that matches a core function's name, the core function will override all references. These core functions fall into three main categories: transmutation, flow control, and data structure manipulation.

#### Link

Link, referenced with `$link`, is the only core function that takes in an argument with a `@` prefix. It transmutes a function by creating a copy of that function that will set a certain variable to the output of the function every time it is called. In java it would look something like:

```java
Function<?, ?> link(Consumer<?> target, Function<?, ?> func) {
    return (obj) -> {
        var out = func.apply(obj);
        target.accept(out);
        return out;
    };
}
```

`target` is a setter, playing the same role as a write-only reference allowing an external value to be set. `func` is any function.

> **param** `target`: A write-only reference to a certain variable.
> 
> **param** `func`: The function to create a linked version of.
> 
> **return**: A linked version of `func` that updates `target` every time it is called.

#### If

If, referenced with `$if`, is the most basic form of control flow in SMFL. It takes a boolean `condition` and two function references, `then` and `else`. If the condition is true, the `then` function will be run and its output returned. If not, the `else` function will be run and its output returned.

> **param** `condition`: A boolean condition.
> 
> **param** `then`: The function to run if `condition` is true.
> 
> **param** `else`: The function to run if `condition` is false.
> 
> **return**: The output of the function that was run.

#### While

While, referenced with `$while`, runs a function `func` in a loop until `condition` returns false. The output from `func` each loop is added to an array returned once the loop ends.

> **param** `condition`: A function that returns a boolean. It is called with default parameters every cycle until it returns false, ending the loop.
> 
> **param** `func`: The function to run every cycle of the loop.
> 
> **return**: An array containing the output of `func` from each cycle of the loop.

#### Foreach

Foreach, referenced with `$foreach`, loops through an array or map and applies a function to each index. Foreach over an array is simplest: the `func` is run for each index in the `iterable`, with the value at each index passed to the `func` with the keyword `val_kw` as the only passed arg. The result from each run of the `func` is saved to an array which is returned.

If a structure/map is the `iterable`, there are two variations of foreach. If `val_kw` is passed but `key_kw` is omitted from the args, foreach loops through the `iterable` as if it is an array, with the value at each index being a map in the format `{ key = "key", value = "value }`.

If both `val_kw` and `key_kw` the key and value passed to the `func` indepedently and the results are saved to a structure with the same keys as the original map.

> **param** `iterable`: The array or structure to loop over.
> 
> **param** `val_kw`: The keyword to use when passing the value at each index of the `iterable` to the `func`.
> 
> **param** `key_kw`: The keyword to use when passing the key at each index of the `iterable` to the func.
> 
> **param** `func`: The function to run at each index of the `iterable`.
> 
> **return**: An array or structure containing all results from the `func`.

#### Range

Range, referenced with `$range`, creates an array of numbers with the first item being the start value and each subsequent value being the previous value plus `step` until that value reaches or surpassed `end`. The value that reaches or surpasses `end` will not be included in the array.

If `end` is less than `start`, `step` must be negative.

> **param** `start`: The first value in the array, 0 by default.
> 
> **param** `end`: The ending value of the range, exclusive.
> 
> **param** `step`: The difference between subsequent items in the array, 1 by default.
> 
> **return**: An array with values from `start` (inclusive) to `end` (exclusive) with a step size of `step`.

#### Length

Length, referenced with `$len`, returns the number of items in a given array or structure.

> **param** `iterable`: An array or structure.
> 
> **return**: The number of items in `iterable`.

#### In

In, referenced with `$in`, checks if an array or structure includes a given item. The `matcher` is used to compare items to the `target` and determine if they are the same. If the `iterable` is a structure and the `target` is a string, the keys of the structure will be searched for a match. Otherwise, the values will be searched for a match.

> **param** `iterable`: The array or structure to search for an item that matches the `target`.
> 
> **param** `target`: The item or key to search for.
> 
> **param** `matcher`: A function that tests if all items in an array match, returning a boolean. Uses hash codes by default.

## Compilation

SMFL is generally auto-compiling for single-file programs. This means that have a map from strings to objects (provided the types of all the objects in the map are supported by SMFL), merely creating a structure from that map will compile and run the program.

```java
Map<String, Object> rawData;
// ...
ScriptStructure structure = new ScriptStructure("root", rawData, s -> null);
System.out.println(structure);
```

However, for more complex programs, the compiler is necessary to allow multiple files to interact and to allow files to reference java functions.

### Compiler Builder

The compiler builder is used to add all files and java functions to the compiler before the compiler is built, as no new files or functions can be added to the compiler once it is built. 

```java
// Example compiler build
Compiler compiler = Compiler.builder()
                .addFile("src/main/java/com/striker/datascript/tests/data_test.toml")
                .addJavaFunction("print", new ScriptFunction<ScriptString>(
                        a -> {
                            System.out.println(a.get("msg").toString());
                            return new ScriptString(a.get("msg").toString());
                        },
                        new ScriptStructure(Map.of("msg", ScriptString.EMPTY))
                ))
                .build();
```

### Imports

The compiler enables files to use structures or functions from outside sources via imports. To import from another file, format the reference as follows:

```toml
data = "$import.OTHER_FILE_NAME.ITEM_PATH"
```

The `@` can be used for imports, but it is not recommended. To import a function that was passed to the compiler directly in java code, format the reference as follows:

```toml
func = "$import.java.FUNCTION_NAME"
```

### Running Functions

The compiler also enables the fetching of data and running of functions from SMFL files. The `get` function will retrieve the item at a given path (no prefix) or reference. The `run` function will do the same as `get`, but if the retrieved item is a function it will run that function with the passed arguments and return the result

---

> #### Last updated July 24th, 2026 for `v0.6.1`