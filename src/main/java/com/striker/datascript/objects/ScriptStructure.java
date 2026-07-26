package com.striker.datascript.objects;

import com.striker.datascript.Core;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/// A ScriptStructure is the core building block of a DataScript program. At its most basic level, a ScriptStrucure acts as a map of other [ScriptObject]s.
/// However, a ScriptStructure can also act as a function definition or a function call.
///
/// @author Striker-909
/// @since v0.1.0
public class ScriptStructure implements ScriptObject<Object> {

    /// An empty ScriptStructure that can serve as a placeholder.
    public static final ScriptStructure EMPTY = new ScriptStructure();

    private enum Type {
        FUNCTION_CALL,
        LAMBDA,
        DATA,
    }

    private final String path;
    private final Function<String, Supplier<ScriptObject<?>>> rawContext;
    private final Function<String, Supplier<ScriptObject<?>>> context;

    private Type type = Type.DATA;
    private Supplier<Map<String, ScriptObject<?>>> dataSupplier;
    private Supplier<?> supplier;

    private Supplier<Map<String, ScriptObject<?>>> passedArgs = Map::of;
    private ScriptArray privateArgs;

    /// A ScriptStructure is the core building block of a DataScript program. At its most basic level, a ScriptStrucure acts as a map of other [ScriptObject]s.
    /// However, a ScriptStructure can also act as a function definition or a function call.
    ///
    /// This initializer accepts a map of raw Java objects that are then converted into ScriptObjects.
    /// If any of the objects are maps, a ScriptStructure will be created from it in a recursive manner.
    ///
    /// @param path The path to this ScriptStructure from its root, i.e., the file it is initialized in.
    /// @param data A map of data and the keys associated with that data.
    /// @param context A function that accepts a path and returns a supplier for the variable associated with that path.
    public ScriptStructure(String path, Map<String, Object> data, Function<String, Supplier<ScriptObject<?>>> context) {
        HashMap<String, ScriptObject<?>> dataMap = new HashMap<>();
        this.supplier = this.dataSupplier = () -> dataMap;
        this.path = path;
        // builds the contex for this structure
        this.rawContext = context;
        this.context = (reference) -> {
            Supplier<ScriptObject<?>> supplier = Core.context(reference);
            if (supplier != null) { return supplier; }
            return context.apply(reference);
        };

        // the new context that is passed to script structures and strings that are children of this structure
        Function<String, Supplier<ScriptObject<?>>> childContext = this::supplier;
        // loops through all entries in the input data
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            ScriptObject<?> value = switch (entry.getValue()) {
                case String s -> new ScriptString(s, childContext);
                case Map m -> new ScriptStructure(path + "." + entry.getKey(), m, childContext); // maps become script structures with context
                default -> ScriptObject.of(entry.getValue()); // all other objects become script objects with no context
            };
            if (value == null) { continue; } // skips if the entry could not be converted into a script object
            dataMap.put(entry.getKey(), value);
        }

