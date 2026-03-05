package com.obfuscator.generator;

/**
 * Generates Method Invocation proxy classes optimized for Dex generation.
 * This proxy does not accept `Object[] args` to avoid register exhaustion and `new-array`
 * instruction complexity in DexEngine. Instead, it generates a method signature that exactly
 * matches the types and count of the intercepted method's parameters.
 */
public class DexMethodProxyGenerator implements ProxyGenerator {

    @Override
    public Object generate(String id, Object data) {
        String className = id;
        if (!(data instanceof MethodData)) {
            throw new IllegalArgumentException("Data must be an instance of MethodData");
        }
        MethodData methodData = (MethodData) data;

        StringBuilder sb = new StringBuilder();

        sb.append("public class ").append(className).append(" {\n");

        String[] paramTypes = methodData.getParamTypes();
        String returnType = methodData.getReturnType();
        if (returnType == null) {
            returnType = "Object"; // Fallback if return type is not provided in MethodData
        }

        String proxyReturnType = returnType.equals("void") ? "void" : returnType;

        // Generate 'invoke' (virtual) method
        sb.append("    public static ").append(proxyReturnType).append(" invoke(Object target");
        for (int i = 0; i < paramTypes.length; i++) {
            sb.append(", ").append(paramTypes[i]).append(" arg").append(i);
        }
        sb.append(") throws Exception {\n");
        sb.append(JunkCodeGenerator.generate());
        sb.append("        Class<?> clazz = Class.forName(\"").append(methodData.getClassName()).append("\");\n");

        sb.append("        Class<?>[] paramClasses = new Class<?>[").append(paramTypes.length).append("];\n");
        for (int i = 0; i < paramTypes.length; i++) {
            sb.append("        paramClasses[").append(i).append("] = ").append(getClassForType(paramTypes[i])).append(";\n");
        }

        sb.append("        Object[] args = new Object[").append(paramTypes.length).append("];\n");
        for (int i = 0; i < paramTypes.length; i++) {
            sb.append("        args[").append(i).append("] = arg").append(i).append(";\n");
        }

        sb.append("        java.lang.reflect.Method method = clazz.getDeclaredMethod(\"").append(methodData.getMethodName()).append("\", paramClasses);\n");
        sb.append("        method.setAccessible(true);\n");
        if (returnType.equals("void")) {
            sb.append("        method.invoke(target, args);\n");
        } else if (isPrimitive(returnType)) {
            sb.append("        return ((").append(getWrapperForType(returnType)).append(") method.invoke(target, args)).").append(returnType).append("Value();\n");
        } else {
            sb.append("        return (").append(returnType).append(") method.invoke(target, args);\n");
        }
        sb.append("    }\n\n");

        // Generate 'invokeStatic' method (no target parameter)
        sb.append("    public static ").append(proxyReturnType).append(" invokeStatic(");
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramTypes[i]).append(" arg").append(i);
        }
        sb.append(") throws Exception {\n");
        sb.append("        Class<?> clazz = Class.forName(\"").append(methodData.getClassName()).append("\");\n");
        sb.append("        Class<?>[] paramClasses = new Class<?>[").append(paramTypes.length).append("];\n");
        for (int i = 0; i < paramTypes.length; i++) {
            sb.append("        paramClasses[").append(i).append("] = ").append(getClassForType(paramTypes[i])).append(";\n");
        }
        sb.append("        Object[] args = new Object[").append(paramTypes.length).append("];\n");
        for (int i = 0; i < paramTypes.length; i++) {
            sb.append("        args[").append(i).append("] = arg").append(i).append(";\n");
        }
        sb.append("        java.lang.reflect.Method method = clazz.getDeclaredMethod(\"").append(methodData.getMethodName()).append("\", paramClasses);\n");
        sb.append("        method.setAccessible(true);\n");
        if (returnType.equals("void")) {
            sb.append("        method.invoke(null, args);\n");
        } else if (isPrimitive(returnType)) {
            sb.append("        return ((").append(getWrapperForType(returnType)).append(") method.invoke(null, args)).").append(returnType).append("Value();\n");
        } else {
            sb.append("        return (").append(returnType).append(") method.invoke(null, args);\n");
        }
        sb.append("    }\n");
        sb.append("}\n");

        System.out.println("Generating Dex Method Invocation Proxy: " + className + " for method: " + methodData.getMethodName());
        return sb.toString();
    }

    private boolean isPrimitive(String type) {
        switch (type) {
            case "int":
            case "boolean":
            case "byte":
            case "short":
            case "long":
            case "float":
            case "double":
            case "char": return true;
            default: return false;
        }
    }

    private String getWrapperForType(String type) {
        switch (type) {
            case "int": return "java.lang.Integer";
            case "boolean": return "java.lang.Boolean";
            case "byte": return "java.lang.Byte";
            case "short": return "java.lang.Short";
            case "long": return "java.lang.Long";
            case "float": return "java.lang.Float";
            case "double": return "java.lang.Double";
            case "char": return "java.lang.Character";
            default: return type;
        }
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
