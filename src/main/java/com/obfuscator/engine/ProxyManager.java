package com.obfuscator.engine;

import com.obfuscator.generator.DynamicNameGenerator;
import com.obfuscator.generator.ProxyGenerator;
import com.obfuscator.generator.StringProxyGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the generation, deduplication, and compilation of proxy classes.
 */
public class ProxyManager {
    // Maps original value to generated proxy class name
    private final Map<String, String> stringProxies = new HashMap<>();
    private final Map<String, String> methodProxies = new HashMap<>();
    private final Map<String, String> classCreationProxies = new HashMap<>();
    private final Map<String, String> fieldProxies = new HashMap<>();
    private final Map<String, String> controlFlowProxies = new HashMap<>();

    private final StringProxyGenerator stringProxyGenerator = new StringProxyGenerator();
    private final com.obfuscator.generator.MethodProxyGenerator methodProxyGenerator = new com.obfuscator.generator.MethodProxyGenerator();
    private final com.obfuscator.generator.ClassCreationProxyGenerator classCreationProxyGenerator = new com.obfuscator.generator.ClassCreationProxyGenerator();
    private final com.obfuscator.generator.FieldProxyGenerator fieldProxyGenerator = new com.obfuscator.generator.FieldProxyGenerator();
    private final com.obfuscator.generator.ControlFlowProxyGenerator controlFlowProxyGenerator = new com.obfuscator.generator.ControlFlowProxyGenerator();
    private final InMemoryCompiler compiler = new InMemoryCompiler();

    /**
     * Retrieves or generates a String proxy for the given string value.
     * @param value The string literal to obfuscate.
     * @return The dynamically generated class name of the proxy.
     */
    public String getStringProxy(String value) {
        if (stringProxies.containsKey(value)) {
            return stringProxies.get(value);
        }

        String proxyName = DynamicNameGenerator.generate();
        stringProxies.put(value, proxyName);

        // Generate Java source code
        String sourceCode = (String) stringProxyGenerator.generate(proxyName, value);

        // Compile it immediately (or defer compilation, depending on memory limits. Here we compile immediately)
        compiler.compile(proxyName, sourceCode);

        return proxyName;
    }

    /**
     * Retrieves or generates a Method proxy.
     * @param methodData The method information.
     * @return The dynamically generated class name of the proxy.
     */
    public String getMethodProxy(com.obfuscator.generator.MethodData methodData) {
        String key = methodData.getClassName() + "." + methodData.getMethodName() + ":" + java.util.Arrays.toString(methodData.getParamTypes());
        if (methodProxies.containsKey(key)) {
            return methodProxies.get(key);
        }

        String proxyName = DynamicNameGenerator.generate();
        methodProxies.put(key, proxyName);

        String sourceCode = (String) methodProxyGenerator.generate(proxyName, methodData);
        compiler.compile(proxyName, sourceCode);

        return proxyName;
    }

    /**
     * Retrieves or generates a Class Creation proxy.
     * @param className The class name to instantiate.
     * @return The dynamically generated class name of the proxy.
     */
    public String getClassCreationProxy(String className) {
        if (classCreationProxies.containsKey(className)) {
            return classCreationProxies.get(className);
        }

        String proxyName = DynamicNameGenerator.generate();
        classCreationProxies.put(className, proxyName);

        String sourceCode = (String) classCreationProxyGenerator.generate(proxyName, className);
        compiler.compile(proxyName, sourceCode);

        return proxyName;
    }

    /**
     * Retrieves or generates a Field proxy.
     * @param fieldData The field information.
     * @return The dynamically generated class name of the proxy.
     */
    public String getFieldProxy(com.obfuscator.generator.FieldData fieldData) {
        String key = fieldData.getClassName() + "." + fieldData.getFieldName();
        if (fieldProxies.containsKey(key)) {
            return fieldProxies.get(key);
        }

        String proxyName = DynamicNameGenerator.generate();
        fieldProxies.put(key, proxyName);

        String sourceCode = (String) fieldProxyGenerator.generate(proxyName, fieldData);
        compiler.compile(proxyName, sourceCode);

        return proxyName;
    }

    /**
     * Retrieves or generates a Control Flow proxy.
     * @param prefix The logic type prefix (e.g., "IF", "FOR").
     * @return The dynamically generated class name of the proxy.
     */
    public String getControlFlowProxy(String prefix) {
        if (controlFlowProxies.containsKey(prefix)) {
            return controlFlowProxies.get(prefix);
        }

        String proxyName = DynamicNameGenerator.generate();
        controlFlowProxies.put(prefix, proxyName);

        String sourceCode = (String) controlFlowProxyGenerator.generate(proxyName, prefix);
        compiler.compile(proxyName, sourceCode);

        return proxyName;
    }

    public Map<String, byte[]> getCompiledProxies() {
        return compiler.getCompiledClasses();
    }
}
