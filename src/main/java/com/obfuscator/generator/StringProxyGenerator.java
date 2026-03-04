package com.obfuscator.generator;

/**
 * Generates String proxy classes with dynamic names.
 * <p>
 * Every extracted string is converted into a separate class containing only a static method
 * that decrypts and returns the original string. The original caller uses the dynamic class method instead of "String".
 * </p>
 */
public class StringProxyGenerator implements ProxyGenerator {

    @Override
    public Object generate(String id, Object data) {
        String className = id;
        String originalString = (String) data;

        // Base64 encode the string to obfuscate it
        String encodedString = java.util.Base64.getEncoder().encodeToString(originalString.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("    public static String get() {\n");
        sb.append("        String encoded = \"").append(encodedString).append("\";\n");
        sb.append("        byte[] decoded = java.util.Base64.getDecoder().decode(encoded);\n");
        sb.append("        return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);\n");
        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating String Proxy: " + className + " for string: " + originalString);
        return sb.toString();
    }
}
