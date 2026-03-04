package com.obfuscator.engine;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM MethodVisitor that intercepts string literals and rewrites them to proxy calls.
 */
public class ObfuscationMethodVisitor extends MethodVisitor {

    private final ProxyManager proxyManager;

    public ObfuscationMethodVisitor(int api, MethodVisitor methodVisitor, ProxyManager proxyManager) {
        super(api, methodVisitor);
        this.proxyManager = proxyManager;
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (value instanceof String) {
            String originalString = (String) value;
            // Get or generate a dynamic proxy for this string
            String proxyClassName = proxyManager.getStringProxy(originalString);

            // Replace LDC "string" with INVOKESTATIC ProxyClass.get()
            // Note: ASM expects the internal name (e.g. com/example/ProxyClass)
            String internalName = proxyClassName.replace('.', '/');
            super.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "get", "()Ljava/lang/String;", false);
        } else {
            // Keep other LDC instructions (int, float, class constants)
            super.visitLdcInsn(value);
        }
    }
}
