package com.obfuscator.engine;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM ClassVisitor that redirects method visitation to ObfuscationMethodVisitor.
 */
public class ObfuscationClassVisitor extends ClassVisitor {

    private final ProxyManager proxyManager;

    public ObfuscationClassVisitor(int api, ClassVisitor classVisitor, ProxyManager proxyManager) {
        super(api, classVisitor);
        this.proxyManager = proxyManager;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new ObfuscationMethodVisitor(api, mv, proxyManager);
    }
}
