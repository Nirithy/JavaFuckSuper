package com.obfuscator.generator;

/**
 * Generates Class Creation proxy classes (C + ID).
 * <p>
 * C2001.java
 * Replaces 'new Object()' with 'C2001.create()'.
 * The proxy uses reflection to instantiate the class and return the instance.
 * </p>
 */
public class ClassCreationProxyGenerator implements ProxyGenerator {

    @Override
    public Object generate(String id, Object data) {
        String className = "C" + id;
        String targetClass = (String) data; // e.g. "java.util.ArrayList"

        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("    public static Object create() throws Exception {\n");
        sb.append("        return Class.forName(\"").append(targetClass).append("\").getDeclaredConstructor().newInstance();\n");
        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating Class Creation Proxy: " + className + " for class: " + targetClass);
        return sb.toString();
    }
}
