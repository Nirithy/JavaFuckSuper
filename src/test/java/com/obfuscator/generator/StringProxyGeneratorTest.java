package com.obfuscator.generator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class StringProxyGeneratorTest {

    @Test
    public void testGenerate() {
        StringProxyGenerator generator = new StringProxyGenerator();
        String result = (String) generator.generate("1001", "Hello World");

        assertNotNull(result);
        assertTrue(result.contains("public class S1001"));
        assertTrue(result.contains("public static String get()"));
        assertTrue(result.contains("java.util.Base64.getDecoder().decode"));

        // Ensure "Hello World" is NOT directly in the output source
        assertTrue(!result.contains("\"Hello World\""));
    }
}
