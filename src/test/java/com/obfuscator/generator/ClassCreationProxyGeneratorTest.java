package com.obfuscator.generator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClassCreationProxyGeneratorTest {

    @Test
    public void testGenerate() {
        ClassCreationProxyGenerator generator = new ClassCreationProxyGenerator();
        String dynamicId = "O" + java.util.UUID.randomUUID().toString().replace("-", "");
        String result = (String) generator.generate(dynamicId, "java.util.ArrayList");

        assertNotNull(result);
        assertTrue(result.contains("public class " + dynamicId));
        assertTrue(result.contains("public static Object create() throws Exception"));
        assertTrue(result.contains("Class.forName(\"java.util.ArrayList\").getDeclaredConstructor().newInstance()"));
    }
}
