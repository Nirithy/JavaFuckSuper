package com.obfuscator.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;

public class DexBytecodeEngineTest {

    @Test
    public void testStringObfuscationInDex() throws Exception {
        // Compile a dummy java class to .class using InMemoryCompiler
        String testClassName = "DummyDexTestClass";
        String testSource = "public class DummyDexTestClass {\n" +
                            "    public String sayHello() {\n" +
                            "        return \"Secret Hello Dex\";\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] dummyClassBytes = compiler.getCompiledClasses().get(testClassName);

        // Convert the .class to .dex
        DexCompiler dexCompiler = new DexCompiler() {};
        byte[] dexBytes = dexCompiler.compileClassToDex(dummyClassBytes);

        File tempInputDex = File.createTempFile("test-input", ".dex");
        tempInputDex.deleteOnExit();
        Files.write(tempInputDex.toPath(), dexBytes);

        File tempOutputDex = File.createTempFile("test-output", ".dex");
        tempOutputDex.deleteOnExit();

        DexEngine engine = new DexEngine();
        engine.process(tempInputDex, tempOutputDex);

        assertTrue(tempOutputDex.exists(), "Output DEX should be created");
        assertTrue(tempOutputDex.length() > 0, "Output DEX should not be empty");

        // We can't easily load dalvik classes inside standard JVM for testing their execution
        // without something like Robolectric. But we can inspect the generated DEX to see if string is missing
        byte[] outDexBytes = Files.readAllBytes(tempOutputDex.toPath());
        String outDexString = new String(outDexBytes, "UTF-8");
        assertFalse(outDexString.contains("Secret Hello Dex"), "The string literal must be obfuscated in the DEX file");
    }
}
