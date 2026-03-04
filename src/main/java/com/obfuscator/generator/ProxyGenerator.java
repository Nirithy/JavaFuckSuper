package com.obfuscator.generator;

/**
 * Interface for generating specific dispersed proxy classes with dynamic names.
 * Each implementation handles a single extreme obfuscation concern.
 */
public interface ProxyGenerator {

    /**
     * Generates a proxy class based on the input item (e.g., a String, a method signature, a class name).
     *
     * @param inputId   The unique dynamic ID generated for this item.
     * @param inputData The actual data to be obfuscated (e.g., the string "Hello World", the class name "java.lang.String").
     * @return A generated proxy class structure (AST or Bytecode array, depending on the engine).
     */
    Object generate(String inputId, Object inputData);
}
