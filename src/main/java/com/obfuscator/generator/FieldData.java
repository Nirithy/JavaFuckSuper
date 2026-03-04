package com.obfuscator.generator;

public class FieldData {
    private String className;
    private String fieldName;

    public FieldData(String className, String fieldName) {
        this.className = className;
        this.fieldName = fieldName;
    }

    public String getClassName() {
        return className;
    }

    public String getFieldName() {
        return fieldName;
    }
}
