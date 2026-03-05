package com.obfuscator.generator;

/**
 * Generates Field Access proxy classes with dynamic names.
 * <p>
 * Replaces field reads and writes (e.g., int x = obj.field; obj.field = 5;) with
 * dynamic proxy calls using reflection.
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

        boolean useMethodHandlesForGet = Math.random() > 0.5;
        boolean useMethodHandlesForSet = Math.random() > 0.5;

        StringBuilder sb = new StringBuilder();

        String junkCode = "";
        if (Math.random() > 0.3) {
            junkCode = "        int junk = " + (int)(Math.random() * 1000) + ";\n" +
                       "        junk = junk * " + (int)(Math.random() * 50) + ";\n" +
                       "        if (junk < 0) { junk = 0; }\n";
        }

        sb.append("public class ").append(className).append(" {\n");

        // GET
        sb.append("    public static Object get(Object target) throws Exception {\n");
        sb.append(junkCode);
        sb.append("        Class<?> clazz = Class.forName(\"").append(fieldData.getClassName()).append("\");\n");
        if (useMethodHandlesForGet) {
            sb.append("        try {\n");
            sb.append("            java.lang.reflect.Field field = clazz.getDeclaredField(\"").append(fieldData.getFieldName()).append("\");\n");
            sb.append("            field.setAccessible(true);\n");
            sb.append("            java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();\n");
            sb.append("            java.lang.invoke.MethodHandle mh = lookup.unreflectGetter(field);\n");
            sb.append("            if (target == null) {\n");
            sb.append("                return mh.invoke();\n");
            sb.append("            } else {\n");
            sb.append("                return mh.invoke(target);\n");
            sb.append("            }\n");
            sb.append("        } catch (Throwable t) {\n");
            sb.append("            if (t instanceof Exception) throw (Exception) t;\n");
            sb.append("            throw new Exception(t);\n");
            sb.append("        }\n");
        } else {
            sb.append("        java.lang.reflect.Field field = clazz.getDeclaredField(\"").append(fieldData.getFieldName()).append("\");\n");
            sb.append("        field.setAccessible(true);\n");
            sb.append("        return field.get(target);\n");
        }
        sb.append("    }\n\n");

        // SET
        sb.append("    public static void set(Object target, Object value) throws Exception {\n");
        sb.append(junkCode);
        sb.append("        Class<?> clazz = Class.forName(\"").append(fieldData.getClassName()).append("\");\n");
        if (useMethodHandlesForSet) {
            sb.append("        try {\n");
            sb.append("            java.lang.reflect.Field field = clazz.getDeclaredField(\"").append(fieldData.getFieldName()).append("\");\n");
            sb.append("            field.setAccessible(true);\n");
            sb.append("            java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();\n");
            sb.append("            java.lang.invoke.MethodHandle mh = lookup.unreflectSetter(field);\n");
            sb.append("            if (target == null) {\n");
            sb.append("                mh.invoke(value);\n");
            sb.append("            } else {\n");
            sb.append("                mh.invoke(target, value);\n");
            sb.append("            }\n");
            sb.append("        } catch (Throwable t) {\n");
            sb.append("            if (t instanceof Exception) throw (Exception) t;\n");
            sb.append("            throw new Exception(t);\n");
            sb.append("        }\n");
        } else {
            sb.append("        java.lang.reflect.Field field = clazz.getDeclaredField(\"").append(fieldData.getFieldName()).append("\");\n");
            sb.append("        field.setAccessible(true);\n");
            sb.append("        field.set(target, value);\n");
        }
        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating Field Access Proxy: " + className + " for field: " + fieldData.getFieldName());
        return sb.toString();
    }
}