        // If this structure contains a "lambda" child, it is a lambda function definition
        if (this.type == Type.DATA && dataMap.containsKey("lambda")) {
            this.type = Type.LAMBDA;
            var lambda = dataMap.get("lambda");
            if (!(lambda instanceof ScriptStructure)) { throw new IllegalArgumentException("Lambda function definition must have a structure as its 'lambda' child"); }
            Function<Map<String, ScriptObject<?>>, ScriptObject<?>> function = (args) -> {
                Map<String, Object> newMap = new HashMap<>(data);
                for (Map.Entry<String, ScriptObject<?>> entry : args.entrySet()) {
                    if (!Objects.equals(entry.getKey(), "lambda") && newMap.containsKey(entry.getKey()) && entry.getValue() != null) { newMap.put(entry.getKey(), entry.getValue().get()); }
                }
                ScriptStructure newStructure = new ScriptStructure(this.path, newMap, context);
                return newStructure.get("$lambda.return");
            };
            HashMap<String, ScriptObject<?>> defaults = new HashMap<>();
            for (String key : this.data().keySet()) {
                if (key.equals("lambda")) { continue; }
                defaults.put(key, null);
            }

            ScriptObject<?> docsObj = this.get("$lambda.docs");
            String docs = (docsObj == null) ? "" : this.get("$lambda.docs").get().toString();

            ScriptObject<?> priv = this.get("$lambda.private");
            if (priv instanceof ScriptArray array) { privateArgs = array; }
            else { privateArgs = new ScriptArray(List.of()); }

            ScriptFunction<?> scriptFunction = new ScriptFunction<>(function, new ScriptStructure(defaults), docs);
            this.supplier = () -> scriptFunction;
        } else if (this.type == Type.DATA && dataMap.containsKey("run")) { // if a "run" child exists, this is a function call structure
            this.type = Type.FUNCTION_CALL;
            // the supplier will now supply the output of the function call
            this.supplier = () -> {
                ScriptObject<?> func = this.get("$run.func");
                while (!(func instanceof ScriptFunction<?>)) {
                    if (func == null || !(func.get() instanceof ScriptObject<?>)) {
                        throw new IllegalArgumentException("Function call structure must have a function as its 'func' child");
                    }
                    func = ScriptObject.of(func.get());
                }
                ScriptFunction<?> scriptFunction = ScriptObject.assertType(func, ScriptFunction.DUMMY);
                ScriptObject<?> argsObj = this.get("$run.args");
                return switch (argsObj) {
                    case ScriptStructure structure -> scriptFunction.apply(structure);
                    default -> scriptFunction.apply();
                };
            };
        }
        this.dataSupplier = () -> dataMap;
    }

    /// A simple ScriptObject equivalent for a map linking keys to ScriptObjects.
    ///
    /// This initializer has no path or context.
    ///
    /// @param data The map for this ScriptStructure to represent.
    public ScriptStructure(Map<String, ScriptObject<?>> data) {
        this.supplier = this.dataSupplier = () -> data;
        this.path = "";
        this.context = (reference) -> null;
        this.rawContext = (reference) -> null;
    }

    /// An initializer for an empty instance of a ScriptStructure.
    private ScriptStructure() { this(new HashMap<>()); }

    /// @return The path to this ScriptStructure from its root, i.e., the file it is initialized in.
    public String path() { return this.path; }
    /// @return A snapshot of the internal map of this ScriptStructure linking keys to ScriptObjects.
    public Map<String, ScriptObject<?>> data() { return this.dataSupplier.get(); }

    ///
    public Supplier<ScriptObject<?>> supplier(String reference) {
        if (reference == null || reference.isBlank()) { return null; }

        char prefix = reference.charAt(0);
        if (prefix != '$' && prefix != '@') {
            reference = "$" + reference;
            prefix = '$';
        }

        if (prefix == '$' && reference.length() == 1) {
            return switch (this.type) {
                case FUNCTION_CALL -> () -> ScriptObject.of(this.supplier.get());
                default -> () -> this;
            };
        } // If reference is just "$", return the structure itself

        String[] split = reference.substring(1).split("\\.");

        char finalPrefix = prefix;
        String finalReference = reference;
        return () -> {
            if (!this.data().containsKey(split[0])) {
                var response = this.rawContext.apply(finalReference);
                return (response == null) ? null : response.get();
            } // If reference doesn't exist, return null

            var obj = this.data().get(split[0]);
            if (obj instanceof ScriptStructure structure) { // Allows for indexing into structures such as "$data.structure.key" regardless of reference type
                if (finalPrefix == '@' && split.length == 1) {
                    return getSetter(split[0]);
                }
                if (split.length == 1) {
                    return structure;
                }
                return structure.supplier(finalPrefix + finalReference.substring(split[0].length() + 2)).get();
            }

            if (finalPrefix == '$') { // Read-only reference
                return switch (obj) {
                    // Allows for indexing into arrays such as "$data.array.0"
                    case ScriptArray array ->
                            (split.length > 1 && Pattern.matches("\\d+", split[1])) ? array.get(Integer.parseInt(split[1])) : array;
                    // case ScriptString string -> {
                    //     if (string.isText()) { yield string; }
                    //     yield ScriptObject.of(string.get());
                    // }
                    default -> obj;
                };
            } else { // Write-only reference
                return getSetter(split[0]);
            }
        };
    }

    private MutableScriptObject getSetter(String reference) {
        Consumer<ScriptObject<?>> setter = (o) -> this.data().get(reference).setSupplier(o.supplier());
        return new MutableScriptObject(setter);
    }

    public ScriptObject<?> get(String reference) {
        Supplier<ScriptObject<?>> resolvedSupplier = this.supplier(reference);
        if (resolvedSupplier == null) {
            throw new IllegalArgumentException("Unknown ScriptStructure reference '" + reference + "' in structure '" + this.path + "'");
        }
        return resolvedSupplier.get();
    }

    public Supplier<Object> supplier() { return supplier::get; }
    public void setSupplier(Supplier<?> supplier) { this.supplier = supplier; }
    public Object get() { return supplier == null ? null : supplier.get(); }

    public ScriptArray matches(ScriptObject<?> target, ScriptFunction<ScriptBoolean> matcher) {
        ArrayList<ScriptObject<?>> matches = new ArrayList<>();
        for (Map.Entry<String, ScriptObject<?>> entry : this.data().entrySet()) {
            if (matcher.apply(new ScriptArray(List.of(target, new ScriptString(entry.getKey())))).get()) {
                matches.add(entry.getValue());
            }
        }
        return new ScriptArray(matches);
    }

    public int size() { return this.data().size(); }
    public double comparisonNumber() {
        double num = size();
        for (Map.Entry<String, ScriptObject<?>> entry : this.data().entrySet()) {
            num += 0.01 * entry.getValue().comparisonNumber();
        }
        return num;
    }

    public boolean isFunctionCall() { return this.type == Type.FUNCTION_CALL; }
    public boolean isLambda() { return this.type == Type.LAMBDA; }
    public boolean isData() { return this.type == Type.DATA; }

    public String toString() { return String.valueOf(this.get()); }
}
