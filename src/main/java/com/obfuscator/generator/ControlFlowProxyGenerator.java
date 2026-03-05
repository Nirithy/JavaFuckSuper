package com.obfuscator.generator;

/**
 * Generates Control Flow proxy classes with dynamic names.
 * <p>
 * Replaces logical branch instructions with delegate classes. Each distinct branch condition
 * logic is extracted into its own class to break control flow graphs.
 * </p>
 */
public class ControlFlowProxyGenerator implements ProxyGenerator {

    @Override
    public Object generate(String id, Object data) {
        if (!(data instanceof String)) {
            throw new IllegalArgumentException("Data must be a string containing the prefix (IF, FOR, WHILE)");
        }

        String prefix = (String) data; // e.g. "IF" or "FOR" or "WHILE"
        String className = id;

        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");

        sb.append("    public static boolean eval(String op, int a, int b) {\n");
        sb.append(JunkCodeGenerator.generate());
        sb.append("        switch (op) {\n");
        sb.append("            case \"==\": return a == b;\n");
        sb.append("            case \"!=\": return a != b;\n");
        sb.append("            case \"<\": return a < b;\n");
        sb.append("            case \">\": return a > b;\n");
        sb.append("            case \"<=\": return a <= b;\n");
        sb.append("            case \">=\": return a >= b;\n");
        sb.append("            default: throw new IllegalArgumentException(\"Unknown operator: \" + op);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    public static boolean eval(String op, Object a, Object b) {\n");
        sb.append(JunkCodeGenerator.generate());
        sb.append("        switch (op) {\n");
        sb.append("            case \"==\": return a == b;\n");
        sb.append("            case \"!=\": return a != b;\n");
        sb.append("            default: throw new IllegalArgumentException(\"Unknown operator: \" + op);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating Control Flow Proxy: " + className);
        return sb.toString();
    }
}
