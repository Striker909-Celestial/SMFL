package com.striker.datascript.tests;

import com.striker.datascript.Compiler;
import com.striker.datascript.objects.ScriptFunction;
import com.striker.datascript.objects.ScriptString;
import com.striker.datascript.objects.ScriptStructure;

import java.io.IOException;
import java.util.Map;

public class Test {

    public static void main(String[] args) throws IOException {
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

        System.out.println(compiler);
        compiler.run("data_test.func1");
    }
}
