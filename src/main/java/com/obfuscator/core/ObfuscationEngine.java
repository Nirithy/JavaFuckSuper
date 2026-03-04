package com.obfuscator.core;

import java.io.File;
import java.io.IOException;

/**
 * Core interface for obfuscation engines dealing with different file formats.
 */
public interface ObfuscationEngine {

    /**
     * Processes the input file and produces the obfuscated output file.
     *
     * @param input  The input file (.java, .jar, .dex).
     * @param output The output file.
     * @throws IOException If an I/O error occurs during processing.
     */
    void process(File input, File output) throws Exception;
}
