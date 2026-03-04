package com.obfuscator.generator;

/**
 * Interface for generating specific dispersed proxy classes (S, C, M, F, IF/FOR).
 * Each implementation handles a single extreme obfuscation concern.
 */
public interface ProxyGenerator {

    /**
     * Generates a proxy class based on the input item (e.g., a String, a method signature, a class name).
     *
     * @param inputId   The unique ID generated for this item (e.g., "1001" for "S1001").
     * @param inputData The actual data to be obfuscated (e.g., the string "Hello World", the class name "java.lang.String").
     * @return A generated proxy class structure (AST or Bytecode array, depending on the engine).
     */
    Object generate(String inputId, Object inputData);
}
