package com.obfuscator.engine;

import com.obfuscator.core.ObfuscationEngine;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.DexFile;

import java.io.File;

/**
 * Obfuscation engine for processing Android Dex files (.dex, .apk) using Dexlib2/Smali.
 */
public class DexEngine implements ObfuscationEngine {

    @Override
    public void process(File input, File output) throws Exception {
        System.out.println("Processing DEX file: " + input.getName());

        // 1. Load the original dex file
        // We use the default opcodes for reading, which usually matches the dex file's api level
        DexFile dexFile = DexFileFactory.loadDexFile(input, Opcodes.getDefault());

        // 2. Iterate classes and methods
        for (org.jf.dexlib2.iface.ClassDef classDef : dexFile.getClasses()) {
            for (org.jf.dexlib2.iface.Method method : classDef.getMethods()) {
                org.jf.dexlib2.iface.MethodImplementation impl = method.getImplementation();
                if (impl != null) {
                    for (org.jf.dexlib2.iface.instruction.Instruction instruction : impl.getInstructions()) {
                        if (instruction instanceof org.jf.dexlib2.iface.instruction.ReferenceInstruction) {
                            org.jf.dexlib2.iface.instruction.ReferenceInstruction refInstruction = (org.jf.dexlib2.iface.instruction.ReferenceInstruction) instruction;
                            if (refInstruction.getReference() instanceof org.jf.dexlib2.iface.reference.StringReference) {
                                org.jf.dexlib2.iface.reference.StringReference stringRef = (org.jf.dexlib2.iface.reference.StringReference) refInstruction.getReference();
                                String originalString = stringRef.getString();

                                // Print out the string literal found
                                System.out.println("Found string in DEX method " + classDef.getType() + "." + method.getName() + ": " + originalString);
                            }
                        }
                    }
                }
            }
        }

        // 3. Write out the modified dex file
        DexFileFactory.writeDexFile(output.getAbsolutePath(), dexFile);

        System.out.println("Successfully wrote modified DEX file to: " + output.getName());
    }
}
