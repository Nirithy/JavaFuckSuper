package com.obfuscator.generator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FieldProxyGeneratorTest {

    @Test
    public void testGenerate() {
        FieldProxyGenerator generator = new FieldProxyGenerator();
        FieldData data = new FieldData("java.awt.Point", "x");
        String dynamicId = DynamicNameGenerator.generate();
        String result = (String) generator.generate(dynamicId, data);

        assertNotNull(result);
        assertTrue(result.contains("public class " + dynamicId));
        assertTrue(result.contains("public static Object get(Object target)"));
        assertTrue(result.contains("public static void set(Object target, Object value)"));
        assertTrue(result.contains("Class.forName(\"java.awt.Point\")"));
        assertTrue(result.contains("getDeclaredField(\"x\")"));
        assertTrue(result.contains("field.get(target)"));
        assertTrue(result.contains("field.set(target, value)"));
    }
}
