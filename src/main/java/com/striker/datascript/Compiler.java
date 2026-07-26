package com.striker.datascript;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.striker.datascript.objects.ScriptFunction;
import com.striker.datascript.objects.ScriptObject;
import com.striker.datascript.objects.ScriptString;
import com.striker.datascript.objects.ScriptStructure;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class Compiler {

    private static final TomlMapper TOML_MAPPER = new TomlMapper();
    private static final JsonMapper JSON_MAPPER = new JsonMapper();

    private final Map<String, ScriptStructure> files;

    private Compiler(Map<String, Map<String, Object>> files) {
        this.files = new HashMap<>();
        for (String filename : files.keySet()) {
            Map<String, Object> data = files.get(filename);
            ScriptStructure structure = new ScriptStructure(filename, data, this::importContext);
            this.files.put(filename, structure);
        }
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Map<String, Function<String, Map<String, Object>>> fileMappers;
        private final Map<String, Map<String, Object>> files;

        public Builder() {
            files = new HashMap<>();
            fileMappers = new HashMap<>();
            fileMappers.put("toml", (s) -> {
                try {
                    return TOML_MAPPER.readValue(s, Map.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
            fileMappers.put("json", (s) -> {
                try {
                    return JSON_MAPPER.readValue(s, Map.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
            files.put("java", new HashMap<>());
        }

        public Builder addFileMapper(String extension, Function<String, Map<String, Object>> fileMapper) {
            this.fileMappers.put(extension, fileMapper);
            return this;
        }

        public Builder addFile(File file) throws IOException {
            String extension = file.getName().split("\\.")[1];
            String fileText = "";
            try (FileReader reader = new FileReader(file)) { fileText = reader.readAllAsString(); }
            Map<String, Object> data = fileMappers.get(extension).apply(fileText);
            files.put(file.getName().split("\\.")[0], data);
            return this;
        }

        public Builder addFile(String path) throws IOException {
            return this.addFile(Path.of(path).toAbsolutePath().toFile());
        }

        public Builder addJavaFunction(String name, ScriptFunction<?> function) {
            files.get("java").put(name, function);
            return this;
        }

        public Compiler build() {
            return new Compiler(files);
        }
    }

    public Supplier<ScriptObject<?>> importContext(String reference) {
        String[] split = reference.substring(1).split("\\.");
        if (!split[0].equals("import") || split.length < 3) { return null; }
        String filename = split[1];
        StringBuilder localRef = new StringBuilder(String.valueOf(reference.charAt(0)));
        for (int i = 2; i < split.length; i++) {
            localRef.append(split[i]).append(".");
        }
        localRef.deleteCharAt(localRef.length() - 1);
        return () -> {
            if (files.containsKey(filename)) {
                return files.get(filename).get(localRef.toString());
            }
            throw new RuntimeException("File not found or not compiled: " + filename);
        };
    }

    public ScriptObject<?> get(String path) {
        char prefix = path.charAt(0);
        String[] split = path.split("\\.");
        if (split.length == 1) {
            if (prefix == '$' || prefix == '@') { path = path.substring(1); }
            return files.get(path);
        } else {
            StringBuilder localRef = new StringBuilder();
            String filename = split[0];
            if (prefix != '$' && prefix != '@') { localRef.append("$"); }
            else {
                localRef.append(prefix);
                filename = filename.substring(1);
            }
            for (int i = 1; i < split.length; i++) {
                localRef.append(split[i]).append(".");
            }
            return files.get(filename).get(localRef.substring(1, localRef.length() - 1));
        }
    }

    public ScriptObject<?> run(String path, Map<String, ScriptObject<?>> args) {
        ScriptObject<?> func = this.get(path);
        while (!(func instanceof ScriptFunction<?>)) {
            if (func == null || !(func.get() instanceof ScriptObject<?>)) {
                return func;
            }
            func = ScriptObject.of(func.get());
        }
        var function = ScriptObject.assertType(func, ScriptFunction.DUMMY);
        return function.apply(new ScriptStructure(args));
    }

    public ScriptObject<?> run(String path) {
        return this.run(path, new HashMap<>());
    }

    public String toString() { return this.files.toString(); }
}
