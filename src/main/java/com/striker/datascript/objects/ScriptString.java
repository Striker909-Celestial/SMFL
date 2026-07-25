package com.striker.datascript.objects;
import com.striker.datascript.Core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScriptString implements ScriptObject<Object> {

    public static final ScriptString EMPTY = new ScriptString("");

    private static int idNum = 0;

    private enum Type {
        PLAINTEXT,
        FSTRING,
        REFERENCE,
        MUT_REFERENCE,
        OPERATIONAL,
        ERROR
    }

    private final Pattern INSERT_PATTERN = Pattern.compile("(?:^|[^\\\\])\\{((?:[^{}]*?[^\\\\])??)}");
    private final Pattern PARENTHETICAL_PATTERN = Pattern.compile("(?:^|[^\\\\])\\(((?:[^()]*?[^\\\\])??)\\)");
    private final Pattern REFERENCE_PATTERN = Pattern.compile("(?:^|[^\\\\])(\\$\\S+)");
    private final Pattern MUT_PATTERN = Pattern.compile("(?:^|[^\\\\])(@\\S+)");
    private final Pattern ESCAPE_PATTERN = Pattern.compile("\\\\([$@{}+\\-*/%=!<>&|])");

    private Matcher insertMatcher;
    private Matcher parentheticalMatcher;
    private Matcher referenceMatcher;
    private Matcher mutMatcher;

    private final int id;
    public Supplier<String> strSupplier;
    private Type type;
    private boolean isText;
    private final Function<String, Supplier<ScriptObject<?>>> context;
    private Supplier<?> supplier;

    public ScriptString(String str, Function<String, Supplier<ScriptObject<?>>> context) {
        this.id = idNum++;
        this.context = context;
        this.setSupplier(() -> str);
    }

    public ScriptString(String str) {
        this.id = idNum++;
        this.context = null;
        this.setSupplier(() -> str);
    }

    private Type detectType() {
        if (insertMatcher.find()) {
            insertMatcher.reset();
            return Type.FSTRING;
        }
        long rCount = referenceMatcher.results().count();
        long mCount = mutMatcher.results().count();
        referenceMatcher.reset(); mutMatcher.reset();

        if (rCount + mCount == 0) { return Type.PLAINTEXT; }

        for (Pattern pattern : Core.OPERATOR_PATTERNS.values()) {
            if (pattern.matcher(str()).find()) { return Type.OPERATIONAL; }
        }

        if (rCount + mCount == 1) {
            if (referenceMatcher.find()) {
                referenceMatcher.reset();
                return Type.REFERENCE;
            }
            referenceMatcher.reset();
            return Type.MUT_REFERENCE;
        }
        return Type.ERROR;
    }

    private Supplier<String> buildFTextSupplier() {
        if (!insertMatcher.find()) { return () -> str(); }
        String insert = this.insertMatcher.group(1);
        int insertIndex = this.insertMatcher.start(1);
        List<ScriptString> strings = new ArrayList<>();
        if (insertIndex > 1) { strings.add(new ScriptString(str().substring(0, insertIndex - 1), context)); }
        strings.add(new ScriptString(insert, context));
        if (insertIndex + insert.length() + 1 < str().length()) { strings.add(new ScriptString(str().substring(insertIndex + insert.length() + 1), context)); }
        return () -> {
            StringBuilder builder = new StringBuilder();
            for (ScriptString string : strings) { builder.append(string.get()); }
            String output = builder.toString();
            return ESCAPE_PATTERN.matcher(output).replaceAll(m -> m.group(1));
        };
    }

    private Supplier<ScriptObject<?>> buildOperationalSupplier() {
        List<MatchResult> parentheticals = this.parentheticalMatcher.results().toList();
        this.parentheticalMatcher.reset();
        String newStr = str();
        List<ScriptString> parentheticalResults = new ArrayList<>();
        for (int i = 0; i < parentheticals.size(); i++) {
            String parenthetical = parentheticals.get(i).group(1);
            newStr = newStr.replace("(" + parenthetical + ")", "$[" + this.id + "~" + i + "]");
            parentheticalResults.add(new ScriptString(parenthetical, context));
        }

        Pattern parentheicalRefPattern = Pattern.compile("\\$\\[" + this.id + "~(\\d)]");
        Function<String, Supplier<ScriptObject<?>>> newContext = s -> {
            Matcher matcher = parentheicalRefPattern.matcher(s);
            if (matcher.matches()) {
                return () -> ScriptObject.of(parentheticalResults.get(Integer.parseInt(matcher.group(1))).get());
            }
            return context.apply(s);
        };

        for (String operator : Core.OPERATOR_SYMBOLS) {
            Matcher matcher = Core.OPERATOR_PATTERNS.get(operator).matcher(newStr);
            if (matcher.find()) {
                int numGroups = matcher.groupCount();
                List<ScriptObject<?>> args = new ArrayList<>();
                for (int i = 1; i <= numGroups; i++) {
                    args.add(new ScriptString(matcher.group(i), newContext));
                }
                return () -> {
                    try {
                        Map<String, ScriptObject<?>> newArgs = new HashMap<>();
                        for (int i = 0; i < args.size(); i++) {
                            newArgs.put(Core.OPERATOR_ARG_KEYWORDS[i], ScriptObject.of(args.get(i).get()));
                        }
                        return Core.OPERATORS.get(operator).apply(new ScriptStructure(newArgs));
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                };
            }
        }
        return null;
    }

    private String str() { return strSupplier.get(); }
    public boolean isText() { return isText; }

    public Supplier<Object> supplier() { return supplier::get; }
    public void setSupplier(Supplier<?> supplier) {
        this.strSupplier = () -> supplier.get().toString();
        this.insertMatcher = INSERT_PATTERN.matcher(str());
        this.parentheticalMatcher = PARENTHETICAL_PATTERN.matcher(str());
        this.referenceMatcher = REFERENCE_PATTERN.matcher(str());
        this.mutMatcher = MUT_PATTERN.matcher(str());

        if (this.context != null) { this.type = detectType(); }
        else { this.type = Type.PLAINTEXT; }
        this.supplier = switch (type) {
            case REFERENCE -> {
                boolean found = this.referenceMatcher.find();
                String reference = this.referenceMatcher.group(1);
                var coreResult = Core.context(reference);
                if (coreResult != null) { yield coreResult; }
                yield this.context.apply(reference);
            }
            case MUT_REFERENCE -> {
                boolean found = this.mutMatcher.find();
                yield this.context.apply(this.mutMatcher.group(1));
            }
            case FSTRING -> buildFTextSupplier();
            case OPERATIONAL -> buildOperationalSupplier();
            default -> () -> ESCAPE_PATTERN.matcher(strSupplier.get()).replaceAll(m -> m.group(1));
        };
        this.isText = type == Type.PLAINTEXT || type == Type.FSTRING;
    }
    public Object get() { return supplier.get(); }

    public double comparisonNumber() {
        var output = this.get();
        if (output instanceof String s) {
            double num = s.length();
            for (int i = (int) num; i > 0; i--) {
                num += 0.01 * i * (double) s.charAt(s.length() - i);
            }
            return num;
        }
        ScriptObject<?> outputObj = ScriptObject.of(this.get());
        if (outputObj == null) { return 0; }

        return outputObj.comparisonNumber();
    }
    public String toString() { return this.get().toString(); }
}
