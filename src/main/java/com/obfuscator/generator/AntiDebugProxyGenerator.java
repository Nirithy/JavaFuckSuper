package com.obfuscator.generator;

/**
 * Generates an Anti-Debugging Proxy class.
 * This class contains static methods that check for debuggers and reverse engineering environments.
 */
public class AntiDebugProxyGenerator implements ProxyGenerator {

    @Override
    public Object generate(String id, Object data) {
        String className = id;

        StringBuilder sb = new StringBuilder();
        int lastDot = className.lastIndexOf('.');
        if (lastDot != -1) {
            sb.append("package ").append(className.substring(0, lastDot)).append(";\n\n");
            sb.append("public class ").append(className.substring(lastDot + 1)).append(" {\n");
        } else {
            sb.append("public class ").append(className).append(" {\n");
        }

        sb.append("    public static void check() {\n");
        sb.append(JunkCodeGenerator.generate());
        sb.append("        boolean detected = false;\n");
        sb.append("        try {\n");
        sb.append("            Class<?> debugClass = Class.forName(\"android.os.Debug\");\n");
        sb.append("            java.lang.reflect.Method isDebuggerConnected = debugClass.getMethod(\"isDebuggerConnected\");\n");
        sb.append("            detected = (boolean) isDebuggerConnected.invoke(null);\n");
        sb.append("        } catch (Exception t) {\n");
        sb.append("            // Ignored\n");
        sb.append("        }\n");
        sb.append("        if (detected) {\n");
        sb.append("            throw new RuntimeException(\"Debugger detected\");\n");
        sb.append("        }\n");

        sb.append("        try {\n");
        sb.append("            Class.forName(\"de.robv.android.xposed.XposedBridge\");\n");
        sb.append("            detected = true;\n");
        sb.append("        } catch (Exception t) {\n");
        sb.append("            // Ignored\n");
        sb.append("        }\n");
        sb.append("        if (detected) {\n");
        sb.append("            throw new RuntimeException(\"Xposed detected\");\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating Anti-Debug Proxy: " + className);
        return sb.toString();
    }
}
