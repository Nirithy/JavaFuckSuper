package com.obfuscator.engine;

import com.obfuscator.core.ObfuscationEngine;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21c;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import com.obfuscator.generator.FieldData;
import com.obfuscator.generator.MethodData;
import org.jf.dexlib2.rewriter.DexRewriter;
import org.jf.dexlib2.rewriter.InstructionRewriter;
import org.jf.dexlib2.rewriter.Rewriter;
import org.jf.dexlib2.rewriter.RewriterModule;
import org.jf.dexlib2.rewriter.Rewriters;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

/**
 * Obfuscation engine for processing Android Dex files (.dex, .apk) using Dexlib2/Smali.
 */
public class DexEngine implements ObfuscationEngine {

    @Override
    public void process(File input, File output) throws Exception {
        System.out.println("Processing DEX file: " + input.getName());
        ProxyManager proxyManager = new ProxyManager();

        // 1. Load the original dex file
        // We use the default opcodes for reading, which usually matches the dex file's api level
        DexFile dexFile = DexFileFactory.loadDexFile(input, Opcodes.getDefault());

        // 2. Setup DexRewriter to intercept and rewrite instructions using MutableMethodImplementation to handle offsets correctly
        DexRewriter methodRewriter = new DexRewriter(new RewriterModule() {
            @Override
            public Rewriter<org.jf.dexlib2.iface.MethodImplementation> getMethodImplementationRewriter(Rewriters rewriters) {
                return new org.jf.dexlib2.rewriter.MethodImplementationRewriter(rewriters) {
                    @Override
                    public org.jf.dexlib2.iface.MethodImplementation rewrite(org.jf.dexlib2.iface.MethodImplementation methodImplementation) {
                        if (methodImplementation == null) return null;

                        org.jf.dexlib2.builder.MutableMethodImplementation mutableImpl = new org.jf.dexlib2.builder.MutableMethodImplementation(methodImplementation);
                        boolean changed = false;

                        List<org.jf.dexlib2.builder.BuilderInstruction> toReplace = new ArrayList<>();
                        List<List<org.jf.dexlib2.builder.BuilderInstruction>> replacements = new ArrayList<>();

                        for (org.jf.dexlib2.builder.BuilderInstruction instruction : mutableImpl.getInstructions()) {
                            if (instruction instanceof org.jf.dexlib2.builder.instruction.BuilderInstruction21c) {
                                org.jf.dexlib2.builder.instruction.BuilderInstruction21c instr21c = (org.jf.dexlib2.builder.instruction.BuilderInstruction21c) instruction;
                                if (instr21c.getOpcode() == org.jf.dexlib2.Opcode.CONST_STRING || instr21c.getOpcode() == org.jf.dexlib2.Opcode.CONST_STRING_JUMBO) {
                                    if (instr21c.getReference() instanceof StringReference) {
                                        StringReference stringRef = (StringReference) instr21c.getReference();
                                        String originalString = stringRef.getString();

                                        String proxyClassName = proxyManager.getStringProxy(originalString);
                                        String internalProxyName = "L" + proxyClassName.replace('.', '/') + ";";

                                        // invoke-static {}, proxyClassName->get()Ljava/lang/String;
                                        org.jf.dexlib2.builder.instruction.BuilderInstruction35c invokeInstr =
                                                new org.jf.dexlib2.builder.instruction.BuilderInstruction35c(
                                                        org.jf.dexlib2.Opcode.INVOKE_STATIC,
                                                        0, // register count
                                                        0, 0, 0, 0, 0,
                                                        new ImmutableMethodReference(internalProxyName, "get", java.util.Collections.<String>emptyList(), "Ljava/lang/String;")
                                                );

                                        // move-result-object vX
                                        org.jf.dexlib2.builder.instruction.BuilderInstruction11x moveResultInstr =
                                                new org.jf.dexlib2.builder.instruction.BuilderInstruction11x(
                                                        org.jf.dexlib2.Opcode.MOVE_RESULT_OBJECT,
                                                        instr21c.getRegisterA()
                                                );

                                        List<org.jf.dexlib2.builder.BuilderInstruction> newInsts = new ArrayList<>();
                                        newInsts.add(invokeInstr);
                                        newInsts.add(moveResultInstr);

                                        toReplace.add(instruction);
                                        replacements.add(newInsts);

                                        changed = true;
                                    }
                                }
                            } else if (instruction instanceof org.jf.dexlib2.builder.instruction.BuilderInstruction21c) {
                                org.jf.dexlib2.builder.instruction.BuilderInstruction21c instr21c = (org.jf.dexlib2.builder.instruction.BuilderInstruction21c) instruction;
                                if (instr21c.getOpcode() == org.jf.dexlib2.Opcode.SGET || instr21c.getOpcode() == org.jf.dexlib2.Opcode.SGET_OBJECT) {
                                    if (instr21c.getReference() instanceof FieldReference) {
                                        FieldReference fieldRef = (FieldReference) instr21c.getReference();
                                        String owner = fieldRef.getDefiningClass();
                                        String className = owner.substring(1, owner.length() - 1).replace('/', '.');
                                        String name = fieldRef.getName();
                                        String desc = fieldRef.getType();

                                        FieldData fieldData = new FieldData(className, name);
                                        String proxyClassName = proxyManager.getFieldProxy(fieldData);
                                        String internalProxyName = "L" + proxyClassName.replace('.', '/') + ";";

                                        // We intercept static field gets:
                                        // 1. const/4 vTemp, 0 (null target for static)
                                        // 2. invoke-static {vTemp}, proxyClassName->get(Ljava/lang/Object;)Ljava/lang/Object;
                                        // 3. move-result-object vTemp
                                        // 4. check-cast vTemp, desc (if object)
                                        // 5. move-object vDest, vTemp (if object) or unbox if primitive
                                        // For MVP let's assume objects and handle strings mainly, or just push null target to get()

                                        // Simplified MVP for SGET_OBJECT:
                                        if (instr21c.getOpcode() == org.jf.dexlib2.Opcode.SGET_OBJECT) {
                                            int destReg = instr21c.getRegisterA();

                                            // const/4 vDest, 0
                                            org.jf.dexlib2.builder.instruction.BuilderInstruction11n constNull =
                                                    new org.jf.dexlib2.builder.instruction.BuilderInstruction11n(
                                                        org.jf.dexlib2.Opcode.CONST_4, destReg, 0
                                                    );

                                            // invoke-static {vDest}, proxy->get
                                            org.jf.dexlib2.builder.instruction.BuilderInstruction35c invokeInstr =
                                                    new org.jf.dexlib2.builder.instruction.BuilderInstruction35c(
                                                            org.jf.dexlib2.Opcode.INVOKE_STATIC,
                                                            1, // register count
                                                            destReg, 0, 0, 0, 0,
                                                            new ImmutableMethodReference(internalProxyName, "get", java.util.Collections.singletonList("Ljava/lang/Object;"), "Ljava/lang/Object;")
                                                    );

                                            // move-result-object vDest
                                            org.jf.dexlib2.builder.instruction.BuilderInstruction11x moveResult =
                                                    new org.jf.dexlib2.builder.instruction.BuilderInstruction11x(
                                                        org.jf.dexlib2.Opcode.MOVE_RESULT_OBJECT, destReg
                                                    );

                                            // check-cast vDest, desc
                                            org.jf.dexlib2.builder.instruction.BuilderInstruction21c checkCast =
                                                    new org.jf.dexlib2.builder.instruction.BuilderInstruction21c(
                                                        org.jf.dexlib2.Opcode.CHECK_CAST, destReg,
                                                        new org.jf.dexlib2.immutable.reference.ImmutableTypeReference(desc)
                                                    );

                                            List<org.jf.dexlib2.builder.BuilderInstruction> newInsts = new ArrayList<>();
                                            newInsts.add(constNull);
                                            newInsts.add(invokeInstr);
                                            newInsts.add(moveResult);
                                            newInsts.add(checkCast);

                                            toReplace.add(instruction);
                                            replacements.add(newInsts);
                                            changed = true;
                                        }
                                    }
                                }
                            }
                        }

                        if (!changed) {
                            return super.rewrite(methodImplementation);
                        }

                        // Get current instructions to find indices
                        List<org.jf.dexlib2.builder.BuilderInstruction> instructionsList = mutableImpl.getInstructions();

                        // We must process backwards to avoid messing up indices
                        for (int i = toReplace.size() - 1; i >= 0; i--) {
                            org.jf.dexlib2.builder.BuilderInstruction oldInst = toReplace.get(i);
                            List<org.jf.dexlib2.builder.BuilderInstruction> newInsts = replacements.get(i);

                            int index = instructionsList.indexOf(oldInst);
                            if (index != -1) {
                                // Add new instructions at the index
                                for (int j = newInsts.size() - 1; j >= 0; j--) {
                                    mutableImpl.addInstruction(index, newInsts.get(j));
                                }
                                // Remove old instruction (its index shifted by newInsts.size())
                                mutableImpl.removeInstruction(index + newInsts.size());
                            }
                        }

                        return mutableImpl;
                    }
                };
            }
        });

        DexFile rewrittenDexFile = methodRewriter.getDexFileRewriter().rewrite(dexFile);

        // 3. Write out the modified dex file to a temporary file
        File tempModifiedOriginalDex = File.createTempFile("modified-original-", ".dex");
        tempModifiedOriginalDex.deleteOnExit();
        DexFileFactory.writeDexFile(tempModifiedOriginalDex.getAbsolutePath(), rewrittenDexFile);

        // 4. Compile proxy classes into DEX
        Map<String, byte[]> proxies = proxyManager.getCompiledProxies();
        System.out.println("Injecting " + proxies.size() + " compiled proxy classes into the output DEX.");

        List<File> classFiles = new ArrayList<>();
        File tempDir = Files.createTempDirectory("proxy-classes").toFile();
        tempDir.deleteOnExit();

        for (Map.Entry<String, byte[]> proxyEntry : proxies.entrySet()) {
            String className = proxyEntry.getKey();
            byte[] classBytes = proxyEntry.getValue();

            File classFile = new File(tempDir, className + ".class");
            Files.write(classFile.toPath(), classBytes);
            classFiles.add(classFile);
        }

        // Use D8 to merge tempModifiedOriginalDex and proxy class files
        // D8 output mode DexIndexed usually expects a directory or a ZIP file when building to a Path,
        // unless we use a program consumer. Let's use a consumer.
        com.android.tools.r8.DexIndexedConsumer consumer = new com.android.tools.r8.DexIndexedConsumer.ForwardingConsumer(null) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

            @Override
            public void accept(int fileIndex, com.android.tools.r8.ByteDataView data, java.util.Set<String> descriptors, com.android.tools.r8.DiagnosticsHandler handler) {
                baos.write(data.getBuffer(), data.getOffset(), data.getLength());
            }

            @Override
            public void finished(com.android.tools.r8.DiagnosticsHandler handler) {
                try {
                    Files.write(output.toPath(), baos.toByteArray());
                } catch (java.io.IOException e) {
                    throw new RuntimeException("Failed to write merged dex to output file", e);
                }
            }
        };

        D8Command.Builder d8Builder = D8Command.builder()
                .setMinApiLevel(21)
                .setProgramConsumer(consumer)
                .addProgramFiles(tempModifiedOriginalDex.toPath());

        for (File classFile : classFiles) {
            d8Builder.addProgramFiles(classFile.toPath());
        }

        D8.run(d8Builder.build());

        System.out.println("Successfully wrote modified DEX file to: " + output.getName());
    }
}
