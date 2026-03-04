package com.obfuscator.generator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClassCreationProxyGeneratorTest {

    @Test
    public void testGenerate() {
        ClassCreationProxyGenerator generator = new ClassCreationProxyGenerator();
        String result = (String) generator.generate("2001", "java.util.ArrayList");

        assertNotNull(result);
        assertTrue(result.contains("public class C2001"));
        assertTrue(result.contains("public static Object create() throws Exception"));
        assertTrue(result.contains("Class.forName(\"java.util.ArrayList\").getDeclaredConstructor().newInstance()"));
    }
}
