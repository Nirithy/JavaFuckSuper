package com.obfuscator.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;

public class DexBytecodeEngineTest {

    @Test
    public void testMethodObfuscationInDex() throws Exception {
        String testClassName = "DummyDexMethodTestClass";
        String testSource = "public class DummyDexMethodTestClass {\n" +
                            "    public void callMethod() {\n" +
                            "        System.gc();\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] dummyClassBytes = compiler.getCompiledClasses().get(testClassName);

        DexCompiler dexCompiler = new DexCompiler() {};
        byte[] dexBytes = dexCompiler.compileClassToDex(dummyClassBytes);

        File tempInputDex = File.createTempFile("test-input-method", ".dex");
        tempInputDex.deleteOnExit();
        Files.write(tempInputDex.toPath(), dexBytes);

        File tempOutputDex = File.createTempFile("test-output-method", ".dex");
        tempOutputDex.deleteOnExit();

        DexEngine engine = new DexEngine();
        engine.process(tempInputDex, tempOutputDex);

        assertTrue(tempOutputDex.exists(), "Output DEX should be created");
        assertTrue(tempOutputDex.length() > 0, "Output DEX should not be empty");

        // Memory rule: The interception of NEW_INSTANCE, INVOKE_* is skipped.
        // We verify that the DEX is syntactically valid by parsing it and ensure
        // original instructions like INVOKE_VIRTUAL are still present.
        DexFile outDex = DexFileFactory.loadDexFile(tempOutputDex, Opcodes.getDefault());
        boolean hasInvokeVirtual = false;

        for (ClassDef classDef : outDex.getClasses()) {
            if (classDef.getType().contains("DummyDexMethodTestClass")) {
                for (Method method : classDef.getMethods()) {
                    if (method.getName().equals("callMethod") && method.getImplementation() != null) {
                        for (Instruction instruction : method.getImplementation().getInstructions()) {
                            if (instruction.getOpcode() == org.jf.dexlib2.Opcode.INVOKE_STATIC ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.INVOKE_VIRTUAL) {
                                hasInvokeVirtual = true;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(hasInvokeVirtual, "INVOKE instructions should remain intact as per memory rules.");
    }

    @Test
    public void testFieldObfuscationInDex() throws Exception {
        String testClassName = "DummyDexFieldTestClass";
        String testSource = "public class DummyDexFieldTestClass {\n" +
                            "    public static String helloField = \"test\";\n" +
                            "    public String getField() {\n" +
                            "        return helloField;\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] dummyClassBytes = compiler.getCompiledClasses().get(testClassName);

        DexCompiler dexCompiler = new DexCompiler() {};
        byte[] dexBytes = dexCompiler.compileClassToDex(dummyClassBytes);

        File tempInputDex = File.createTempFile("test-input-field", ".dex");
        tempInputDex.deleteOnExit();
        Files.write(tempInputDex.toPath(), dexBytes);

        File tempOutputDex = File.createTempFile("test-output-field", ".dex");
        tempOutputDex.deleteOnExit();

        DexEngine engine = new DexEngine();
        engine.process(tempInputDex, tempOutputDex);

        assertTrue(tempOutputDex.exists(), "Output DEX should be created");
        assertTrue(tempOutputDex.length() > 0, "Output DEX should not be empty");

        DexFile outDex = DexFileFactory.loadDexFile(tempOutputDex, Opcodes.getDefault());
        boolean hasSget = false;
        boolean hasInvokeStatic = false;

        for (ClassDef classDef : outDex.getClasses()) {
            if (classDef.getType().contains("DummyDexFieldTestClass")) {
                for (Method method : classDef.getMethods()) {
                    if (method.getName().equals("getField") && method.getImplementation() != null) {
                        for (Instruction instruction : method.getImplementation().getInstructions()) {
                            System.out.println("Instruction opcode in getField: " + instruction.getOpcode().name());
                            if (instruction.getOpcode() == org.jf.dexlib2.Opcode.SGET ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.SGET_OBJECT) {
                                hasSget = true;
                            }
                            if (instruction.getOpcode() == org.jf.dexlib2.Opcode.INVOKE_STATIC) {
                                hasInvokeStatic = true;
                            }
                        }
                    }
                }
            }
        }

        assertFalse(hasSget, "The sget / sget-object instruction must be entirely removed and replaced with proxy invoke in DEX.");
        assertTrue(hasInvokeStatic, "An invoke-static instruction should be present to call the proxy.");
    }

    @Test
    public void testClassCreationObfuscationInDex() throws Exception {
        String testClassName = "DummyDexClassCreationTestClass";
        String testSource = "public class DummyDexClassCreationTestClass {\n" +
                            "    public void createClass() {\n" +
                            "        new java.util.ArrayList();\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] dummyClassBytes = compiler.getCompiledClasses().get(testClassName);

        DexCompiler dexCompiler = new DexCompiler() {};
        byte[] dexBytes = dexCompiler.compileClassToDex(dummyClassBytes);

        File tempInputDex = File.createTempFile("test-input-class-creation", ".dex");
        tempInputDex.deleteOnExit();
        Files.write(tempInputDex.toPath(), dexBytes);

        File tempOutputDex = File.createTempFile("test-output-class-creation", ".dex");
        tempOutputDex.deleteOnExit();

        DexEngine engine = new DexEngine();
        engine.process(tempInputDex, tempOutputDex);

        assertTrue(tempOutputDex.exists(), "Output DEX should be created");
        assertTrue(tempOutputDex.length() > 0, "Output DEX should not be empty");

        DexFile outDex = DexFileFactory.loadDexFile(tempOutputDex, Opcodes.getDefault());
        boolean hasNewInstance = false;

        for (ClassDef classDef : outDex.getClasses()) {
            if (classDef.getType().contains("DummyDexClassCreationTestClass")) {
                for (Method method : classDef.getMethods()) {
                    if (method.getName().equals("createClass") && method.getImplementation() != null) {
                        for (Instruction instruction : method.getImplementation().getInstructions()) {
                            if (instruction.getOpcode() == org.jf.dexlib2.Opcode.NEW_INSTANCE) {
                                hasNewInstance = true;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(hasNewInstance, "NEW_INSTANCE instructions should remain intact as per memory rules.");
    }

    @Test
    public void testControlFlowObfuscationInDex() throws Exception {
        String testClassName = "DummyDexControlFlowTestClass";
        String testSource = "public class DummyDexControlFlowTestClass {\n" +
                            "    public boolean check(int x, int y) {\n" +
                            "        if (x == y) return true;\n" +
                            "        if (x != y) return false;\n" +
                            "        return x > y;\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] dummyClassBytes = compiler.getCompiledClasses().get(testClassName);

        DexCompiler dexCompiler = new DexCompiler() {};
        byte[] dexBytes = dexCompiler.compileClassToDex(dummyClassBytes);

        File tempInputDex = File.createTempFile("test-input-cf", ".dex");
        tempInputDex.deleteOnExit();
        Files.write(tempInputDex.toPath(), dexBytes);

        File tempOutputDex = File.createTempFile("test-output-cf", ".dex");
        tempOutputDex.deleteOnExit();

        DexEngine engine = new DexEngine();
        engine.process(tempInputDex, tempOutputDex);

        assertTrue(tempOutputDex.exists(), "Output DEX should be created");
        assertTrue(tempOutputDex.length() > 0, "Output DEX should not be empty");
    }

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

        DexFile outDex = DexFileFactory.loadDexFile(tempOutputDex, Opcodes.getDefault());
        boolean hasConstString = false;
        for (ClassDef classDef : outDex.getClasses()) {
            if (classDef.getType().contains("DummyDexTestClass")) {
                for (Method method : classDef.getMethods()) {
                    if (method.getImplementation() != null) {
                        for (Instruction instruction : method.getImplementation().getInstructions()) {
                            if (instruction.getOpcode() == org.jf.dexlib2.Opcode.CONST_STRING || instruction.getOpcode() == org.jf.dexlib2.Opcode.CONST_STRING_JUMBO) {
                                hasConstString = true;
                            }
                        }
                    }
                }
            }
        }
        assertFalse(hasConstString, "The string literal must be entirely removed and replaced with proxy invoke in DEX.");
    }
}
