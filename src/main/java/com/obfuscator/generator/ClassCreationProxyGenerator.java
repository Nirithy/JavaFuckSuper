package com.obfuscator.generator;

/**
 * Generates Class Creation proxy classes with dynamic names.
 * <p>
 * Replaces 'new Object()' with a dynamic proxy call.
 * The proxy uses reflection to instantiate the class and return the instance.
 * </p>
 */
public class ClassCreationProxyGenerator implements ProxyGenerator {

    @Override
    public Object generate(String id, Object data) {
        String className = id;
        String targetClass = (String) data; // e.g. "java.util.ArrayList"

        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("    public static Object create(Object[] args) throws Exception {\n");
        sb.append("        Class<?> clazz = Class.forName(\"").append(targetClass).append("\");\n");
        sb.append("        java.lang.reflect.Constructor<?>[] constructors = clazz.getDeclaredConstructors();\n");
        sb.append("        for (java.lang.reflect.Constructor<?> c : constructors) {\n");
        sb.append("            if (c.getParameterCount() == (args == null ? 0 : args.length)) {\n");
        sb.append("                boolean match = true;\n");
        sb.append("                Class<?>[] paramTypes = c.getParameterTypes();\n");
        sb.append("                for (int i = 0; i < paramTypes.length; i++) {\n");
        sb.append("                    if (args[i] != null && !paramTypes[i].isAssignableFrom(args[i].getClass())) {\n");
        sb.append("                        if (paramTypes[i].isPrimitive()) {\n");
        sb.append("                            // Basic primitive check logic\n");
        sb.append("                            if (paramTypes[i] == int.class && args[i] instanceof Integer) continue;\n");
        sb.append("                            if (paramTypes[i] == boolean.class && args[i] instanceof Boolean) continue;\n");
        sb.append("                            if (paramTypes[i] == byte.class && args[i] instanceof Byte) continue;\n");
        sb.append("                            if (paramTypes[i] == short.class && args[i] instanceof Short) continue;\n");
        sb.append("                            if (paramTypes[i] == long.class && args[i] instanceof Long) continue;\n");
        sb.append("                            if (paramTypes[i] == float.class && args[i] instanceof Float) continue;\n");
        sb.append("                            if (paramTypes[i] == double.class && args[i] instanceof Double) continue;\n");
        sb.append("                            if (paramTypes[i] == char.class && args[i] instanceof Character) continue;\n");
        sb.append("                        }\n");
        sb.append("                        match = false;\n");
        sb.append("                        break;\n");
        sb.append("                    }\n");
        sb.append("                }\n");
        sb.append("                if (match) {\n");
        sb.append("                    c.setAccessible(true);\n");
        sb.append("                    return c.newInstance(args);\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        throw new NoSuchMethodException(\"No matching constructor found\");\n");
        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating Class Creation Proxy: " + className + " for class: " + targetClass);
        return sb.toString();
    }
}
