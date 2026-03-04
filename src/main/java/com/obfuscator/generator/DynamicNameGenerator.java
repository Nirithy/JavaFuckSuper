package com.obfuscator.generator;

import java.util.UUID;

/**
 * Utility for generating completely dynamic, valid Java identifiers.
 * This avoids any predictable naming patterns or hardcoded prefixes.
 */
public class DynamicNameGenerator {

    /**
     * Generates a random valid Java class name.
     * Ensures it starts with a letter and contains only alphanumeric characters.
     *
     * @return A dynamically generated class name.
     */
    public static String generate() {
        // A valid Java identifier must start with a letter (or _/$)
        // We pick a random letter A-Z or a-z to start
        char startChar = (char) ('A' + (Math.random() * 26));
        if (Math.random() > 0.5) {
             startChar = Character.toLowerCase(startChar);
        }

        String uuidPart = UUID.randomUUID().toString().replace("-", "");
        return startChar + uuidPart;
    }
}
