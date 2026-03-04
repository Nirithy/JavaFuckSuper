package com.obfuscator.generator;

public class MethodData {
    private String className;
    private String methodName;
    private String[] paramTypes;

    public MethodData(String className, String methodName, String[] paramTypes) {
        this.className = className;
        this.methodName = methodName;
        this.paramTypes = paramTypes;
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
}
