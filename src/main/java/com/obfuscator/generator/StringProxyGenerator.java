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

        // ThreadLocalRandom is used to guarantee thread safety during concurrent proxy generation
        double rand = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        if (rand < 0.33) {
            // Generate AES decryption logic with dynamic stack-based key derivation
            try {
                javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance("AES");
                keyGen.init(128);
                javax.crypto.SecretKey secretKey = keyGen.generateKey();
                byte[] originalKeyBytes = secretKey.getEncoded();

                // We will obfuscate the key by XORing it with a known mask.
                // The mask will be the hash of this proxy class's name, derived dynamically at runtime
                // by inspecting the stack trace to find the current executing class.
                int proxyHash = className.hashCode();
                byte[] proxyHashBytes = new byte[] {
                    (byte) (proxyHash >>> 24),
                    (byte) (proxyHash >>> 16),
                    (byte) (proxyHash >>> 8),
                    (byte) proxyHash
                };

                byte[] obfuscatedKeyBytes = new byte[originalKeyBytes.length];
                for (int i = 0; i < originalKeyBytes.length; i++) {
                    obfuscatedKeyBytes[i] = (byte) (originalKeyBytes[i] ^ proxyHashBytes[i % 4]);
                }

                String keyBase64 = java.util.Base64.getEncoder().encodeToString(obfuscatedKeyBytes);

                javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES");
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey);
                byte[] encryptedBytes = cipher.doFinal(originalString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String encryptedBase64 = java.util.Base64.getEncoder().encodeToString(encryptedBytes);

                sb.append("        try {\n");

                // Dynamic Key Derivation based on Stack State
                sb.append("            String k = \"").append(keyBase64).append("\";\n");
                sb.append("            byte[] keyBytes = java.util.Base64.getDecoder().decode(k);\n");

                // Inspect stack trace to dynamically find this proxy class
                sb.append("            StackTraceElement[] ste = Thread.currentThread().getStackTrace();\n");
                sb.append("            String proxyClass = \"\";\n");
                sb.append("            for (StackTraceElement e : ste) {\n");
                sb.append("                String cName = e.getClassName();\n");
                // The proxy class name is dynamically generated and won't be a system class
                sb.append("                if (!cName.startsWith(\"java.\") && !cName.startsWith(\"dalvik.\") && !cName.startsWith(\"android.\")) {\n");
                sb.append("                    proxyClass = cName;\n");
                sb.append("                    break;\n");
                sb.append("                }\n");
                sb.append("            }\n");

                sb.append("            int hash = proxyClass.hashCode();\n");
                sb.append("            byte[] hashBytes = new byte[] {\n");
                sb.append("                (byte) (hash >>> 24), (byte) (hash >>> 16), (byte) (hash >>> 8), (byte) hash\n");
                sb.append("            };\n");

                sb.append("            for (int i = 0; i < keyBytes.length; i++) {\n");
                sb.append("                keyBytes[i] = (byte) (keyBytes[i] ^ hashBytes[i % 4]);\n");
                sb.append("            }\n");

                // Introduce dynamic runtime checks to make it slightly harder to extract the key statically
                sb.append("            long t = System.currentTimeMillis();\n");
                sb.append("            if (t < 0 || proxyClass.isEmpty()) {\n");
                sb.append("                for (int i=0; i<keyBytes.length; i++) keyBytes[i] = (byte)(keyBytes[i] ^ 0x42);\n");
                sb.append("            }\n");

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
            byte key = (byte) (java.util.concurrent.ThreadLocalRandom.current().nextInt(254) + 1); // Random byte key 1-255
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
