package com.obfuscator.engine;

import com.obfuscator.core.ObfuscationEngine;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * Obfuscation engine for processing Java bytecode (.class, .jar) using ASM.
 */
public class JarBytecodeEngine implements ObfuscationEngine {

    @Override
    public void process(File input, File output) throws Exception {
        System.out.println("Processing JAR/Class file: " + input.getName());
        ProxyManager proxyManager = new ProxyManager();

        try (JarInputStream jis = new JarInputStream(new FileInputStream(input));
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(output))) {

            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    // It's a class file, let's obfuscate it
                    byte[] classBytes = readAllBytes(jis);

                    ClassReader cr = new ClassReader(classBytes);
                    // Do not pass cr to ClassWriter to avoid copying the original constant pool
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    ObfuscationClassVisitor cv = new ObfuscationClassVisitor(Opcodes.ASM9, cw, proxyManager);

                    cr.accept(cv, ClassReader.EXPAND_FRAMES);

                    byte[] modifiedClassBytes = cw.toByteArray();

                    jos.putNextEntry(new JarEntry(entryName));
                    jos.write(modifiedClassBytes);
                    jos.closeEntry();
                } else {
                    // Non-class file, copy it directly
                    jos.putNextEntry(new JarEntry(entryName));
                    copyStream(jis, jos);
                    jos.closeEntry();
                }
            }

            // After processing all original classes, inject the newly generated proxy classes
            Map<String, byte[]> proxies = proxyManager.getCompiledProxies();
            System.out.println("Injecting " + proxies.size() + " compiled proxy classes into the output JAR.");

            for (Map.Entry<String, byte[]> proxyEntry : proxies.entrySet()) {
                String className = proxyEntry.getKey();
                byte[] classBytes = proxyEntry.getValue();

                // Convert class name to entry path (e.g., com.example.Proxy -> com/example/Proxy.class)
                String classPath = className.replace('.', '/') + ".class";

                jos.putNextEntry(new JarEntry(classPath));
                jos.write(classBytes);
                jos.closeEntry();
            }
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private void copyStream(InputStream is, OutputStream os) throws IOException {
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            os.write(data, 0, nRead);
        }
    }
}
