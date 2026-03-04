package com.obfuscator;

import com.obfuscator.engine.DexEngine;
import com.obfuscator.engine.JarBytecodeEngine;
import com.obfuscator.core.ObfuscationEngine;

import java.io.File;

/**
 * Entry point for Extreme Anti-Reverse Engineering Obfuscator.
 * Determines the file type and routes it to the correct processing engine.
 */
public class Obfuscator {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar extreme-obfuscator.jar <input_file> <output_file>");
            System.exit(1);
        }

        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);

        if (!inputFile.exists()) {
            System.err.println("Input file does not exist: " + inputFile.getAbsolutePath());
            System.exit(1);
        }

        String fileName = inputFile.getName().toLowerCase();
        ObfuscationEngine engine = null;

        if (fileName.endsWith(".jar") || fileName.endsWith(".class")) {
            engine = new JarBytecodeEngine();
        } else if (fileName.endsWith(".dex") || fileName.endsWith(".apk")) {
            engine = new DexEngine();
        } else {
            System.err.println("Unsupported file format. Supported formats: .jar, .class, .dex, .apk");
            System.exit(1);
        }

        try {
            System.out.println("Starting obfuscation on: " + inputFile.getName());
            engine.process(inputFile, outputFile);
            System.out.println("Obfuscation completed successfully. Output saved to: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Obfuscation failed.");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
