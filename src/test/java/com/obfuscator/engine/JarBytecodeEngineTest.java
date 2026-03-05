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
