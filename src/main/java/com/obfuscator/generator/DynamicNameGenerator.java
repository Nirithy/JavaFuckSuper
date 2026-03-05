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
    private static final String[] DICTIONARIES = {
        "O0", // O and 0
        "Il1", // I, l, and 1
        "Oo" // O and o
    };

    /**
     * Generates a random valid Java class name using an obfuscation dictionary.
     * Ensures it starts with a letter and contains only alphanumeric characters.
     *
     * @return A dynamically generated class name.
     */
    public static String generate() {
        // Pick a random dictionary
        String dict = DICTIONARIES[(int) (Math.random() * DICTIONARIES.length)];

        StringBuilder name = new StringBuilder();

        // Ensure it starts with a letter (Java identifier requirement)
        char startChar;
        do {
            startChar = dict.charAt((int) (Math.random() * dict.length()));
        } while (Character.isDigit(startChar));

        name.append(startChar);

        // Generate a random length between 10 and 20 characters
        int length = 10 + (int) (Math.random() * 11);
        for (int i = 0; i < length; i++) {
            name.append(dict.charAt((int) (Math.random() * dict.length())));
        }

        return name.toString();
    }
}
