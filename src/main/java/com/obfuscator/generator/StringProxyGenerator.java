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

        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("    public static String get() {\n");
        sb.append(JunkCodeGenerator.generate());

        if (Math.random() > 0.5) {
            // Generate Base64 decryption logic
            String encodedString = java.util.Base64.getEncoder().encodeToString(originalString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            sb.append("        String encoded = \"").append(encodedString).append("\";\n");
            sb.append("        byte[] decoded = java.util.Base64.getDecoder().decode(encoded);\n");
            sb.append("        return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);\n");
        } else {
            // Generate XOR decryption logic
            byte key = (byte) (Math.random() * 254 + 1); // Random byte key 1-255
            byte[] bytes = originalString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder byteString = new StringBuilder();
            byteString.append("new byte[]{");
            for (int i = 0; i < bytes.length; i++) {
                byteString.append((byte) (bytes[i] ^ key));
                if (i < bytes.length - 1) byteString.append(",");
            }
            byteString.append("}");

            sb.append("        byte[] data = ").append(byteString.toString()).append(";\n");
            sb.append("        byte key = (byte) ").append(key).append(";\n");
            sb.append("        for (int i = 0; i < data.length; i++) {\n");
            sb.append("            data[i] = (byte) (data[i] ^ key);\n");
            sb.append("        }\n");
            sb.append("        return new String(data, java.nio.charset.StandardCharsets.UTF_8);\n");
        }

        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating String Proxy: " + className + " for string: " + originalString);
        return sb.toString();
    }
}
