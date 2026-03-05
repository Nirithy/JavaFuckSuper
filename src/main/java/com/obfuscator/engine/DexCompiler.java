package com.obfuscator.engine;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;

import java.io.ByteArrayOutputStream;

/**
 * Interface to abstract the process of compiling Java bytecode (.class) to Dalvik bytecode (.dex).
 */
public interface DexCompiler {

    /**
     * Compiles a given Java class byte array into a DEX format byte array.
     *
     * @param classBytes The raw bytes of the compiled .class file.
     * @return The raw bytes of the resulting .dex file.
     */
    default byte[] compileClassToDex(byte[] classBytes) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            D8Command.Builder builder = D8Command.builder()
                .addClassProgramData(classBytes, Origin.unknown())
                .setProgramConsumer(new DexIndexedConsumer.ForwardingConsumer(null) {
                    @Override
                    public void accept(int fileIndex, ByteDataView data, java.util.Set<String> descriptors, DiagnosticsHandler handler) {
                        baos.write(data.getBuffer(), data.getOffset(), data.getLength());
                    }
                });
            D8.run(builder.build());
            return baos.toByteArray();
        } catch (CompilationFailedException e) {
            throw new RuntimeException("Failed to compile class to dex", e);
        }
    }
}
