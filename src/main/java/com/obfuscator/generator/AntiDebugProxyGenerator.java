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
        sb.append("        try {\n");
        sb.append("            Class.forName(\"com.saurik.substrate.MS$2\");\n");
        sb.append("            detected = true;\n");
        sb.append("        } catch (Exception t) {\n");
        sb.append("            // Ignored\n");
        sb.append("        }\n");
        sb.append("        try {\n");
        sb.append("            Class<?> buildClass = Class.forName(\"android.os.Build\");\n");
        sb.append("            String model = (String) buildClass.getField(\"MODEL\").get(null);\n");
        sb.append("            String hardware = (String) buildClass.getField(\"HARDWARE\").get(null);\n");
        sb.append("            if (model != null && model.toLowerCase().contains(\"emulator\")) {\n");
        sb.append("                detected = true;\n");
        sb.append("            }\n");
        sb.append("            if (hardware != null && hardware.toLowerCase().contains(\"qemu\")) {\n");
        sb.append("                detected = true;\n");
        sb.append("            }\n");
        sb.append("        } catch (Exception t) {\n");
        sb.append("            // Ignored\n");
        sb.append("        }\n");
        sb.append("        String[] suPaths = {\n");
        sb.append("            \"/system/bin/su\", \"/system/xbin/su\", \"/sbin/su\", \"/data/local/xbin/su\",\n");
        sb.append("            \"/data/local/bin/su\", \"/system/sd/xbin/su\", \"/system/bin/failsafe/su\",\n");
        sb.append("            \"/data/local/su\", \"/su/bin/su\"\n");
        sb.append("        };\n");
        sb.append("        for (String path : suPaths) {\n");
        sb.append("            if (new java.io.File(path).exists()) {\n");
        sb.append("                detected = true;\n");
        sb.append("                break;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        try {\n");
        sb.append("            java.net.Socket socket = new java.net.Socket(\"127.0.0.1\", 27042);\n");
        sb.append("            socket.close();\n");
        sb.append("            detected = true;\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            // Ignored\n");
        sb.append("        }\n");
        sb.append("        try {\n");
        sb.append("            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(\"/proc/self/maps\"));\n");
        sb.append("            String line;\n");
        sb.append("            while ((line = reader.readLine()) != null) {\n");
        sb.append("                if (line.contains(\"frida-agent.so\")) {\n");
        sb.append("                    detected = true;\n");
        sb.append("                    break;\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("            reader.close();\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            // Ignored\n");
        sb.append("        }\n");
        sb.append("        if (detected) {\n");
        sb.append("            Runtime.getRuntime().halt(0);\n");
        sb.append("            throw new VirtualMachineError(\"Tampering or Emulator detected\") {};\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating Anti-Debug Proxy: " + className);
        return sb.toString();
    }
}
