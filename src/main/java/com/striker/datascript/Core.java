package com.striker.datascript;

import com.striker.datascript.objects.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Core {

    /// Runs the given [ScriptFunction] with its default args.
    public static <T extends ScriptObject<?>> T run(ScriptFunction<T> func) {
        return func.apply();
    }
    /// Runs the given [ScriptFunction] with the given [ScriptStructure] of arguments.
    public static <T extends ScriptObject<?>> T run(ScriptFunction<T> func, ScriptStructure args) { return func.apply(args); }
    /// Runs the given [ScriptFunction] with the given [com.striker.datascript.objects.ScriptArray] of arguments.
    public static <T extends ScriptObject<?>> T run(ScriptFunction<T> func, ScriptArray args) { return func.apply(args); }

    /// Returns a [ScriptFunction] that sends the output of the given [ScriptFunction] to the given [Consumer].
    public static <T extends ScriptObject<?>> ScriptFunction<?> link(MutableScriptObject target, ScriptFunction<T> func) { return func.link(target); }

    /// Checks if the given condition is true.
    /// If it is, the then [ScriptFunction] will be run with its default parameters and the output returned.
    /// If not, the else [ScriptFunction] will be run with its default parameters and the output returned.
    public static ScriptObject<?> _if(ScriptBoolean condition, ScriptObject<?> then, ScriptObject<?> _else) {
        ScriptObject<?> result = _else;
        if (condition.get()) { result = then; }
        if (result instanceof ScriptFunction<?> res) { return run(res); }
        return result;
    }

    /// Runs a while loop, each loop running the condition [ScriptFunction] and then running the func if the condition is true.
    /// Adds the output of the func each loop to an array, which is returned once the loop finishes.
    public static <T extends ScriptObject<?>> ScriptArray _while(ScriptFunction<ScriptBoolean> condition, ScriptFunction<T> func) {
        List<ScriptObject<?>> results = new ArrayList<>();
        while (run(condition).get()) {
            results.add(run(func));
        }
        return new ScriptArray(results);
    }

    /// Loops through an array or a map, applies the func [ScriptFunction] to the value at each index.
    /// The output of the func is shaved to an array and returned.
    /// The items from the iterable are passed to the func with the val_kw given as the keyword.
    public static <T extends ScriptObject<?>> ScriptArray foreach(ScriptArray iterable, ScriptString val_kw, ScriptFunction<T> func) {
        List<ScriptObject<?>> results = new ArrayList<>();
        for (ScriptObject<?> item : iterable.get()) {
            results.add(run(func, new ScriptStructure(Map.of(val_kw.get().toString(), item))));
        }
        return new ScriptArray(results);
    }
    public static <T extends ScriptObject<?>> ScriptArray foreach(ScriptStructure iterable, ScriptString val_kw, ScriptFunction<T> func) {
        List<ScriptObject<?>> results = new ArrayList<>();
        for (String key : iterable.data().keySet()) {
            ScriptStructure item = new ScriptStructure(Map.of("key", new ScriptString(key), "value", iterable.data().get(key)));
            results.add(run(func, new ScriptStructure(Map.of(val_kw.get().toString(), item))));
        }
        return new ScriptArray(results);
    }
    /// Loops through a map, applies the func [ScriptFunction] to the key and value at each index.
    /// The output of the func is saved to a map, indexed with the corresponding key.
    /// The keys from the map are passed to the func with the key_kw given as the keyword, and the values are passed with val_kw.
    public static <T extends ScriptObject<?>> ScriptStructure foreach(ScriptStructure iterable, ScriptString key_kw, ScriptString val_kw, ScriptFunction<T> func) {
        Map<String, ScriptObject<?>> results = new HashMap<>();
        for (String key : iterable.data().keySet()) {
            results.put(key, run(func, new ScriptStructure(Map.of(key_kw.get().toString(), new ScriptString(key), val_kw.get().toString(), iterable.data().get(key)))));
        }
        return new ScriptStructure(results);
    }

    /// Generates a list of numbers with the first number being start and end being an exclusive upper bound.
    /// Each number is equal to the previous number plus the step.
    public static ScriptArray range(ScriptNumber start, ScriptNumber end, ScriptNumber step) {
        List<ScriptObject<?>> results = new ArrayList<>();
        for (double i = start.get(); (step.get() > 0) ? i < end.get() : i > end.get(); i += step.get()) {
            results.add(new ScriptNumber(i));
        }
        return new ScriptArray(results);
    }

    /// Returns the length of the given string.
    public static ScriptNumber len(ScriptString iterable) { return new ScriptNumber(iterable.get().toString().length()); }
    /// Returns the length of the given array.
    public static ScriptNumber len(ScriptArray iterable) {
        return new ScriptNumber(iterable.size());
    }
    /// Returns the length of the given structure.
    public static ScriptNumber len(ScriptStructure iterable) {
        return new ScriptNumber(iterable.size());
    }

    /// Checks if the given target is present in the given array.
    /// Uses the given matcher [ScriptFunction] to determine if an item matches the target.
    public static <T> ScriptBoolean in(ScriptArray iterable, ScriptObject<T> target, ScriptFunction<ScriptBoolean> matcher) {
        for (ScriptObject<?> item : iterable.get()) {
            if (run(matcher, new ScriptArray(List.of(target, item))).get()) { return ScriptBoolean.TRUE; }
        }
        return ScriptBoolean.FALSE;
    }
    /// Checks if the given target is present in the values of the given map.
    /// Uses the given matcher [ScriptFunction] to determine if an item matches the target.
    public static <T> ScriptBoolean in(ScriptStructure iterable, ScriptObject<T> target, ScriptFunction<ScriptBoolean> matcher) {
        return( in(new ScriptArray(iterable.data().values().stream().toList()), target, matcher));
    }
    /// Checks if the given target is a key in the given map.
    public static ScriptBoolean in(ScriptStructure iterable, ScriptString target) {
        return new ScriptBoolean(iterable.data().containsKey(target.get().toString()));
    }

    private static final ScriptFunction<ScriptBoolean> HASH_MATCHER = new ScriptFunction<>(
            args -> {
                var array = args.get("array");
                if (array instanceof ScriptArray arr) {
                    List<ScriptObject<?>> list = arr.get();
                    if (list.size() <= 1) { return ScriptBoolean.TRUE; }
                    int hash = list.get(0).hashCode();
                    for (int i = 1; i < list.size(); i++) {
                        if (list.get(i).hashCode() != hash) { return ScriptBoolean.FALSE; }
                    }
                    return ScriptBoolean.TRUE;
                }
                return ScriptBoolean.FALSE;
            },
            new ScriptStructure(Map.of("array", ScriptArray.EMPTY))
    );

    public static final Map<String, ScriptFunction<?>> CORE = Map.ofEntries(
            Map.entry("link", new ScriptFunction<ScriptFunction<?>>(
                    args -> link(
                            ScriptObject.assertType(args.get("target"), MutableScriptObject.DUMMY),
                            ScriptObject.assertType(args.get("func"), ScriptFunction.DUMMY)),
                    new ScriptStructure(Map.of("target", MutableScriptObject.DUMMY, "func", ScriptFunction.DUMMY))
            )),
            Map.entry("if", new ScriptFunction<ScriptObject<?>>(
                    args -> _if(
                            ScriptObject.assertType(args.get("condition"), ScriptBoolean.FALSE),
                            args.get("then"), args.get("else")),
                    new ScriptStructure(Map.of("condition", ScriptBoolean.TRUE, "then", ScriptObject.DUMMY, "else", ScriptObject.DUMMY))
            )),
            Map.entry("while", new ScriptFunction<>(
                    args -> _while(
                            ScriptObject.assertType(args.get("condition"), ScriptFunction.DUMMY_BOOLEAN),
                            ScriptObject.assertType(args.get("func"), ScriptFunction.DUMMY)),
                    new ScriptStructure(Map.of("condition", ScriptFunction.DUMMY_BOOLEAN, "func", ScriptFunction.DUMMY))
            )),
            Map.entry("foreach", new ScriptFunction<>(
                    args -> {
                        var iterable = args.get("iterable");
                        if (ScriptObject.matchType(iterable, ScriptArray.EMPTY)) { return foreach(
                                    ScriptObject.assertType(iterable, ScriptArray.EMPTY),
                                    ScriptObject.assertType(args.get("val_kw"), ScriptString.EMPTY),
                                    ScriptObject.assertType(args.get("func"), ScriptFunction.DUMMY)); }
                        if (ScriptObject.matchType(iterable, ScriptStructure.EMPTY)) {
                            var key_kw = args.get("key_kw");
                            if (ScriptObject.assertType(key_kw, ScriptString.EMPTY).get().equals("")) { return foreach(
                                    ScriptObject.assertType(iterable, ScriptStructure.EMPTY),
                                    ScriptObject.assertType(args.get("val_kw"), ScriptString.EMPTY),
                                    ScriptObject.assertType(args.get("func"), ScriptFunction.DUMMY)); }
                            else { return foreach(
                                    ScriptObject.assertType(iterable, ScriptStructure.EMPTY),
                                    ScriptObject.assertType(key_kw, ScriptString.EMPTY),
                                    ScriptObject.assertType(args.get("val_kw"), ScriptString.EMPTY),
                                    ScriptObject.assertType(args.get("func"), ScriptFunction.DUMMY)); }
                        }
                        return null;
                    },
                    new ScriptStructure(Map.of("iterable", ScriptArray.EMPTY, "key_kw", ScriptString.EMPTY, "val_kw", ScriptString.EMPTY, "func", ScriptFunction.DUMMY))
            )),
            Map.entry("range", new ScriptFunction<>(
                    args -> range(
                            ScriptObject.assertType(args.get("start"), ScriptNumber.ZERO),
                            ScriptObject.assertType(args.get("end"), ScriptNumber.ZERO),
                            ScriptObject.assertType(args.get("step"), ScriptNumber.ZERO)
                    ),
                    new ScriptStructure(Map.of("start", ScriptNumber.ZERO, "end", ScriptNumber.ZERO, "step", ScriptNumber.ONE))
            )),
            Map.entry("len", new ScriptFunction<>(
                    args -> {
                        var iterable = args.get("iterable");
                        if (ScriptObject.matchType(iterable, ScriptString.EMPTY)) { return len(ScriptObject.assertType(iterable, ScriptString.EMPTY)); }
                        if (ScriptObject.matchType(iterable, ScriptArray.EMPTY)) { return len(ScriptObject.assertType(iterable, ScriptArray.EMPTY)); }
                        if (ScriptObject.matchType(iterable, ScriptStructure.EMPTY)) { return len(ScriptObject.assertType(iterable, ScriptStructure.EMPTY)); }
                        return null;
                    },
                    new ScriptStructure(Map.of("iterable", ScriptArray.EMPTY))
            )),
            Map.entry("in", new ScriptFunction<>(
                    args -> {
                        var iterable = args.get("iterable");
                        if (ScriptObject.matchType(iterable, ScriptArray.EMPTY)) { return in(
                                    ScriptObject.assertType(iterable, ScriptArray.EMPTY),
                                    args.get("target"),
                                    ScriptObject.assertType(args.get("matcher"), ScriptFunction.DUMMY_BOOLEAN)); }
                        if (ScriptObject.matchType(iterable, ScriptStructure.EMPTY)) {
                            var target = args.get("target");
                            if (ScriptObject.assertType(target, ScriptString.EMPTY).get().equals("")) { return in(
                                    ScriptObject.assertType(iterable, ScriptStructure.EMPTY),
                                    target,
                                    ScriptObject.assertType(args.get("matcher"), ScriptFunction.DUMMY_BOOLEAN)); }
                            else { return in(
                                    ScriptObject.assertType(iterable, ScriptStructure.EMPTY),
                                    ScriptObject.assertType(target, ScriptString.EMPTY)); }
                        }
                        return null;
                    },
                    new ScriptStructure(Map.of("iterable", ScriptArray.EMPTY, "target", ScriptString.EMPTY, "matcher", HASH_MATCHER))
            ))
    );

    public static Supplier<ScriptObject<?>> context(String reference) {
        if (reference.startsWith("$") && CORE.containsKey(reference.substring(1))) { return () -> CORE.get(reference.substring(1)); }
        return null;
    }

    public static final String[] OPERATOR_ARG_KEYWORDS = { "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"};
    public static final String[] OPERATOR_SYMBOLS = { "+", "-", "*", "/", "//", "%", "**", "==", "!=", "<", ">", "<=", ">=", "&&", "||", "!"};

    public static final Map<String, ScriptFunction<?>> OPERATORS = Map.ofEntries(
            Map.entry("+", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        if (a instanceof ScriptNumber aNum && b instanceof ScriptNumber bNum) {
                            return ScriptObject.of(aNum.get() + bNum.get());
                        }
                        if (ScriptObject.matchType(a, ScriptString.EMPTY) && ScriptObject.matchType(b, ScriptString.EMPTY)) {
                            return ScriptObject.of(ScriptObject.assertType(a, ScriptString.EMPTY).get().toString() + ScriptObject.assertType(b, ScriptString.EMPTY).get().toString());
                        }
                        if (ScriptObject.matchType(a, ScriptArray.EMPTY)) {
                            List<ScriptObject<?>> newList = new ArrayList<>(ScriptObject.assertType(a, ScriptArray.EMPTY).get());
                            if (ScriptObject.matchType(b, ScriptArray.EMPTY)) {
                                newList.addAll(ScriptObject.assertType(b, ScriptArray.EMPTY).get());
                            } else {
                                newList.add(b);
                            }
                            return new ScriptArray(newList);
                        }
                        if (ScriptObject.matchType(b, ScriptArray.EMPTY)) {
                            List<ScriptObject<?>> newList = new ArrayList<>(ScriptObject.assertType(b, ScriptArray.EMPTY).get());
                            newList.addFirst(a);
                            return new ScriptArray(newList);
                        }
                        if (ScriptObject.matchType(a, ScriptStructure.EMPTY) && ScriptObject.matchType(b, ScriptStructure.EMPTY)) {
                            Map<String, ScriptObject<?>> newMap = new HashMap<>(ScriptObject.assertType(a, ScriptStructure.EMPTY).data());
                            newMap.putAll(ScriptObject.assertType(b, ScriptStructure.EMPTY).data());
                            return new ScriptStructure(newMap);
                        }
                        return null;
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ZERO, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ZERO))
            )),
            Map.entry("-", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        if (a instanceof ScriptNumber aNum && b instanceof ScriptNumber bNum) {
                            return ScriptObject.of(aNum.get() - bNum.get());
                        }
                        if (ScriptObject.matchType(a, ScriptString.EMPTY) && ScriptObject.matchType(b, ScriptString.EMPTY)) {
                            return ScriptObject.of((ScriptObject.assertType(a, ScriptString.EMPTY).get().toString()).replace(ScriptObject.assertType(b, ScriptString.EMPTY).get().toString(), ""));
                        }
                        if (ScriptObject.matchType(a, ScriptStructure.EMPTY) && ScriptObject.matchType(b, ScriptString.EMPTY)) {
                            Map<String, ScriptObject<?>> newMap = new HashMap<>(ScriptObject.assertType(a, ScriptStructure.EMPTY).data());
                            newMap.remove(ScriptObject.assertType(b, ScriptString.EMPTY).get().toString());
                            return new ScriptStructure(newMap);
                        }
                        if (ScriptObject.matchType(a, ScriptArray.EMPTY) && b instanceof ScriptNumber bNum) {
                            List<ScriptObject<?>> newList = new ArrayList<>(ScriptObject.assertType(a, ScriptArray.EMPTY).get());
                            newList.remove(bNum.get().intValue());
                            return new ScriptArray(newList);
                        }
                        return null;
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ZERO, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ZERO))
            )),
            Map.entry("*", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        if (a instanceof ScriptNumber aNum && b instanceof ScriptNumber bNum) {
                            return ScriptObject.of(aNum.get() * bNum.get());
                        }
                        if (ScriptObject.matchType(a, ScriptString.EMPTY) && b instanceof ScriptNumber bNum) {
                            return ScriptObject.of((ScriptObject.assertType(a, ScriptString.EMPTY).get().toString()).repeat(Math.max(0, bNum.get().intValue())));
                        }
                        if (ScriptObject.matchType(a, ScriptArray.EMPTY) && b instanceof ScriptNumber bNum) {
                            List<ScriptObject<?>> _a = ScriptObject.assertType(a, ScriptArray.EMPTY).get();
                            List<ScriptObject<?>> newList = new ArrayList<>();
                            for (int i = 0; i < bNum.get().intValue(); i++) {
                                newList.addAll(_a);
                            }
                            return new ScriptArray(newList);
                        }
                        return null;
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry("/", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        if (a instanceof ScriptNumber aNum && b instanceof ScriptNumber bNum) {
                            return new ScriptNumber(aNum.get() / bNum.get());
                        }
                        return null;
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry("//", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        if (a instanceof ScriptNumber aNum && b instanceof ScriptNumber bNum) {
                            return new ScriptNumber((double) (aNum.get().intValue() / bNum.get().intValue()));
                        }
                        return null;
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry("%", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        if (a instanceof ScriptNumber aNum && b instanceof ScriptNumber bNum) {
                            return new ScriptNumber(aNum.get() % bNum.get());
                        }
                        return null;
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry("**", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        if (a instanceof ScriptNumber aNum && b instanceof ScriptNumber bNum) {
                            return new ScriptNumber(Math.pow(aNum.get(), bNum.get()));
                        }
                        return null;
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry("==", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        return new ScriptBoolean(a.get().equals(b.get()));
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry("!=", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        return new ScriptBoolean(!a.get().equals(b.get()));
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry(">", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        return new ScriptBoolean(a.comparisonNumber() > b.comparisonNumber());
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry("<", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        return new ScriptBoolean(a.comparisonNumber() < b.comparisonNumber());
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry(">=", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        if (a.get().equals(b.get())) {
                            return ScriptBoolean.TRUE;
                        }
                        return new ScriptBoolean(a.comparisonNumber() > b.comparisonNumber());
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry("<=", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        if (a.get().equals(b.get())) { return ScriptBoolean.TRUE; }
                        return new ScriptBoolean(a.comparisonNumber() < b.comparisonNumber());
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptNumber.ONE, OPERATOR_ARG_KEYWORDS[1], ScriptNumber.ONE))
            )),
            Map.entry("&&", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        return new ScriptBoolean(ScriptObject.assertType(a, ScriptBoolean.FALSE).get() && ScriptObject.assertType(b, ScriptBoolean.FALSE).get());
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptBoolean.FALSE, OPERATOR_ARG_KEYWORDS[1], ScriptBoolean.FALSE))
            )),
            Map.entry("||", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        var b = args.get(OPERATOR_ARG_KEYWORDS[1]);
                        return new ScriptBoolean(ScriptObject.assertType(a, ScriptBoolean.FALSE).get() || ScriptObject.assertType(b, ScriptBoolean.FALSE).get());
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptBoolean.FALSE, OPERATOR_ARG_KEYWORDS[1], ScriptBoolean.FALSE))
            )),
            Map.entry("!", new ScriptFunction<>(
                    args -> {
                        var a = args.get(OPERATOR_ARG_KEYWORDS[0]);
                        if (a instanceof ScriptBoolean bool) { return new ScriptBoolean(!bool.get()); }
                        return ScriptBoolean.FALSE;
                    },
                    new ScriptStructure(Map.of(OPERATOR_ARG_KEYWORDS[0], ScriptBoolean.FALSE))
            ))
    );
    
    public static final LinkedHashMap<String, Pattern> OPERATOR_PATTERNS = Stream.of(
            Map.entry("+",   Pattern.compile("(.*[^\\\\\\s])\\s*\\+\\s*(\\S.*)")),
            Map.entry("-",   Pattern.compile("(.*[^\\\\\\s])\\s*-\\s*(\\S.*)")),
            Map.entry("*",   Pattern.compile("(.*[^\\\\\\s*])\\s*\\*\\s*([^\\s*].*)")),
            Map.entry("/",   Pattern.compile("(.*[^\\\\\\s/])\\s*/\\s*([^\\s/].*)")),
            Map.entry("//",  Pattern.compile("(.*[^\\\\\\s])\\s*//\\s*(\\S.*)")),
            Map.entry("%",   Pattern.compile("(.*[^\\\\\\s])\\s*%\\s*(\\S.*)")),
            Map.entry("**",  Pattern.compile("(.*[^\\\\\\s])\\s*\\*\\*\\s*(\\S.*)")),
            Map.entry("==",  Pattern.compile("(.*[^\\\\\\s])\\s*==\\s*(\\S.*)")),
            Map.entry("!=",  Pattern.compile("(.*[^\\\\\\s])\\s*!=\\s*(\\S.*)")),
            Map.entry(">",   Pattern.compile("(.*[^\\\\\\s])\\s*>\\s*([^\\s=].*)")),
            Map.entry("<",   Pattern.compile("(.*[^\\\\\\s])\\s*<\\s*([^\\s=].*)")),
            Map.entry(">=",  Pattern.compile("(.*[^\\\\\\s])\\s*>=\\s*(\\S.*)")),
            Map.entry("<=",  Pattern.compile("(.*[^\\\\\\s])\\s*<=\\s*(\\S.*)")),
            Map.entry("&&",  Pattern.compile("(.*[^\\\\\\s])\\s*&&\\s*(\\S.*)")),
            Map.entry("||",  Pattern.compile("(.*[^\\\\\\s])\\s*\\|\\|\\s*(\\S.*)")),
            Map.entry("!",   Pattern.compile("!\\s*([^\\s=].*)"))
    ).collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (oldValue, newValue) -> oldValue,
            LinkedHashMap::new
    ));
}
