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
        String dict = DICTIONARIES[java.util.concurrent.ThreadLocalRandom.current().nextInt(DICTIONARIES.length)];

        StringBuilder name = new StringBuilder();

        // Ensure it starts with a letter (Java identifier requirement)
        char startChar;
        do {
            startChar = dict.charAt(java.util.concurrent.ThreadLocalRandom.current().nextInt(dict.length()));
        } while (Character.isDigit(startChar));

        name.append(startChar);

        String uuidStr = UUID.randomUUID().toString().replace("-", "");
        java.math.BigInteger num = new java.math.BigInteger(uuidStr, 16);
        int base = dict.length();

        while (num.compareTo(java.math.BigInteger.ZERO) > 0) {
            java.math.BigInteger[] divrem = num.divideAndRemainder(java.math.BigInteger.valueOf(base));
            name.append(dict.charAt(divrem[1].intValue()));
            num = divrem[0];
        }

        return name.toString();
    }
}
