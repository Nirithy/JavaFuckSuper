package com.obfuscator.engine;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Compiles Java source strings in memory and provides the resulting bytecode.
 */
public class InMemoryCompiler {

    private final JavaCompiler compiler;
    private final Map<String, byte[]> compiledClasses = new HashMap<>();
    private final StandardJavaFileManager standardFileManager;

    public InMemoryCompiler() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new RuntimeException("System JavaCompiler is not available. Ensure you are running with a JDK, not a JRE.");
        }
        this.standardFileManager = compiler.getStandardFileManager(null, null, null);
    }

    /**
     * Compiles a single Java class from a source string.
     *
     * @param className  The full name of the class (e.g., "MyProxy").
     * @param sourceCode The Java source code.
     */
    public void compile(String className, String sourceCode) {
        JavaFileObject sourceObj = new StringSourceJavaFileObject(className, sourceCode);
        MemoryJavaFileManager fileManager = new MemoryJavaFileManager(standardFileManager);

        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, null, null, Collections.singletonList(sourceObj));

        boolean success = task.call();
        if (!success) {
            throw new RuntimeException("Failed to compile class: " + className);
        }

        compiledClasses.putAll(fileManager.getCompiledClasses());
    }

    public Map<String, byte[]> getCompiledClasses() {
        return compiledClasses;
    }

    /**
     * Wraps the Java source string into a JavaFileObject.
     */
    private static class StringSourceJavaFileObject extends SimpleJavaFileObject {
        private final String sourceCode;

        protected StringSourceJavaFileObject(String className, String sourceCode) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }

    /**
     * A FileManager that stores compiled bytecodes in memory.
     */
    private static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayJavaFileObject> classBytes = new HashMap<>();

        protected MemoryJavaFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            ByteArrayJavaFileObject fileObject = new ByteArrayJavaFileObject(className);
            classBytes.put(className, fileObject);
            return fileObject;
        }

        public Map<String, byte[]> getCompiledClasses() {
            Map<String, byte[]> result = new HashMap<>();
            for (Map.Entry<String, ByteArrayJavaFileObject> entry : classBytes.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getBytes());
            }
            return result;
        }
    }

    /**
     * A JavaFileObject that holds the compiled bytecode in a byte array.
     */
    private static class ByteArrayJavaFileObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        protected ByteArrayJavaFileObject(String className) {
            super(URI.create("bytes:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        public byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }
}
