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

    private final StringProxyGenerator stringProxyGenerator = new StringProxyGenerator();
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
     * Gets all compiled proxy classes.
     * @return A map of class names to their compiled bytecode.
     */
    public Map<String, byte[]> getCompiledProxies() {
        return compiler.getCompiledClasses();
    }
}
