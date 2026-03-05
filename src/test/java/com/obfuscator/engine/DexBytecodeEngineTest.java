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

        // Now that INVOKE_STATIC/INVOKE_VIRTUAL is supported, we assert that the original
        // System.gc() call (INVOKE_STATIC to java.lang.System) is replaced with our proxy.
        // And the new instruction should still be an INVOKE_STATIC but to our proxy class.
        DexFile outDex = DexFileFactory.loadDexFile(tempOutputDex, Opcodes.getDefault());
        boolean hasProxyInvoke = false;
        boolean hasOriginalInvoke = false;

        for (ClassDef classDef : outDex.getClasses()) {
            if (classDef.getType().contains("DummyDexMethodTestClass")) {
                for (Method method : classDef.getMethods()) {
                    if (method.getName().equals("callMethod") && method.getImplementation() != null) {
                        for (Instruction instruction : method.getImplementation().getInstructions()) {
                            if (instruction.getOpcode() == org.jf.dexlib2.Opcode.INVOKE_STATIC ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.INVOKE_VIRTUAL) {
                                org.jf.dexlib2.iface.instruction.ReferenceInstruction refInst = (org.jf.dexlib2.iface.instruction.ReferenceInstruction) instruction;
                                org.jf.dexlib2.iface.reference.MethodReference methodRef = (org.jf.dexlib2.iface.reference.MethodReference) refInst.getReference();
                                String owner = methodRef.getDefiningClass();

                                if (owner.equals("Ljava/lang/System;") && methodRef.getName().equals("gc")) {
                                    hasOriginalInvoke = true;
                                } else if (!owner.startsWith("Ljava")) {
                                    hasProxyInvoke = true; // Proxy class is dynamic
                                }
                            }
                        }
                    }
                }
            }
        }
        assertFalse(hasOriginalInvoke, "The original method call should be removed.");
        assertTrue(hasProxyInvoke, "A proxy method call should be inserted in place of the original method invocation.");
    }

    @Test
    public void testFieldObfuscationInDex() throws Exception {
        String testClassName = "DummyDexFieldTestClass";
        String testSource = "public class DummyDexFieldTestClass {\n" +
                            "    public static String helloField = \"test\";\n" +
                            "    public static int staticInt = 42;\n" +
                            "    public int instanceInt = 100;\n" +
                            "    public String getField() {\n" +
                            "        return helloField;\n" +
                            "    }\n" +
                            "    public void setFields() {\n" +
                            "        helloField = \"new test\";\n" +
                            "        staticInt = 43;\n" +
                            "        instanceInt = 101;\n" +
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
        boolean hasSput = false;
        boolean hasIput = false;
        boolean hasInvokeStatic = false;

        for (ClassDef classDef : outDex.getClasses()) {
            if (classDef.getType().contains("DummyDexFieldTestClass")) {
                for (Method method : classDef.getMethods()) {
                    if (method.getName().equals("getField") && method.getImplementation() != null) {
                        for (Instruction instruction : method.getImplementation().getInstructions()) {
                            if (instruction.getOpcode() == org.jf.dexlib2.Opcode.SGET ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.SGET_OBJECT) {
                                hasSget = true;
                            }
                            if (instruction.getOpcode() == org.jf.dexlib2.Opcode.INVOKE_STATIC) {
                                hasInvokeStatic = true;
                            }
                        }
                    } else if (method.getName().equals("setFields") && method.getImplementation() != null) {
                        for (Instruction instruction : method.getImplementation().getInstructions()) {
                            if (instruction.getOpcode() == org.jf.dexlib2.Opcode.SPUT ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.SPUT_OBJECT ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.SPUT_WIDE ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.SPUT_BOOLEAN ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.SPUT_BYTE ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.SPUT_CHAR ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.SPUT_SHORT) {
                                hasSput = true;
                                System.out.println("Found SPUT opcode that was not removed: " + instruction.getOpcode().name());
                            }
                            if (instruction.getOpcode() == org.jf.dexlib2.Opcode.IPUT ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.IPUT_OBJECT ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.IPUT_WIDE ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.IPUT_BOOLEAN ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.IPUT_BYTE ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.IPUT_CHAR ||
                                instruction.getOpcode() == org.jf.dexlib2.Opcode.IPUT_SHORT) {
                                hasIput = true;
                            }
                        }
                    }
                }
            }
        }

        assertFalse(hasSget, "The sget / sget-object instruction must be entirely removed and replaced with proxy invoke in DEX.");
        // We only expect hasSput to be false. If there are other instructions that are considered 'SPUT' they should have been intercepted.
        // Wait, is there a chance we missed some SPUT instructions? We added them to the list!
        // But what about SPUT_STRING? No such thing.
        // Wait, what if the instructions were NOT replaced because they failed the `instr21c.getReference() instanceof FieldReference`? No, they always have it.
        // Let's print the opcodes if it fails.
        // For testing we will just assert false.
        assertFalse(hasSput, "The sput instruction must be entirely removed and replaced with proxy invoke in DEX.");
        assertFalse(hasIput, "The iput instruction must be entirely removed and replaced with proxy invoke in DEX.");
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
    public void testApkAndMultiDexProcessing() throws Exception {
        // 1. Create a dummy APK (ZIP file) containing a classes.dex and a random text file
        File tempApk = File.createTempFile("test-input-apk", ".apk");
        tempApk.deleteOnExit();

        String testClassName = "DummyApkTestClass";
        String testSource = "public class DummyApkTestClass {\n" +
                            "    public String sayHello() {\n" +
                            "        return \"APK String\";\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] dummyClassBytes = compiler.getCompiledClasses().get(testClassName);

        DexCompiler dexCompiler = new DexCompiler() {};
        byte[] dexBytes = dexCompiler.compileClassToDex(dummyClassBytes);

        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(tempApk))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("classes.dex"));
            zos.write(dexBytes);
            zos.closeEntry();

            zos.putNextEntry(new java.util.zip.ZipEntry("assets/config.txt"));
            zos.write("dummy config data".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new java.util.zip.ZipEntry("META-INF/mock_signature"));
            zos.write("mock signature".getBytes());
            zos.closeEntry();
        }

        File tempOutputApk = File.createTempFile("test-output-apk", ".apk");
        tempOutputApk.deleteOnExit();

        // 2. Process the APK using DexEngine
        DexEngine engine = new DexEngine();
        engine.process(tempApk, tempOutputApk);

        assertTrue(tempOutputApk.exists(), "Output APK should be created");
        assertTrue(tempOutputApk.length() > 0, "Output APK should not be empty");

        // 3. Verify the contents of the output APK
        boolean foundDex = false;
        boolean foundConfig = false;
        boolean hasConstString = false;
        boolean foundMetaInf = false;

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(tempOutputApk))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("META-INF/")) {
                    foundMetaInf = true;
                }

                if (entry.getName().endsWith(".dex")) {
                    foundDex = true;
                    // Read dex bytes to verify obfuscation
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }

                    File tempExtractedDex = File.createTempFile("extracted", ".dex");
                    tempExtractedDex.deleteOnExit();
                    Files.write(tempExtractedDex.toPath(), baos.toByteArray());

                    DexFile outDex = DexFileFactory.loadDexFile(tempExtractedDex, Opcodes.getDefault());
                    for (ClassDef classDef : outDex.getClasses()) {
                        if (classDef.getType().contains(testClassName)) {
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

                } else if (entry.getName().equals("assets/config.txt")) {
                    foundConfig = true;
                }
            }
        }

        assertTrue(foundDex, "Output APK should contain .dex file(s)");
        assertTrue(foundConfig, "Output APK should retain original non-dex resources");
        assertFalse(hasConstString, "The string literal must be obfuscated inside the APK's dex file");
        assertFalse(foundMetaInf, "Output APK should not contain any files from the META-INF directory (old signatures)");
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
