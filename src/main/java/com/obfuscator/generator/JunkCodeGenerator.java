package com.obfuscator.generator;

import java.util.Random;

public class JunkCodeGenerator {
    private static final Random random = new Random();

    public static String generate() {
        if (random.nextDouble() > 0.6) {
            return ""; // 40% chance of generating nothing
        }

        StringBuilder sb = new StringBuilder();

        int numJunks = random.nextInt(3) + 1;
        for (int i = 0; i < numJunks; i++) {
            String varName = generateVarName();
            int randVal = random.nextInt(1000);

            int junkType = random.nextInt(3);
            if (junkType == 0) {
                sb.append(String.format("        int %s = %d;\n", varName, randVal));
                sb.append(String.format("        %s = %s * %d;\n", varName, varName, random.nextInt(50)));
                sb.append(String.format("        if (%s < 0) { %s = 0; }\n", varName, varName));
            } else if (junkType == 1) {
                sb.append(String.format("        String %s = \"\" + %d;\n", varName, randVal));
                sb.append(String.format("        if (%s.length() > 10) { %s = \"\"; }\n", varName, varName));
            } else if (junkType == 2) {
                sb.append(String.format("        long %s = System.currentTimeMillis();\n", varName));
                sb.append(String.format("        %s += %d;\n", varName, randVal));
            }
        }
        return sb.toString();
    }

    private static String generateVarName() {
        char c1 = (char) ('a' + random.nextInt(26));
        char c2 = (char) ('a' + random.nextInt(26));
        char c3 = (char) ('a' + random.nextInt(26));
        return "" + c1 + c2 + c3 + random.nextInt(100);
    }
}
