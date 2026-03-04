package com.obfuscator.generator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MethodProxyGeneratorTest {

    @Test
    public void testGenerate() {
        MethodProxyGenerator generator = new MethodProxyGenerator();
        MethodData data = new MethodData("java.lang.String", "substring", new String[]{"int", "int"});
        String dynamicId = "O" + java.util.UUID.randomUUID().toString().replace("-", "");
        String result = (String) generator.generate(dynamicId, data);

        assertNotNull(result);
        assertTrue(result.contains("public class " + dynamicId));
        assertTrue(result.contains("public static Object invoke(Object target, Object[] args)"));
        assertTrue(result.contains("Class.forName(\"java.lang.String\")"));
        assertTrue(result.contains("getDeclaredMethod(\"substring\", paramClasses)"));
        assertTrue(result.contains("paramClasses[0] = int.class"));
        assertTrue(result.contains("paramClasses[1] = int.class"));
    }
}
