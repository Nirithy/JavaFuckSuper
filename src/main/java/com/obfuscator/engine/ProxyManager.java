package com.obfuscator.engine;

import com.obfuscator.generator.DynamicNameGenerator;
import com.obfuscator.generator.ProxyGenerator;
import com.obfuscator.generator.StringProxyGenerator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the generation, deduplication, and compilation of proxy classes.
 */
public class ProxyManager {
    // Maps original value to generated proxy class name
    private final Map<String, String> stringProxies = new ConcurrentHashMap<>();
    private final Map<String, String> methodProxies = new ConcurrentHashMap<>();
    private final Map<String, String> classCreationProxies = new ConcurrentHashMap<>();
    private final Map<String, String> fieldProxies = new ConcurrentHashMap<>();
    private final Map<String, String> controlFlowProxies = new ConcurrentHashMap<>();

    private final StringProxyGenerator stringProxyGenerator = new StringProxyGenerator();
    private final com.obfuscator.generator.MethodProxyGenerator methodProxyGenerator = new com.obfuscator.generator.MethodProxyGenerator();
    private final com.obfuscator.generator.DexMethodProxyGenerator dexMethodProxyGenerator = new com.obfuscator.generator.DexMethodProxyGenerator();
    private final com.obfuscator.generator.ClassCreationProxyGenerator classCreationProxyGenerator = new com.obfuscator.generator.ClassCreationProxyGenerator();
    private final com.obfuscator.generator.FieldProxyGenerator fieldProxyGenerator = new com.obfuscator.generator.FieldProxyGenerator();
    private final com.obfuscator.generator.ControlFlowProxyGenerator controlFlowProxyGenerator = new com.obfuscator.generator.ControlFlowProxyGenerator();
    private final com.obfuscator.generator.AntiDebugProxyGenerator antiDebugProxyGenerator = new com.obfuscator.generator.AntiDebugProxyGenerator();
    private final InMemoryCompiler compiler = new InMemoryCompiler();

    private volatile String antiDebugProxyName = null;

    /**
     * Retrieves or generates a String proxy for the given string value.
     * @param value The string literal to obfuscate.
     * @return The dynamically generated class name of the proxy.
     */
    public String getStringProxy(String value) {
        return stringProxies.computeIfAbsent(value, k -> {
            String proxyName = DynamicNameGenerator.generate();
            // Generate Java source code
            String sourceCode = (String) stringProxyGenerator.generate(proxyName, k);
            // Compile it immediately (or defer compilation, depending on memory limits. Here we compile immediately)
            compiler.compile(proxyName, sourceCode);
            return proxyName;
        });
    }

    /**
     * Retrieves or generates a Method proxy optimized for Dex generation.
     * @param methodData The method information.
     * @return The dynamically generated class name of the proxy.
     */
    public String getDexMethodProxy(com.obfuscator.generator.MethodData methodData) {
        String key = "dex_" + methodData.getClassName() + "." + methodData.getMethodName() + ":" + java.util.Arrays.toString(methodData.getParamTypes());
        return methodProxies.computeIfAbsent(key, k -> {
            String proxyName = DynamicNameGenerator.generate();
            String sourceCode = (String) dexMethodProxyGenerator.generate(proxyName, methodData);
            compiler.compile(proxyName, sourceCode);
            return proxyName;
        });
    }

    /**
     * Retrieves or generates a Method proxy.
     * @param methodData The method information.
     * @return The dynamically generated class name of the proxy.
     */
    public String getMethodProxy(com.obfuscator.generator.MethodData methodData) {
        String key = methodData.getClassName() + "." + methodData.getMethodName() + ":" + java.util.Arrays.toString(methodData.getParamTypes());
        return methodProxies.computeIfAbsent(key, k -> {
            String proxyName = DynamicNameGenerator.generate();
            String sourceCode = (String) methodProxyGenerator.generate(proxyName, methodData);
            compiler.compile(proxyName, sourceCode);
            return proxyName;
        });
    }

    /**
     * Retrieves or generates a Class Creation proxy.
     * @param className The class name to instantiate.
     * @return The dynamically generated class name of the proxy.
     */
    public String getClassCreationProxy(String className) {
        return classCreationProxies.computeIfAbsent(className, k -> {
            String proxyName = DynamicNameGenerator.generate();
            String sourceCode = (String) classCreationProxyGenerator.generate(proxyName, k);
            compiler.compile(proxyName, sourceCode);
            return proxyName;
        });
    }

    /**
     * Retrieves or generates a Field proxy.
     * @param fieldData The field information.
     * @return The dynamically generated class name of the proxy.
     */
    public String getFieldProxy(com.obfuscator.generator.FieldData fieldData) {
        String key = fieldData.getClassName() + "." + fieldData.getFieldName();
        return fieldProxies.computeIfAbsent(key, k -> {
            String proxyName = DynamicNameGenerator.generate();
            String sourceCode = (String) fieldProxyGenerator.generate(proxyName, fieldData);
            compiler.compile(proxyName, sourceCode);
            return proxyName;
        });
    }

    /**
     * Retrieves or generates a Control Flow proxy.
     * @param prefix The logic type prefix (e.g., "IF", "FOR").
     * @return The dynamically generated class name of the proxy.
     */
    public String getControlFlowProxy(String prefix) {
        return controlFlowProxies.computeIfAbsent(prefix, k -> {
            String proxyName = DynamicNameGenerator.generate();
            String sourceCode = (String) controlFlowProxyGenerator.generate(proxyName, k);
            compiler.compile(proxyName, sourceCode);
            return proxyName;
        });
    }

    /**
     * Retrieves or generates the AntiDebug proxy. There is only one per application.
     * @return The dynamically generated class name of the AntiDebug proxy.
     */
    public String getAntiDebugProxy() {
        if (antiDebugProxyName == null) {
            synchronized (this) {
                if (antiDebugProxyName == null) {
                    antiDebugProxyName = DynamicNameGenerator.generate();
                    String sourceCode = (String) antiDebugProxyGenerator.generate(antiDebugProxyName, null);
                    compiler.compile(antiDebugProxyName, sourceCode);
                }
            }
        }
        return antiDebugProxyName;
    }

    public Map<String, byte[]> getCompiledProxies() {
        return compiler.getCompiledClasses();
    }
}
