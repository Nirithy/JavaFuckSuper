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

        double rand = Math.random();
        if (rand < 0.33) {
            // Generate AES decryption logic
            try {
                javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance("AES");
                keyGen.init(128);
                javax.crypto.SecretKey secretKey = keyGen.generateKey();
                byte[] keyBytes = secretKey.getEncoded();
                String keyBase64 = java.util.Base64.getEncoder().encodeToString(keyBytes);

                javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES");
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey);
                byte[] encryptedBytes = cipher.doFinal(originalString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String encryptedBase64 = java.util.Base64.getEncoder().encodeToString(encryptedBytes);

                sb.append("        try {\n");
                sb.append("            byte[] keyBytes = java.util.Base64.getDecoder().decode(\"").append(keyBase64).append("\");\n");
                sb.append("            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, \"AES\");\n");
                sb.append("            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(\"AES\");\n");
                sb.append("            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey);\n");
                sb.append("            byte[] encryptedBytes = java.util.Base64.getDecoder().decode(\"").append(encryptedBase64).append("\");\n");
                sb.append("            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);\n");
                sb.append("            return new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);\n");
                sb.append("        } catch (Exception e) {\n");
                sb.append("            return null;\n");
                sb.append("        }\n");
            } catch (Exception e) {
                // Fallback to Base64 if AES fails
                String encodedString = java.util.Base64.getEncoder().encodeToString(originalString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                sb.append("        String encoded = \"").append(encodedString).append("\";\n");
                sb.append("        byte[] decoded = java.util.Base64.getDecoder().decode(encoded);\n");
                sb.append("        return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);\n");
            }
        } else if (rand < 0.66) {
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
