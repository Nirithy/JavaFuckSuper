package com.obfuscator.engine;

/**
 * Interface to abstract the process of compiling Java bytecode (.class) to Dalvik bytecode (.dex).
 */
public interface DexCompiler {

    /**
     * Compiles a given Java class byte array into a DEX format byte array.
     *
     * @param classBytes The raw bytes of the compiled .class file.
     * @return The raw bytes of the resulting .dex file.
     */
    default byte[] compileClassToDex(byte[] classBytes) {
        throw new UnsupportedOperationException("TODO: Implement .class to .dex compilation");
    }
}
