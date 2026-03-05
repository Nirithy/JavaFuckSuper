package com.obfuscator.generator;

public class MethodData {
    private String className;
    private String methodName;
    private String[] paramTypes;
    private String returnType;

    public MethodData(String className, String methodName, String[] paramTypes) {
        this.className = className;
        this.methodName = methodName;
        this.paramTypes = paramTypes;
        this.returnType = "Object"; // Default
    }

    public MethodData(String className, String methodName, String[] paramTypes, String returnType) {
        this.className = className;
        this.methodName = methodName;
        this.paramTypes = paramTypes;
        this.returnType = returnType;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String[] getParamTypes() {
        return paramTypes;
    }

    public String getReturnType() {
        return returnType;
    }
}
