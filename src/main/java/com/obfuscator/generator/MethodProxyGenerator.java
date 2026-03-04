package com.obfuscator.generator;

/**
 * Generates Method Invocation proxy classes with dynamic names.
 * <p>
 * Replaces direct method calls (e.g., obj.doSomething(arg1)) with dynamic proxy calls.
 * Uses reflection to locate and invoke the method on the target object.
 * </p>
 */
public class MethodProxyGenerator implements ProxyGenerator {

    @Override
    public Object generate(String id, Object data) {
        String className = id;
        if (!(data instanceof MethodData)) {
            throw new IllegalArgumentException("Data must be an instance of MethodData");
        }
        MethodData methodData = (MethodData) data;

        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("    public static Object invoke(Object target, Object[] args) throws Exception {\n");
        sb.append("        Class<?> clazz = Class.forName(\"").append(methodData.getClassName()).append("\");\n");

        String[] paramTypes = methodData.getParamTypes();
        sb.append("        Class<?>[] paramClasses = new Class<?>[").append(paramTypes.length).append("];\n");
        for (int i = 0; i < paramTypes.length; i++) {
            sb.append("        paramClasses[").append(i).append("] = ").append(getClassForType(paramTypes[i])).append(";\n");
        }

        sb.append("        java.lang.reflect.Method method = clazz.getDeclaredMethod(\"").append(methodData.getMethodName()).append("\", paramClasses);\n");
        sb.append("        method.setAccessible(true);\n");
        sb.append("        return method.invoke(target, args);\n");
        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating Method Invocation Proxy: " + className + " for method: " + methodData.getMethodName());
        return sb.toString();
    }

    private String getClassForType(String type) {
        switch (type) {
            case "int": return "int.class";
            case "boolean": return "boolean.class";
            case "byte": return "byte.class";
            case "short": return "short.class";
            case "long": return "long.class";
            case "float": return "float.class";
            case "double": return "double.class";
            case "char": return "char.class";
            default: return "Class.forName(\"" + type + "\")";
        }
    }
}
