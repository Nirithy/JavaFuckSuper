package com.obfuscator.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class JarBytecodeEngineTest {

    @Test
    public void testInvokeVirtualObfuscationWithArgs() throws Exception {
        File tempInputJar = File.createTempFile("test-input-invoke-args", ".jar");
        tempInputJar.deleteOnExit();

        File tempOutputJar = File.createTempFile("test-output-invoke-args", ".jar");
        tempOutputJar.deleteOnExit();

        String testClassName = "InvokeArgsTestClass";
        String testSource = "public class InvokeArgsTestClass {\n" +
                            "    public String callTarget() {\n" +
                            "        String target = \"Secret\";\n" +
                            "        return target.substring(1, 4);\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] testClassBytes = compiler.getCompiledClasses().get(testClassName);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempInputJar))) {
            jos.putNextEntry(new JarEntry(testClassName + ".class"));
            jos.write(testClassBytes);
            jos.closeEntry();
        }

        JarBytecodeEngine engine = new JarBytecodeEngine();
        engine.process(tempInputJar, tempOutputJar);

        URL[] urls = {tempOutputJar.toURI().toURL()};
        try (URLClassLoader cl = new URLClassLoader(urls)) {
            Class<?> clazz = cl.loadClass(testClassName);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            String result = (String) clazz.getMethod("callTarget").invoke(instance);
            assertEquals("ecr", result);
        }
    }

    @Test
    public void testControlFlowFlattening() throws Exception {
        File tempInputJar = File.createTempFile("test-input-cff", ".jar");
        tempInputJar.deleteOnExit();

        File tempOutputJar = File.createTempFile("test-output-cff", ".jar");
        tempOutputJar.deleteOnExit();

        String testClassName = "ControlFlowFlatteningTestClass";
        String testSource = "public class ControlFlowFlatteningTestClass {\n" +
                            "    public int complexCalculation(int input) {\n" +
                            "        int result = 0;\n" +
                            "        if (input > 10) {\n" +
                            "            result = input * 2;\n" +
                            "        } else {\n" +
                            "            result = input + 5;\n" +
                            "        }\n" +
                            "        for (int i = 0; i < 3; i++) {\n" +
                            "            result += i;\n" +
                            "        }\n" +
                            "        return result;\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] testClassBytes = compiler.getCompiledClasses().get(testClassName);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempInputJar))) {
            jos.putNextEntry(new JarEntry(testClassName + ".class"));
            jos.write(testClassBytes);
            jos.closeEntry();
        }

        JarBytecodeEngine engine = new JarBytecodeEngine();
        engine.process(tempInputJar, tempOutputJar);

        URL[] urls = {tempOutputJar.toURI().toURL()};
        try (URLClassLoader cl = new URLClassLoader(urls)) {
            Class<?> clazz = cl.loadClass(testClassName);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            int result1 = (int) clazz.getMethod("complexCalculation", int.class).invoke(instance, 15);
            assertEquals((15 * 2) + 0 + 1 + 2, result1);

            int result2 = (int) clazz.getMethod("complexCalculation", int.class).invoke(instance, 5);
            assertEquals((5 + 5) + 0 + 1 + 2, result2);
        }
    }

    @Test
    public void testExceptionControlFlowObfuscationInJar() throws Exception {
        File tempInputJar = File.createTempFile("test-input-ecf", ".jar");
        tempInputJar.deleteOnExit();

        File tempOutputJar = File.createTempFile("test-output-ecf", ".jar");
        tempOutputJar.deleteOnExit();

        String testClassName = "ExceptionControlFlowTestClass";
        String testSource = "public class ExceptionControlFlowTestClass {\n" +
                            "    public int testMethod() {\n" +
                            "        int x = 5;\n" +
                            "        int y = 10;\n" +
                            "        return x + y;\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] testClassBytes = compiler.getCompiledClasses().get(testClassName);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempInputJar))) {
            jos.putNextEntry(new JarEntry(testClassName + ".class"));
            jos.write(testClassBytes);
            jos.closeEntry();
        }

        JarBytecodeEngine engine = new JarBytecodeEngine();
        engine.process(tempInputJar, tempOutputJar);

        boolean foundFakeException = false;
        boolean hasTryCatchBlock = false;

        try (JarFile jarFile = new JarFile(tempOutputJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.equals(testClassName + ".class")) {
                    byte[] classBytes = readAllBytes(jarFile.getInputStream(entry));

                    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(classBytes);
                    org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
                    cr.accept(cn, 0);

                    for (org.objectweb.asm.tree.MethodNode method : cn.methods) {
                        if (method.name.equals("testMethod")) {
                            if (!method.tryCatchBlocks.isEmpty()) {
                                hasTryCatchBlock = true;
                            }

                            java.util.ListIterator<org.objectweb.asm.tree.AbstractInsnNode> iterator = method.instructions.iterator();
                            while (iterator.hasNext()) {
                                org.objectweb.asm.tree.AbstractInsnNode insn = iterator.next();
                                if (insn instanceof org.objectweb.asm.tree.LdcInsnNode) {
                                    Object cst = ((org.objectweb.asm.tree.LdcInsnNode) insn).cst;
                                    if (cst instanceof String && cst.equals("Fake Exception")) {
                                        foundFakeException = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        assertTrue(hasTryCatchBlock, "The obfuscated method should contain an injected try-catch block.");
        assertTrue(foundFakeException, "The obfuscated method should contain the fake exception string literal.");

        URL[] urls = {tempOutputJar.toURI().toURL()};
        try (URLClassLoader cl = new URLClassLoader(urls)) {
            Class<?> clazz = cl.loadClass(testClassName);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            int result = (int) clazz.getMethod("testMethod").invoke(instance);
            assertEquals(15, result, "The method should still return the correct result despite the fake exception control flow.");
        }
    }

    @Test
    public void testControlFlowObfuscation() throws Exception {
        File tempInputJar = File.createTempFile("test-input-cf", ".jar");
        tempInputJar.deleteOnExit();

        File tempOutputJar = File.createTempFile("test-output-cf", ".jar");
        tempOutputJar.deleteOnExit();

        String testClassName = "ControlFlowTestClass";
        String testSource = "public class ControlFlowTestClass {\n" +
                            "    public boolean compareInts(int a, int b) {\n" +
                            "        if (a == b) return true;\n" +
                            "        return false;\n" +
                            "    }\n" +
                            "    public boolean compareObjects(Object a, Object b) {\n" +
                            "        if (a != b) return true;\n" +
                            "        return false;\n" +
                            "    }\n" +
                            "    public boolean checkZero(int a) {\n" +
                            "        if (a == 0) return true;\n" +
                            "        return false;\n" +
                            "    }\n" +
                            "    public boolean checkNull(Object a) {\n" +
                            "        if (a == null) return true;\n" +
                            "        return false;\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] testClassBytes = compiler.getCompiledClasses().get(testClassName);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempInputJar))) {
            jos.putNextEntry(new JarEntry(testClassName + ".class"));
            jos.write(testClassBytes);
            jos.closeEntry();
        }

        JarBytecodeEngine engine = new JarBytecodeEngine();
        engine.process(tempInputJar, tempOutputJar);

        URL[] urls = {tempOutputJar.toURI().toURL()};
        try (URLClassLoader cl = new URLClassLoader(urls)) {
            Class<?> clazz = cl.loadClass(testClassName);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            boolean result1 = (boolean) clazz.getMethod("compareInts", int.class, int.class).invoke(instance, 10, 10);
            assertTrue(result1);

            boolean result2 = (boolean) clazz.getMethod("compareInts", int.class, int.class).invoke(instance, 10, 20);
            assertFalse(result2);

            Object obj1 = new Object();
            Object obj2 = new Object();
            boolean result3 = (boolean) clazz.getMethod("compareObjects", Object.class, Object.class).invoke(instance, obj1, obj2);
            assertTrue(result3);

            boolean result4 = (boolean) clazz.getMethod("compareObjects", Object.class, Object.class).invoke(instance, obj1, obj1);
            assertFalse(result4);
        }
    }

    @Test
    public void testFieldReadWriteObfuscation() throws Exception {
        File tempInputJar = File.createTempFile("test-input-field", ".jar");
        tempInputJar.deleteOnExit();

        File tempOutputJar = File.createTempFile("test-output-field", ".jar");
        tempOutputJar.deleteOnExit();

        String testClassName = "FieldTestClass";
        String testSource = "public class FieldTestClass {\n" +
                            "    public int myField = 10;\n" +
                            "    public int testField() {\n" +
                            "        myField = 42;\n" +
                            "        return myField;\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] testClassBytes = compiler.getCompiledClasses().get(testClassName);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempInputJar))) {
            jos.putNextEntry(new JarEntry(testClassName + ".class"));
            jos.write(testClassBytes);
            jos.closeEntry();
        }

        JarBytecodeEngine engine = new JarBytecodeEngine();
        engine.process(tempInputJar, tempOutputJar);

        URL[] urls = {tempOutputJar.toURI().toURL()};
        try (URLClassLoader cl = new URLClassLoader(urls)) {
            Class<?> clazz = cl.loadClass(testClassName);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            int result = (int) clazz.getMethod("testField").invoke(instance);
            assertEquals(42, result);
        }
    }

    @Test
    public void testNewInstructionObfuscation() throws Exception {
        File tempInputJar = File.createTempFile("test-input-new", ".jar");
        tempInputJar.deleteOnExit();

        File tempOutputJar = File.createTempFile("test-output-new", ".jar");
        tempOutputJar.deleteOnExit();

        String testClassName = "NewTestClass";
        String testSource = "public class NewTestClass {\n" +
                            "    public String testNew() {\n" +
                            "        return new String(\"HelloProxy\");\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] testClassBytes = compiler.getCompiledClasses().get(testClassName);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempInputJar))) {
            jos.putNextEntry(new JarEntry(testClassName + ".class"));
            jos.write(testClassBytes);
            jos.closeEntry();
        }

        JarBytecodeEngine engine = new JarBytecodeEngine();
        engine.process(tempInputJar, tempOutputJar);

        URL[] urls = {tempOutputJar.toURI().toURL()};
        try (URLClassLoader cl = new URLClassLoader(urls)) {
            Class<?> clazz = cl.loadClass(testClassName);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            String result = (String) clazz.getMethod("testNew").invoke(instance);
            assertEquals("HelloProxy", result);
        }
    }

    @Test
    public void testMethodCallObfuscationInJar() throws Exception {
        // 1. Compile a simple test class with a method call and put it into a JAR
        File tempInputJar = File.createTempFile("test-input-method", ".jar");
        tempInputJar.deleteOnExit();

        File tempOutputJar = File.createTempFile("test-output-method", ".jar");
        tempOutputJar.deleteOnExit();

        // We will test against standard library classes to avoid cross-compilation dependency issues
        String testClassName = "MethodCallerTestClass";
        String testSource = "public class MethodCallerTestClass {\n" +
                            "    public String callTarget() {\n" +
                            "        String target = new String(\"Secret\");\n" +
                            "        return target.toString();\n" + // This INVOKEVIRTUAL java/lang/String.toString() should be replaced
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] testClassBytes = compiler.getCompiledClasses().get(testClassName);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempInputJar))) {
            jos.putNextEntry(new JarEntry(testClassName + ".class"));
            jos.write(testClassBytes);
            jos.closeEntry();
        }

        // 2. Run Obfuscator Engine
        JarBytecodeEngine engine = new JarBytecodeEngine();
        engine.process(tempInputJar, tempOutputJar);

        // 3. Verify Output JAR
        assertTrue(tempOutputJar.exists(), "Output JAR should be created");

        boolean foundProxyClass = false;
        try (JarFile jarFile = new JarFile(tempOutputJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !name.equals("MethodCallerTestClass.class") && !name.contains("META-INF")) {
                    foundProxyClass = true; // Dynamically generated proxy class for the method call (and strings)
                }
            }
        }

        assertTrue(foundProxyClass, "At least one generated proxy class should be found in the JAR");

        // 4. Test run the obfuscated code to see if it still returns the correct value via proxy
        URL[] urls = {tempOutputJar.toURI().toURL()};
        try (URLClassLoader cl = new URLClassLoader(urls)) {
            Class<?> clazz = cl.loadClass(testClassName);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            String result = (String) clazz.getMethod("callTarget").invoke(instance);
            assertEquals("Secret", result, "The obfuscated method call should still return the correct string via proxy");
        }
    }

    @Test
    public void testStringObfuscationInJar() throws Exception {
        // 1. Compile a simple test class with a string literal and put it into a JAR
        File tempInputJar = File.createTempFile("test-input", ".jar");
        tempInputJar.deleteOnExit();

        File tempOutputJar = File.createTempFile("test-output", ".jar");
        tempOutputJar.deleteOnExit();

        String testClassName = "DummyTestClass";
        String testSource = "public class DummyTestClass {\n" +
                            "    public String sayHello() {\n" +
                            "        return \"Secret Hello World\";\n" +
                            "    }\n" +
                            "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] dummyClassBytes = compiler.getCompiledClasses().get(testClassName);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempInputJar))) {
            jos.putNextEntry(new JarEntry(testClassName + ".class"));
            jos.write(dummyClassBytes);
            jos.closeEntry();
        }

        // 2. Run Obfuscator Engine
        JarBytecodeEngine engine = new JarBytecodeEngine();
        engine.process(tempInputJar, tempOutputJar);

        // 3. Verify Output JAR
        assertTrue(tempOutputJar.exists(), "Output JAR should be created");
        assertTrue(tempOutputJar.length() > 0, "Output JAR should not be empty");

        boolean foundDummyClass = false;
        boolean foundProxyClass = false;

        try (JarFile jarFile = new JarFile(tempOutputJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.equals("DummyTestClass.class")) {
                    foundDummyClass = true;
                    // Check if "Secret Hello World" is completely missing from the constant pool
                    byte[] classBytes = readAllBytes(jarFile.getInputStream(entry));
                    String classString = new String(classBytes, "UTF-8");
                    assertFalse(classString.contains("Secret Hello World"), "The string literal must be removed from the class file");
                } else if (name.endsWith(".class") && !name.contains("META-INF")) {
                    foundProxyClass = true; // It's a dynamically generated proxy class
                }
            }
        }

        assertTrue(foundDummyClass, "The original class must still exist in the JAR");
        assertTrue(foundProxyClass, "At least one generated proxy class should be found in the JAR");

        // 4. Test run the obfuscated code to see if it still returns "Secret Hello World"
        URL[] urls = {tempOutputJar.toURI().toURL()};
        try (URLClassLoader cl = new URLClassLoader(urls)) {
            Class<?> clazz = cl.loadClass(testClassName);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            String result = (String) clazz.getMethod("sayHello").invoke(instance);
            assertEquals("Secret Hello World", result, "The obfuscated method should still return the correct string");
        }
    }

    @Test
    public void testMixedPrimitiveTypes() throws Exception {
        // 1. Compile dummy Java code
        String testClassName = "MixedPrimitiveTestClass";
        String testSource = "public class MixedPrimitiveTestClass {\n" +
                "    public static double calculate(byte b, short s, int i, long l, float f, double d) {\n" +
                "        return (b + s) * i - l + (f / d);\n" +
                "    }\n" +
                "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] testClassBytes = compiler.getCompiledClasses().get(testClassName);

        File tempDir = Files.createTempDirectory("jar-test-mixed").toFile();
        tempDir.deleteOnExit();

        File inputJar = new File(tempDir, "in.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(inputJar))) {
            jos.putNextEntry(new ZipEntry(testClassName + ".class"));
            jos.write(testClassBytes);
            jos.closeEntry();
        }

        // 2. Obfuscate
        File outputJar = new File(tempDir, "out.jar");
        JarBytecodeEngine engine = new JarBytecodeEngine();
        engine.process(inputJar, outputJar);

        // 3. Test run the obfuscated code
        URL[] urls = {outputJar.toURI().toURL()};
        try (URLClassLoader cl = new URLClassLoader(urls)) {
            Class<?> clazz = cl.loadClass(testClassName);
            java.lang.reflect.Method method = clazz.getMethod("calculate", byte.class, short.class, int.class, long.class, float.class, double.class);
            Object result = method.invoke(null, (byte) 10, (short) 20, 30, 40L, 50.0f, 2.0);

            assertEquals((10 + 20) * 30 - 40L + (50.0f / 2.0), (double) result, 0.0001);
        }
    }

    @Test
    public void testMultiGenericsMethodCalls() throws Exception {
        // 1. Compile dummy Java code
        String testClassName = "MultiGenericsTestClass";
        String testSource = "import java.util.List;\n" +
                "import java.util.Map;\n" +
                "import java.util.HashMap;\n" +
                "public class MultiGenericsTestClass {\n" +
                "    public static <T, U> int countElements(Map<T, List<U>> map) {\n" +
                "        int count = 0;\n" +
                "        for (List<U> list : map.values()) {\n" +
                "            count += list.size();\n" +
                "        }\n" +
                "        return count;\n" +
                "    }\n" +
                "    public static int execute() {\n" +
                "        Map<String, List<Integer>> map = new HashMap<>();\n" +
                "        map.put(\"A\", java.util.Arrays.asList(1, 2, 3));\n" +
                "        map.put(\"B\", java.util.Arrays.asList(4, 5));\n" +
                "        return countElements(map);\n" +
                "    }\n" +
                "}";

        InMemoryCompiler compiler = new InMemoryCompiler();
        compiler.compile(testClassName, testSource);
        byte[] testClassBytes = compiler.getCompiledClasses().get(testClassName);

        File tempDir = Files.createTempDirectory("jar-test-generics").toFile();
        tempDir.deleteOnExit();

        File inputJar = new File(tempDir, "in.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(inputJar))) {
            jos.putNextEntry(new ZipEntry(testClassName + ".class"));
            jos.write(testClassBytes);
            jos.closeEntry();
        }

        // 2. Obfuscate
        File outputJar = new File(tempDir, "out.jar");
        JarBytecodeEngine engine = new JarBytecodeEngine();
        engine.process(inputJar, outputJar);

        // 3. Test run the obfuscated code
        URL[] urls = {outputJar.toURI().toURL()};
        try (URLClassLoader cl = new URLClassLoader(urls)) {
            Class<?> clazz = cl.loadClass(testClassName);
            java.lang.reflect.Method method = clazz.getMethod("execute");
            Object result = method.invoke(null);

            assertEquals(5, result, "The obfuscated method should still return the correct element count");
        }
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
