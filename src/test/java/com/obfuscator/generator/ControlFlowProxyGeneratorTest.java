package com.obfuscator.generator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ControlFlowProxyGeneratorTest {

    @Test
    public void testGenerate() {
        ControlFlowProxyGenerator generator = new ControlFlowProxyGenerator();
        String dynamicId = DynamicNameGenerator.generate();
        String result = (String) generator.generate(dynamicId, "IF");

        assertNotNull(result);
        assertTrue(result.contains("public class " + dynamicId));
        assertTrue(result.contains("public static boolean eval(String op, int a, int b)"));
        assertTrue(result.contains("public static boolean eval(String op, Object a, Object b)"));
        assertTrue(result.contains("case \"==\": return a == b;"));
        assertTrue(result.contains("case \"<\": return a < b;"));
    }
}
