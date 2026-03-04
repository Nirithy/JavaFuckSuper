package com.obfuscator.generator;

/**
 * Generates Field Access proxy classes (F + ID).
 * <p>
 * F4001.java
 * Replaces field reads and writes (e.g., int x = obj.field; obj.field = 5;) with
 * F4001.get(obj) and F4001.set(obj, 5) using reflection.
 * </p>
 */
public class FieldProxyGenerator implements ProxyGenerator {

    @Override
    public Object generate(String id, Object data) {
        String className = id;

        if (!(data instanceof FieldData)) {
            throw new IllegalArgumentException("Data must be an instance of FieldData");
        }

        FieldData fieldData = (FieldData) data;

        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("    public static Object get(Object target) throws Exception {\n");
        sb.append("        Class<?> clazz = Class.forName(\"").append(fieldData.getClassName()).append("\");\n");
        sb.append("        java.lang.reflect.Field field = clazz.getDeclaredField(\"").append(fieldData.getFieldName()).append("\");\n");
        sb.append("        field.setAccessible(true);\n");
        sb.append("        return field.get(target);\n");
        sb.append("    }\n\n");

        sb.append("    public static void set(Object target, Object value) throws Exception {\n");
        sb.append("        Class<?> clazz = Class.forName(\"").append(fieldData.getClassName()).append("\");\n");
        sb.append("        java.lang.reflect.Field field = clazz.getDeclaredField(\"").append(fieldData.getFieldName()).append("\");\n");
        sb.append("        field.setAccessible(true);\n");
        sb.append("        field.set(target, value);\n");
        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating Field Access Proxy: " + className + " for field: " + fieldData.getFieldName());
        return sb.toString();
    }
}
