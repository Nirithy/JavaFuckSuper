package com.obfuscator.engine;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import com.obfuscator.generator.MethodData;

import java.util.ListIterator;

/**
 * ASM MethodVisitor (Tree API) that intercepts instructions and rewrites them to proxy calls.
 */
public class ObfuscationMethodVisitor extends MethodNode {

    private final ProxyManager proxyManager;
    private final MethodVisitor nextVisitor;

    public ObfuscationMethodVisitor(int api, int access, String name, String descriptor, String signature, String[] exceptions, MethodVisitor nextVisitor, ProxyManager proxyManager) {
        super(api, access, name, descriptor, signature, exceptions);
        this.nextVisitor = nextVisitor;
        this.proxyManager = proxyManager;
    }

    @Override
    public void visitEnd() {
        if (!name.equals("<init>") && !name.equals("<clinit>") && instructions.size() > 0) {
            boolean virtualized = virtualizeMethod();
            if (virtualized) {
                super.visitEnd();
                if (nextVisitor != null) {
                    accept(nextVisitor);
                }
                return;
            }
            flattenControlFlow();

            // After flattening and virtualizing, we can also perform block shuffling
            // for any methods that are not strictly flattened but still have blocks
            shuffleBlocks();
        }

        // Ensure maxLocals is computed after our possible large additions
        int currentMaxLocals = 0;
        for (AbstractInsnNode node : instructions) {
            if (node instanceof VarInsnNode) {
                int var = ((VarInsnNode) node).var;
                int size = (node.getOpcode() == Opcodes.LLOAD || node.getOpcode() == Opcodes.LSTORE || node.getOpcode() == Opcodes.DLOAD || node.getOpcode() == Opcodes.DSTORE) ? 2 : 1;
                if (var + size > currentMaxLocals) currentMaxLocals = var + size;
            } else if (node instanceof IincInsnNode) {
                if (((IincInsnNode) node).var + 1 > currentMaxLocals) currentMaxLocals = ((IincInsnNode) node).var + 1;
            }
        }
        if (currentMaxLocals > maxLocals) {
            maxLocals = currentMaxLocals;
        }

        if (name.equals("<clinit>") && instructions.size() > 0 && Math.random() < 0.05) {
            String proxyClassName = proxyManager.getAntiDebugProxy();
            String internalName = proxyClassName.replace('.', '/');

            // Find the start of the method to inject anti-debug call
            AbstractInsnNode first = instructions.getFirst();
            if (first != null) {
                MethodInsnNode antiDebugCall = new MethodInsnNode(Opcodes.INVOKESTATIC, internalName, "check", "()V", false);
                instructions.insertBefore(first, antiDebugCall);
            }
        }

        injectInvalidLocalVariableTable();

        ListIterator<AbstractInsnNode> iterator = instructions.iterator();
        while (iterator.hasNext()) {
            AbstractInsnNode insn = iterator.next();

            // Insert flower instructions randomly (approx 10% chance per instruction)
            // Enable safe flower instructions (NOP and ICONST_0 + POP).
            // We avoid inserting before NEW to avoid breaking the NEW->DUP pattern detection.
            if (insn.getOpcode() != Opcodes.NEW && insn.getOpcode() != Opcodes.DUP && insn.getOpcode() != Opcodes.INVOKESPECIAL && Math.random() < 0.1) {
                double rand = Math.random();
                if (rand < 0.3) {
                    instructions.insertBefore(insn, new InsnNode(Opcodes.NOP));
                } else if (rand < 0.6) {
                    InsnList junkInstructions = new InsnList();
                    // Basic valid junk: push a number, then pop it. Or math operations that don't affect anything.
                    junkInstructions.add(new InsnNode(Opcodes.ICONST_1));
                    junkInstructions.add(new InsnNode(Opcodes.ICONST_1));
                    junkInstructions.add(new InsnNode(Opcodes.IADD));
                    junkInstructions.add(new InsnNode(Opcodes.POP));
                    instructions.insertBefore(insn, junkInstructions);
                } else {
                    // Opaque Predicate Injection
                    insertOpaquePredicate(insn);
                }
            }

            if (insn.getOpcode() == Opcodes.INEG) {
                // Instruction Substitution: -a -> ~a + 1
                InsnList subList = new InsnList();
                subList.add(new InsnNode(Opcodes.ICONST_M1));
                subList.add(new InsnNode(Opcodes.IXOR)); // ~a
                subList.add(new InsnNode(Opcodes.ICONST_1));
                subList.add(new InsnNode(Opcodes.IADD)); // ~a + 1

                iterator.remove();
                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, subList);
                    iterator.previous();
                } else {
                    instructions.add(subList);
                }
            } else if (insn.getType() == AbstractInsnNode.TYPE_INSN && insn.getOpcode() == Opcodes.NEW) {
                // We check if this is part of NEW -> DUP -> ... -> INVOKESPECIAL <init>
                TypeInsnNode newInsn = (TypeInsnNode) insn;
                AbstractInsnNode next = insn.getNext();
                if (next != null && next.getOpcode() == Opcodes.DUP) {
                    // Try to find the matching INVOKESPECIAL <init>
                    // This is a simplistic approach that works for typical compiler output,
                    // where DUP is followed by args pushed, then INVOKESPECIAL.
                    // A proper implementation requires data flow analysis, but this suffices for the requirements.
                    AbstractInsnNode current = next.getNext();
                    MethodInsnNode initInsn = null;
                    int stackDepth = 0; // tracking roughly to find matching <init>
                    while (current != null) {
                        if (current.getType() == AbstractInsnNode.METHOD_INSN) {
                            MethodInsnNode methodInsn = (MethodInsnNode) current;
                            if (methodInsn.getOpcode() == Opcodes.INVOKESPECIAL && methodInsn.name.equals("<init>") && methodInsn.owner.equals(newInsn.desc)) {
                                if (stackDepth == 0) {
                                    initInsn = methodInsn;
                                    break;
                                } else {
                                    stackDepth--; // consume a matching nested object creation if any? Wait, this gets complex.
                                    // For simplicity, just pick the first matching <init>. This assumes no nested same-type creations immediately.
                                    initInsn = methodInsn;
                                    break;
                                }
                            }
                        } else if (current.getOpcode() == Opcodes.NEW) {
                            stackDepth++; // crude depth tracking
                        }
                        current = current.getNext();
                    }

                    if (initInsn != null) {
                        // Found the NEW...DUP...INVOKESPECIAL pattern.
                        // We will replace the INVOKESPECIAL with our proxy call,
                        // and REMOVE the NEW and DUP instructions.

                        String className = newInsn.desc.replace('/', '.');
                        String proxyClassName = proxyManager.getClassCreationProxy(className);
                        String internalProxyName = proxyClassName.replace('.', '/');

                        Type[] argTypes = Type.getArgumentTypes(initInsn.desc);

                        InsnList newInstructions = new InsnList();

                        int tempVarIndex = maxLocals;
                        for (AbstractInsnNode node : instructions) {
                            if (node instanceof VarInsnNode) {
                                int var = ((VarInsnNode) node).var;
                                int size = (node.getOpcode() == Opcodes.LLOAD || node.getOpcode() == Opcodes.LSTORE || node.getOpcode() == Opcodes.DLOAD || node.getOpcode() == Opcodes.DSTORE) ? 2 : 1;
                                if (var + size > tempVarIndex) tempVarIndex = var + size;
                            } else if (node instanceof IincInsnNode) {
                                if (((IincInsnNode) node).var + 1 > tempVarIndex) tempVarIndex = ((IincInsnNode) node).var + 1;
                            }
                        }
                        tempVarIndex += 2;

                        int[] argLocalIndices = new int[argTypes.length];
                        int currentLocal = tempVarIndex;
                        for (int i = 0; i < argTypes.length; i++) {
                            argLocalIndices[i] = currentLocal;
                            currentLocal += argTypes[i].getSize();
                        }

                        // Pop arguments into locals
                        for (int i = argTypes.length - 1; i >= 0; i--) {
                            Type argType = argTypes[i];
                            int storeOpcode = argType.getOpcode(Opcodes.ISTORE);
                            newInstructions.add(new VarInsnNode(storeOpcode, argLocalIndices[i]));
                        }

                        pushInt(newInstructions, argTypes.length);
                        newInstructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));

                        for (int i = 0; i < argTypes.length; i++) {
                            newInstructions.add(new InsnNode(Opcodes.DUP));
                            pushInt(newInstructions, i);
                            Type argType = argTypes[i];
                            int loadOpcode = argType.getOpcode(Opcodes.ILOAD);
                            newInstructions.add(new VarInsnNode(loadOpcode, argLocalIndices[i]));
                            boxPrimitive(argType, newInstructions);
                            newInstructions.add(new InsnNode(Opcodes.AASTORE));
                        }

                        newInstructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, internalProxyName, "create", "([Ljava/lang/Object;)Ljava/lang/Object;", false));
                        newInstructions.add(new TypeInsnNode(Opcodes.CHECKCAST, newInsn.desc));

                        // Insert new instructions before initInsn
                        instructions.insertBefore(initInsn, newInstructions);
                        // Remove initInsn
                        instructions.remove(initInsn);
                        // Remove NEW and DUP
                        instructions.remove(newInsn);
                        instructions.remove(next); // DUP

                        if (currentLocal > maxLocals) {
                            maxLocals = currentLocal;
                        }

                        // We continue with iterator. It might skip nodes, but that's fine as long as we process the rest.
                        continue;
                    }
                }
            }

            if (insn.getOpcode() == Opcodes.IADD) {
                InsnList subList = new InsnList();
                int choice = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
                if (choice == 0) {
                    // Instruction Substitution: a + b -> a - (~b) - 1
                    subList.add(new InsnNode(Opcodes.ICONST_M1));
                    subList.add(new InsnNode(Opcodes.IXOR)); // ~b
                    subList.add(new InsnNode(Opcodes.ISUB)); // a - (~b)
                    subList.add(new InsnNode(Opcodes.ICONST_1));
                    subList.add(new InsnNode(Opcodes.ISUB)); // a - (~b) - 1
                } else {
                    // Instruction Substitution: a + b -> (a ^ b) + ((a & b) << 1)
                    // Stack: a, b
                    subList.add(new InsnNode(Opcodes.DUP2));
                    // Stack: a, b, a, b
                    subList.add(new InsnNode(Opcodes.IXOR));
                    // Stack: a, b, (a ^ b)
                    subList.add(new InsnNode(Opcodes.DUP_X2));
                    subList.add(new InsnNode(Opcodes.POP));
                    // Stack: (a ^ b), a, b
                    subList.add(new InsnNode(Opcodes.IAND));
                    // Stack: (a ^ b), (a & b)
                    subList.add(new InsnNode(Opcodes.ICONST_1));
                    subList.add(new InsnNode(Opcodes.ISHL));
                    // Stack: (a ^ b), ((a & b) << 1)
                    subList.add(new InsnNode(Opcodes.IADD));
                    // Stack: (a ^ b) + ((a & b) << 1)
                }

                iterator.remove();
                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, subList);
                    iterator.previous();
                } else {
                    instructions.add(subList);
                }
            } else if (insn.getOpcode() == Opcodes.ISUB) {
                // Instruction Substitution: a - b -> a + (~b) + 1
                InsnList subList = new InsnList();
                subList.add(new InsnNode(Opcodes.ICONST_M1));
                subList.add(new InsnNode(Opcodes.IXOR)); // ~b
                subList.add(new InsnNode(Opcodes.IADD)); // a + (~b)
                subList.add(new InsnNode(Opcodes.ICONST_1));
                subList.add(new InsnNode(Opcodes.IADD)); // a + (~b) + 1

                iterator.remove();
                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, subList);
                    iterator.previous();
                } else {
                    instructions.add(subList);
                }
            } else if (insn.getOpcode() == Opcodes.IXOR) {
                // Instruction Substitution: a ^ b -> (a | b) - (a & b)
                InsnList subList = new InsnList();
                // Stack: a, b
                subList.add(new InsnNode(Opcodes.DUP2));
                // Stack: a, b, a, b
                subList.add(new InsnNode(Opcodes.IOR));
                // Stack: a, b, (a | b)
                // We need to move (a | b) under a, b.
                // a, b are two words. (a|b) is one word.
                // dup_x2: [a, b, (a|b)] -> [(a|b), a, b, (a|b)]
                // pop: [(a|b), a, b]
                subList.add(new InsnNode(Opcodes.DUP_X2));
                subList.add(new InsnNode(Opcodes.POP));
                // Stack: (a|b), a, b
                subList.add(new InsnNode(Opcodes.IAND));
                // Stack: (a|b), (a&b)
                subList.add(new InsnNode(Opcodes.ISUB));
                // Stack: (a|b) - (a&b)

                iterator.remove();
                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, subList);
                    iterator.previous();
                } else {
                    instructions.add(subList);
                }
            } else if (insn.getOpcode() == Opcodes.IAND) {
                // Instruction Substitution: a & b -> ~(~a | ~b)
                InsnList subList = new InsnList();
                // Stack: a, b
                // We need to invert both.
                // Stack: a, b -> a, ~b
                subList.add(new InsnNode(Opcodes.ICONST_M1));
                subList.add(new InsnNode(Opcodes.IXOR));
                // Stack: a, ~b. Move ~b down.
                // swap: [a, ~b] -> [~b, a]
                subList.add(new InsnNode(Opcodes.SWAP));
                // Stack: ~b, a -> ~b, ~a
                subList.add(new InsnNode(Opcodes.ICONST_M1));
                subList.add(new InsnNode(Opcodes.IXOR));
                // Stack: ~b, ~a.
                subList.add(new InsnNode(Opcodes.IOR));
                // Stack: (~b | ~a) -> ~(~b | ~a)
                subList.add(new InsnNode(Opcodes.ICONST_M1));
                subList.add(new InsnNode(Opcodes.IXOR));

                iterator.remove();
                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, subList);
                    iterator.previous();
                } else {
                    instructions.add(subList);
                }
            } else if (insn.getOpcode() == Opcodes.IMUL) {
                // Instruction Substitution: IMUL -> IF multiplying by 2, shift instead.
                // We don't always know the operand without analyzing constants, but we can do a generic obfuscation for IMUL.
                // IMUL is hard to fully replace linearly without loops, but we can wrap it:
                // a * b -> (a * b) + 0 (with obfuscated 0)
                InsnList subList = new InsnList();
                int randVal = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 100);
                subList.add(new InsnNode(Opcodes.IMUL)); // Do the normal multiplication

                // Add 0: + (randVal - randVal)
                pushInt(subList, randVal);
                pushInt(subList, randVal);
                subList.add(new InsnNode(Opcodes.ISUB));
                subList.add(new InsnNode(Opcodes.IADD));

                iterator.remove();
                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, subList);
                    iterator.previous();
                } else {
                    instructions.add(subList);
                }
            } else if (insn.getOpcode() == Opcodes.ISHL || insn.getOpcode() == Opcodes.ISHR || insn.getOpcode() == Opcodes.IUSHR) {
                // Instruction Substitution: a << b -> a << (b & 31)
                InsnList subList = new InsnList();
                pushInt(subList, 31);
                subList.add(new InsnNode(Opcodes.IAND));
                subList.add(new InsnNode(insn.getOpcode()));

                iterator.remove();
                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, subList);
                    iterator.previous();
                } else {
                    instructions.add(subList);
                }
            } else if (insn.getOpcode() == Opcodes.IOR) {
                // Instruction Substitution: a | b -> ~(~a & ~b)
                InsnList subList = new InsnList();
                // Stack: a, b
                // We need to invert both.
                // Stack: a, b -> a, ~b
                subList.add(new InsnNode(Opcodes.ICONST_M1));
                subList.add(new InsnNode(Opcodes.IXOR));
                // Stack: a, ~b. Move ~b down.
                // swap: [a, ~b] -> [~b, a]
                subList.add(new InsnNode(Opcodes.SWAP));
                // Stack: ~b, a -> ~b, ~a
                subList.add(new InsnNode(Opcodes.ICONST_M1));
                subList.add(new InsnNode(Opcodes.IXOR));
                // Stack: ~b, ~a.
                subList.add(new InsnNode(Opcodes.IAND));
                // Stack: (~b & ~a) -> ~(~b & ~a)
                subList.add(new InsnNode(Opcodes.ICONST_M1));
                subList.add(new InsnNode(Opcodes.IXOR));

                iterator.remove();
                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, subList);
                    iterator.previous();
                } else {
                    instructions.add(subList);
                }
            } else if (insn.getType() == AbstractInsnNode.LDC_INSN) {
                LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                if (ldcInsn.cst instanceof String) {
                    String originalString = (String) ldcInsn.cst;
                    // Get or generate a dynamic proxy for this string
                    String proxyClassName = proxyManager.getStringProxy(originalString);

                    String internalName = proxyClassName.replace('.', '/');
                    MethodInsnNode proxyCall = new MethodInsnNode(Opcodes.INVOKESTATIC, internalName, "get", "()Ljava/lang/String;", false);
                    iterator.set(proxyCall);
                } else if (ldcInsn.cst instanceof Integer) {
                    int val = (Integer) ldcInsn.cst;
                    InsnList numList = obfuscateNumber(val);

                    iterator.remove();
                    AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                    if (nextNode != null) {
                        instructions.insertBefore(nextNode, numList);
                        iterator.previous();
                    } else {
                        instructions.add(numList);
                    }
                }
            } else if (insn.getOpcode() == Opcodes.BIPUSH || insn.getOpcode() == Opcodes.SIPUSH) {
                int val = ((IntInsnNode) insn).operand;
                InsnList numList = obfuscateNumber(val);

                iterator.remove();
                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, numList);
                    iterator.previous();
                } else {
                    instructions.add(numList);
                }
            } else if (insn.getOpcode() >= Opcodes.ICONST_M1 && insn.getOpcode() <= Opcodes.ICONST_5) {
                int val = insn.getOpcode() - Opcodes.ICONST_0;
                InsnList numList = obfuscateNumber(val);

                iterator.remove();
                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, numList);
                    iterator.previous();
                } else {
                    instructions.add(numList);
                }
            } else if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                String owner = methodInsn.owner;
                String name = methodInsn.name;
                String descriptor = methodInsn.desc;

                if (name.equals("<init>") || name.equals("<clinit>")) {
                    continue;
                }

                if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL || methodInsn.getOpcode() == Opcodes.INVOKESTATIC || methodInsn.getOpcode() == Opcodes.INVOKEINTERFACE || methodInsn.getOpcode() == Opcodes.INVOKESPECIAL) {
                    Type[] argTypes = Type.getArgumentTypes(descriptor);
                    String className = owner.replace('/', '.');
                    String[] paramTypes = new String[argTypes.length];
                    for (int i = 0; i < argTypes.length; i++) {
                        paramTypes[i] = argTypes[i].getClassName();
                    }
                    MethodData methodData = new MethodData(className, name, paramTypes);
                    String proxyClassName = proxyManager.getMethodProxy(methodData);
                    String internalProxyName = proxyClassName.replace('.', '/');

                    InsnList newInstructions = new InsnList();

                    // The stack currently has: [target] arg1 arg2 ... argN
                    // (target is absent for INVOKESTATIC)
                    // We need to pack the arguments into an Object[] array.
                    // To do this without complex local variable manipulation, we create the array first,
                    // but the arguments are already on the stack.
                    // We can swap/dup but it's easier to create a local variable to hold the array?
                    // Actually, ASM Tree API doesn't easily let us allocate locals without managing maxLocals unless we recompute it.
                    // ClassWriter.COMPUTE_MAXS handles maxLocals computation, so we can use a high local index.
                    // Alternatively, we pop arguments into locals, then push array, push array, push index, push local, aastore...
                    // Let's pop arguments in reverse order, store in locals, then construct array.
                    // To avoid local index conflicts, we'll just push array creation and swap. But swap only works for single words.
                    // Instead of locals, let's just use the max locals + offset approach or simply generate code to push the array and swap.

                    // Actually, let's build an Object[] locally without explicit local variable numbers by relying on the fact that we can just do this:
                    // push size
                    // anewarray Object
                    // For each arg from N-1 down to 0:
                    //   dupX1 (or dupX2 for double/long)
                    //   swap
                    //   box primitive if needed
                    //   push index
                    //   swap
                    //   aastore
                    // This stack manipulation gets very tricky with double/long.

                    // Simpler: use a temporary local array variable. We can compute an unused local index.
                    // We can just use the next available local variable index. Since COMPUTE_MAXS is used, we only need to pick a sufficiently large number, e.g., maxLocals.
                    // But we don't have maxLocals until visitMaxs.
                    // Fortunately, in MethodNode, maxLocals is updated if we add nodes, but to be safe, we can just find the max local index used in the method.
                    int tempVarIndex = maxLocals;
                    for (AbstractInsnNode node : instructions) {
                        if (node instanceof VarInsnNode) {
                            int var = ((VarInsnNode) node).var;
                            int size = (node.getOpcode() == Opcodes.LLOAD || node.getOpcode() == Opcodes.LSTORE || node.getOpcode() == Opcodes.DLOAD || node.getOpcode() == Opcodes.DSTORE) ? 2 : 1;
                            if (var + size > tempVarIndex) tempVarIndex = var + size;
                        } else if (node instanceof IincInsnNode) {
                            if (((IincInsnNode) node).var + 1 > tempVarIndex) tempVarIndex = ((IincInsnNode) node).var + 1;
                        }
                    }
                    // Increase tempVarIndex to avoid any clashes
                    tempVarIndex += 2;

                    // We need variables to hold the arguments temporarily
                    int[] argLocalIndices = new int[argTypes.length];
                    int currentLocal = tempVarIndex;
                    for (int i = 0; i < argTypes.length; i++) {
                        argLocalIndices[i] = currentLocal;
                        currentLocal += argTypes[i].getSize();
                    }

                    // Pop arguments into local variables (in reverse order)
                    for (int i = argTypes.length - 1; i >= 0; i--) {
                        Type argType = argTypes[i];
                        int storeOpcode = argType.getOpcode(Opcodes.ISTORE);
                        newInstructions.add(new VarInsnNode(storeOpcode, argLocalIndices[i]));
                    }

                    // For INVOKESTATIC, push null as the target. For others, the target is now at the top of the stack.
                    if (methodInsn.getOpcode() == Opcodes.INVOKESTATIC) {
                        newInstructions.add(new InsnNode(Opcodes.ACONST_NULL));
                    }

                    // Create the Object[] array
                    pushInt(newInstructions, argTypes.length);
                    newInstructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));

                    // Load arguments from locals, box if primitive, and store in array
                    for (int i = 0; i < argTypes.length; i++) {
                        newInstructions.add(new InsnNode(Opcodes.DUP)); // DUP array ref
                        pushInt(newInstructions, i); // Array index

                        Type argType = argTypes[i];
                        int loadOpcode = argType.getOpcode(Opcodes.ILOAD);
                        newInstructions.add(new VarInsnNode(loadOpcode, argLocalIndices[i]));

                        boxPrimitive(argType, newInstructions);

                        newInstructions.add(new InsnNode(Opcodes.AASTORE));
                    }

                    // Invoke the proxy
                    newInstructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, internalProxyName, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false));

                    Type returnType = Type.getReturnType(descriptor);
                    if (returnType.getSort() == Type.VOID) {
                        newInstructions.add(new InsnNode(Opcodes.POP));
                    } else if (returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY) {
                        newInstructions.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getInternalName()));
                    } else {
                        unboxPrimitive(returnType, newInstructions);
                    }

                    // Insert the new instructions and remove the original invoke instruction
                    iterator.remove(); // removes the current methodInsn

                    // We must insert newInstructions carefully so the iterator continues correctly
                    // iterator currently points to the element AFTER methodInsn.
                    // We insert newInstructions before the current iterator position.
                    AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                    if (nextNode != null) {
                        instructions.insertBefore(nextNode, newInstructions);
                        iterator.previous(); // point back to nextNode
                    } else {
                        instructions.add(newInstructions);
                    }

                    // Update maxLocals
                    if (currentLocal > maxLocals) {
                        maxLocals = currentLocal;
                    }
                }
            } else if (insn.getType() == AbstractInsnNode.JUMP_INSN) {
                JumpInsnNode jumpInsn = (JumpInsnNode) insn;
                int opcode = jumpInsn.getOpcode();

                String opString = null;
                boolean isObjectCompare = false;
                boolean isSingleArg = false;

                switch (opcode) {
                    case Opcodes.IFEQ: opString = "=="; isSingleArg = true; break;
                    case Opcodes.IFNE: opString = "!="; isSingleArg = true; break;
                    case Opcodes.IFLT: opString = "<"; isSingleArg = true; break;
                    case Opcodes.IFGE: opString = ">="; isSingleArg = true; break;
                    case Opcodes.IFGT: opString = ">"; isSingleArg = true; break;
                    case Opcodes.IFLE: opString = "<="; isSingleArg = true; break;
                    case Opcodes.IFNULL: opString = "=="; isSingleArg = true; isObjectCompare = true; break;
                    case Opcodes.IFNONNULL: opString = "!="; isSingleArg = true; isObjectCompare = true; break;
                    case Opcodes.IF_ICMPEQ: opString = "=="; break;
                    case Opcodes.IF_ICMPNE: opString = "!="; break;
                    case Opcodes.IF_ICMPLT: opString = "<"; break;
                    case Opcodes.IF_ICMPGE: opString = ">="; break;
                    case Opcodes.IF_ICMPGT: opString = ">"; break;
                    case Opcodes.IF_ICMPLE: opString = "<="; break;
                    case Opcodes.IF_ACMPEQ: opString = "=="; isObjectCompare = true; break;
                    case Opcodes.IF_ACMPNE: opString = "!="; isObjectCompare = true; break;
                }

                if (opString != null) {
                    String proxyClassName = proxyManager.getControlFlowProxy("IF");
                    String internalProxyName = proxyClassName.replace('.', '/');

                    InsnList newInstructions = new InsnList();

                    // To use the existing eval(String, int, int) or eval(String, Object, Object),
                    // we need two arguments. For single arg jump instructions (IFEQ, IFNULL, etc.),
                    // we compare the top of the stack against 0 or null.
                    if (isSingleArg) {
                        if (isObjectCompare) {
                            newInstructions.add(new InsnNode(Opcodes.ACONST_NULL));
                        } else {
                            newInstructions.add(new InsnNode(Opcodes.ICONST_0));
                        }
                    }

                    // Push the operator string
                    newInstructions.add(new LdcInsnNode(opString));

                    // The stack currently has: [..., value1, value2]
                    // We need to pass (opString, value1, value2) to the eval method.
                    // This means we have to move opString under value1 and value2.
                    // Since opString is a single word, and value1/value2 are single words (int/reference),
                    // Stack: [value1, value2, opString]
                    // dup_x2 -> [opString, value1, value2, opString]
                    // pop -> [opString, value1, value2]

                    newInstructions.add(new InsnNode(Opcodes.DUP_X2));
                    newInstructions.add(new InsnNode(Opcodes.POP));

                    if (isObjectCompare) {
                        newInstructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, internalProxyName, "eval", "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)Z", false));
                    } else {
                        newInstructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, internalProxyName, "eval", "(Ljava/lang/String;II)Z", false));
                    }

                    // The proxy returns a boolean (Z). If true (1), jump.
                    newInstructions.add(new JumpInsnNode(Opcodes.IFNE, jumpInsn.label));

                    iterator.remove();
                    AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                    if (nextNode != null) {
                        instructions.insertBefore(nextNode, newInstructions);
                        iterator.previous();
                    } else {
                        instructions.add(newInstructions);
                    }
                }
            } else if (insn.getType() == AbstractInsnNode.FIELD_INSN) {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                String owner = fieldInsn.owner.replace('/', '.');
                String name = fieldInsn.name;
                String desc = fieldInsn.desc;
                Type fieldType = Type.getType(desc);

                com.obfuscator.generator.FieldData fieldData = new com.obfuscator.generator.FieldData(owner, name);
                String proxyClassName = proxyManager.getFieldProxy(fieldData);
                String internalProxyName = proxyClassName.replace('.', '/');

                InsnList newInstructions = new InsnList();

                if (fieldInsn.getOpcode() == Opcodes.GETSTATIC || fieldInsn.getOpcode() == Opcodes.GETFIELD) {
                    if (fieldInsn.getOpcode() == Opcodes.GETSTATIC) {
                        newInstructions.add(new InsnNode(Opcodes.ACONST_NULL));
                    }

                    newInstructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, internalProxyName, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false));

                    if (fieldType.getSort() == Type.OBJECT || fieldType.getSort() == Type.ARRAY) {
                        newInstructions.add(new TypeInsnNode(Opcodes.CHECKCAST, fieldType.getInternalName()));
                    } else {
                        unboxPrimitive(fieldType, newInstructions);
                    }
                } else if (fieldInsn.getOpcode() == Opcodes.PUTSTATIC || fieldInsn.getOpcode() == Opcodes.PUTFIELD) {
                    // For PUTFIELD, stack has: [target, value]
                    // For PUTSTATIC, stack has: [value]
                    // We need to pass (target, boxed_value) to set(Object, Object)

                    int tempVarIndex = maxLocals;
                    for (AbstractInsnNode node : instructions) {
                        if (node instanceof VarInsnNode) {
                            int var = ((VarInsnNode) node).var;
                            int size = (node.getOpcode() == Opcodes.LLOAD || node.getOpcode() == Opcodes.LSTORE || node.getOpcode() == Opcodes.DLOAD || node.getOpcode() == Opcodes.DSTORE) ? 2 : 1;
                            if (var + size > tempVarIndex) tempVarIndex = var + size;
                        } else if (node instanceof IincInsnNode) {
                            if (((IincInsnNode) node).var + 1 > tempVarIndex) tempVarIndex = ((IincInsnNode) node).var + 1;
                        }
                    }
                    tempVarIndex += 2;

                    int valueLocal = tempVarIndex;
                    int targetLocal = tempVarIndex + fieldType.getSize();

                    // Pop value
                    newInstructions.add(new VarInsnNode(fieldType.getOpcode(Opcodes.ISTORE), valueLocal));

                    // Pop target if PUTFIELD, else push null
                    if (fieldInsn.getOpcode() == Opcodes.PUTFIELD) {
                        newInstructions.add(new VarInsnNode(Opcodes.ASTORE, targetLocal));
                        newInstructions.add(new VarInsnNode(Opcodes.ALOAD, targetLocal));
                    } else {
                        newInstructions.add(new InsnNode(Opcodes.ACONST_NULL));
                    }

                    // Push value and box
                    newInstructions.add(new VarInsnNode(fieldType.getOpcode(Opcodes.ILOAD), valueLocal));
                    boxPrimitive(fieldType, newInstructions);

                    newInstructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, internalProxyName, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", false));

                    if (targetLocal + 1 > maxLocals) {
                        maxLocals = targetLocal + 1;
                    }
                }

                iterator.remove();

                AbstractInsnNode nextNode = iterator.hasNext() ? iterator.next() : null;
                if (nextNode != null) {
                    instructions.insertBefore(nextNode, newInstructions);
                    iterator.previous();
                } else {
                    instructions.add(newInstructions);
                }
            }
        }

        super.visitEnd();
        if (nextVisitor != null) {
            accept(nextVisitor);
        }
    }

    /**
     * Injects an opaque predicate before the given instruction.
     * Generates a complex condition that always evaluates to true, wrapping the actual instruction or just jumping.
     * We will generate: `if ((7 * 7) % 2 != 0) goto next; throw exception; next:`
     */
    private void insertOpaquePredicate(AbstractInsnNode insn) {
        InsnList opaqueList = new InsnList();
        LabelNode trueLabel = new LabelNode();

        int choice = java.util.concurrent.ThreadLocalRandom.current().nextInt(8);
        int randVal = java.util.concurrent.ThreadLocalRandom.current().nextInt(10, 100);

        if (choice == 0) {
            // 7 * 7 % 2 != 0
            pushInt(opaqueList, 7);
            pushInt(opaqueList, 7);
            opaqueList.add(new InsnNode(Opcodes.IMUL));
            pushInt(opaqueList, 2);
            opaqueList.add(new InsnNode(Opcodes.IREM));
            opaqueList.add(new JumpInsnNode(Opcodes.IFNE, trueLabel));
        } else if (choice == 1) {
            // (x * x + x) % 2 == 0
            pushInt(opaqueList, randVal);
            opaqueList.add(new InsnNode(Opcodes.DUP));
            opaqueList.add(new InsnNode(Opcodes.IMUL));
            pushInt(opaqueList, randVal);
            opaqueList.add(new InsnNode(Opcodes.IADD));
            pushInt(opaqueList, 2);
            opaqueList.add(new InsnNode(Opcodes.IREM));
            opaqueList.add(new JumpInsnNode(Opcodes.IFEQ, trueLabel));
        } else if (choice == 2) {
            // (x * 0) == 0
            pushInt(opaqueList, randVal);
            pushInt(opaqueList, 0);
            opaqueList.add(new InsnNode(Opcodes.IMUL));
            opaqueList.add(new JumpInsnNode(Opcodes.IFEQ, trueLabel));
        } else if (choice == 3) {
            // x > -1 (where x is a positive random value)
            pushInt(opaqueList, randVal);
            pushInt(opaqueList, -1);
            opaqueList.add(new JumpInsnNode(Opcodes.IF_ICMPGT, trueLabel));
        } else if (choice == 4) {
            // (x ^ x) == 0
            pushInt(opaqueList, randVal);
            pushInt(opaqueList, randVal);
            opaqueList.add(new InsnNode(Opcodes.IXOR));
            opaqueList.add(new JumpInsnNode(Opcodes.IFEQ, trueLabel));
        } else if (choice == 5) {
            // (x^3 - x) % 3 == 0 (which is true for any integer)
            // stack: x
            pushInt(opaqueList, randVal);
            // dup twice: x, x, x
            opaqueList.add(new InsnNode(Opcodes.DUP));
            opaqueList.add(new InsnNode(Opcodes.DUP));
            // x * x -> x^2
            opaqueList.add(new InsnNode(Opcodes.IMUL));
            // x^2 * x -> x^3
            opaqueList.add(new InsnNode(Opcodes.IMUL));
            // x^3 - x
            pushInt(opaqueList, randVal);
            opaqueList.add(new InsnNode(Opcodes.ISUB));
            // % 3
            pushInt(opaqueList, 3);
            opaqueList.add(new InsnNode(Opcodes.IREM));
            // == 0
            opaqueList.add(new JumpInsnNode(Opcodes.IFEQ, trueLabel));
        } else if (choice == 6) {
            // (x + 1)^2 - x^2 - 2x - 1 == 0
            pushInt(opaqueList, randVal);

            // push (x+1)^2
            pushInt(opaqueList, randVal + 1);
            opaqueList.add(new InsnNode(Opcodes.DUP));
            opaqueList.add(new InsnNode(Opcodes.IMUL));

            // push x^2
            pushInt(opaqueList, randVal);
            opaqueList.add(new InsnNode(Opcodes.DUP));
            opaqueList.add(new InsnNode(Opcodes.IMUL));

            // subtract
            opaqueList.add(new InsnNode(Opcodes.ISUB));

            // push 2x
            pushInt(opaqueList, randVal);
            pushInt(opaqueList, 2);
            opaqueList.add(new InsnNode(Opcodes.IMUL));

            // subtract
            opaqueList.add(new InsnNode(Opcodes.ISUB));

            // subtract 1
            pushInt(opaqueList, 1);
            opaqueList.add(new InsnNode(Opcodes.ISUB));

            // == 0
            opaqueList.add(new JumpInsnNode(Opcodes.IFEQ, trueLabel));
        } else {
            // Fermat's Little Theorem: (x^7 - x) % 7 == 0
            pushInt(opaqueList, randVal);
            opaqueList.add(new InsnNode(Opcodes.DUP));
            opaqueList.add(new InsnNode(Opcodes.DUP)); // x, x, x
            opaqueList.add(new InsnNode(Opcodes.IMUL)); // x, x^2
            opaqueList.add(new InsnNode(Opcodes.DUP)); // x, x^2, x^2
            opaqueList.add(new InsnNode(Opcodes.IMUL)); // x, x^4
            opaqueList.add(new InsnNode(Opcodes.SWAP)); // x^4, x
            opaqueList.add(new InsnNode(Opcodes.DUP_X1)); // x, x^4, x
            opaqueList.add(new InsnNode(Opcodes.IMUL)); // x, x^5
            opaqueList.add(new InsnNode(Opcodes.SWAP)); // x^5, x
            opaqueList.add(new InsnNode(Opcodes.DUP_X1)); // x, x^5, x
            opaqueList.add(new InsnNode(Opcodes.IMUL)); // x, x^6
            opaqueList.add(new InsnNode(Opcodes.SWAP)); // x^6, x
            opaqueList.add(new InsnNode(Opcodes.DUP_X1)); // x, x^6, x
            opaqueList.add(new InsnNode(Opcodes.IMUL)); // x, x^7
            opaqueList.add(new InsnNode(Opcodes.SWAP)); // x^7, x
            opaqueList.add(new InsnNode(Opcodes.ISUB)); // x^7 - x
            pushInt(opaqueList, 7);
            opaqueList.add(new InsnNode(Opcodes.IREM)); // (x^7 - x) % 7
            opaqueList.add(new JumpInsnNode(Opcodes.IFEQ, trueLabel));
        }

        // Fake code if predicate is false (which it never is)

        // Add object allocation and immediate pop to disrupt object flow analysis
        opaqueList.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Object"));
        opaqueList.add(new InsnNode(Opcodes.DUP));
        opaqueList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        opaqueList.add(new InsnNode(Opcodes.POP));

        // Add random useless valid instructions before the crash to confuse static analysis
        int fakeJunkCount = java.util.concurrent.ThreadLocalRandom.current().nextInt(3, 7);
        for (int i = 0; i < fakeJunkCount; i++) {
            opaqueList.add(new InsnNode(Opcodes.ACONST_NULL));
            opaqueList.add(new InsnNode(Opcodes.POP));
        }

        // Add dummy API call
        opaqueList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
        opaqueList.add(new InsnNode(Opcodes.POP2));

        // State-dependent loop (dead code)
        LabelNode loopStart = new LabelNode();
        opaqueList.add(loopStart);
        pushInt(opaqueList, randVal);
        pushInt(opaqueList, 100);
        opaqueList.add(new JumpInsnNode(Opcodes.IF_ICMPLT, loopStart)); // infinite loop if randVal < 100

        // opaqueList.add(new TypeInsnNode(Opcodes.NEW, "java/lang/VerifyError"));
        // opaqueList.add(new InsnNode(Opcodes.DUP));
        // opaqueList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/VerifyError", "<init>", "()V", false));
        // opaqueList.add(new InsnNode(Opcodes.ATHROW));

        opaqueList.add(trueLabel);

        instructions.insertBefore(insn, opaqueList);
    }

    private InsnList obfuscateNumber(int val) {
        InsnList numList = new InsnList();
        int strategy = java.util.concurrent.ThreadLocalRandom.current().nextInt(6);

        if (strategy == 0) {
            // Strategy 0: val ^ key
            int key = java.util.concurrent.ThreadLocalRandom.current().nextInt();
            int xored = val ^ key;
            pushInt(numList, xored);
            pushInt(numList, key);
            numList.add(new InsnNode(Opcodes.IXOR));
        } else if (strategy == 1) {
            // Strategy 1: ~(val ^ key)
            int key = java.util.concurrent.ThreadLocalRandom.current().nextInt();
            int xoredAndInverted = ~(val ^ key);
            pushInt(numList, xoredAndInverted);
            numList.add(new InsnNode(Opcodes.ICONST_M1));
            numList.add(new InsnNode(Opcodes.IXOR)); // inverts it back to (val ^ key)
            pushInt(numList, key);
            numList.add(new InsnNode(Opcodes.IXOR)); // (val ^ key) ^ key = val
        } else if (strategy == 2) {
            // Strategy 2: (val - key1) ^ key2
            int key1 = java.util.concurrent.ThreadLocalRandom.current().nextInt();
            int key2 = java.util.concurrent.ThreadLocalRandom.current().nextInt();
            int obfuscated = (val - key1) ^ key2;

            pushInt(numList, obfuscated);
            pushInt(numList, key2);
            numList.add(new InsnNode(Opcodes.IXOR)); // yields (val - key1)
            pushInt(numList, key1);
            numList.add(new InsnNode(Opcodes.IADD)); // yields val
        } else if (strategy == 3) {
            // Strategy 3: (val + key1) - key2 + key3
            int key1 = java.util.concurrent.ThreadLocalRandom.current().nextInt();
            int key2 = java.util.concurrent.ThreadLocalRandom.current().nextInt();
            int key3 = java.util.concurrent.ThreadLocalRandom.current().nextInt();
            int obfuscated = val + key1 - key2 + key3;

            pushInt(numList, obfuscated);
            pushInt(numList, key3);
            numList.add(new InsnNode(Opcodes.ISUB)); // yields val + key1 - key2
            pushInt(numList, key2);
            numList.add(new InsnNode(Opcodes.IADD)); // yields val + key1
            pushInt(numList, key1);
            numList.add(new InsnNode(Opcodes.ISUB)); // yields val
        } else if (strategy == 4) {
            // Strategy 4: Bitwise shifts
            // Safe strategy: val = (val ^ key1) + key2
            int key1 = java.util.concurrent.ThreadLocalRandom.current().nextInt();
            int key2 = java.util.concurrent.ThreadLocalRandom.current().nextInt();
            int obfuscated = (val ^ key1) + key2;

            pushInt(numList, obfuscated);
            pushInt(numList, key2);
            numList.add(new InsnNode(Opcodes.ISUB)); // yields val ^ key1
            pushInt(numList, key1);
            numList.add(new InsnNode(Opcodes.IXOR)); // yields val
        } else if (strategy == 5) {
            // Strategy 5: array length and math
            // val = ((val * key) / key)
            // Need a non-zero key. Prevent overflow by using a small key if val is large,
            // but just to be safe from overflow truncating bits, let's use a simpler safe div.
            // Let's use array length to push a number:
            // val = (val + key1) - key1 (where key1 is pushed via array length)
            int key1 = java.util.concurrent.ThreadLocalRandom.current().nextInt(5, 50);
            int obfuscated = val + key1;

            pushInt(numList, obfuscated);

            // Push key1 via array length
            pushInt(numList, key1);
            numList.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE));
            numList.add(new InsnNode(Opcodes.ARRAYLENGTH));

            numList.add(new InsnNode(Opcodes.ISUB));
        } else {
            // Strategy 6: Dynamic calculation using String.length()
            // val = (val - length) + String.length()
            int length = java.util.concurrent.ThreadLocalRandom.current().nextInt(5, 20);
            int obfuscated = val - length;

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                sb.append((char) ('a' + java.util.concurrent.ThreadLocalRandom.current().nextInt(26)));
            }
            String randomString = sb.toString();

            pushInt(numList, obfuscated);
            numList.add(new LdcInsnNode(randomString));
            numList.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false));
            numList.add(new InsnNode(Opcodes.IADD));
        }

        return numList;
    }

    private void pushInt(InsnList list, int value) {
        if (value >= -1 && value <= 5) {
            list.add(new InsnNode(Opcodes.ICONST_0 + value));
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            list.add(new IntInsnNode(Opcodes.BIPUSH, value));
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            list.add(new IntInsnNode(Opcodes.SIPUSH, value));
        } else {
            list.add(new LdcInsnNode(value));
        }
    }

    private void boxPrimitive(Type type, InsnList list) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
                break;
            case Type.CHAR:
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
                break;
            case Type.BYTE:
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
                break;
            case Type.SHORT:
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
                break;
            case Type.INT:
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
                break;
            case Type.FLOAT:
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
                break;
            case Type.LONG:
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
                break;
            case Type.DOUBLE:
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                break;
        }
    }

    private boolean virtualizeMethod() {
        // Ensure method has no try-catch blocks
        if (!tryCatchBlocks.isEmpty()) return false;

        // Virtualize static methods with integer-like primitive arguments.
        if ((access & Opcodes.ACC_STATIC) == 0) {
            return false;
        }

        Type[] args = Type.getArgumentTypes(desc);
        for (Type t : args) {
            int sort = t.getSort();
            if (sort != Type.INT && sort != Type.BOOLEAN && sort != Type.BYTE && sort != Type.CHAR && sort != Type.SHORT) {
                return false;
            }
        }

        // Two-pass compilation to support labels and jumps
        java.util.Map<LabelNode, Integer> labelOffsets = new java.util.HashMap<>();
        java.util.List<Object> vmCodeNodes = new java.util.ArrayList<>();
        int currentOffset = 0;

        // Pass 1: Compute offsets and validate instructions
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof LineNumberNode || insn instanceof FrameNode) {
                continue; // Ignore
            }

            if (insn instanceof LabelNode) {
                labelOffsets.put((LabelNode) insn, currentOffset);
                continue;
            }

            int opcode = insn.getOpcode();
            int insnSize = 0;

            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                insnSize = 5; // OP_PUSH + 4 bytes
            } else if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                insnSize = 5; // OP_PUSH + 4 bytes
            } else if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer) {
                insnSize = 5; // OP_PUSH + 4 bytes
            } else if (opcode == Opcodes.ILOAD || opcode == Opcodes.ISTORE) {
                VarInsnNode varInsn = (VarInsnNode) insn;
                if (varInsn.var > 63) return false; // VM only supports up to 64 locals
                insnSize = 2; // OP_LOAD/STORE + 1 byte
            } else if (opcode >= Opcodes.IADD && opcode <= Opcodes.IUSHR) {
                insnSize = 1; // 1 byte opcode
            } else if (opcode == Opcodes.IRETURN || opcode == Opcodes.RETURN) {
                insnSize = 1; // OP_RET
            } else if (opcode == Opcodes.GOTO) {
                insnSize = 3; // OP_JMP + 2 bytes
            } else if (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ICMPLE) {
                insnSize = 3; // OP_IF* + 2 bytes
            } else if (opcode == Opcodes.IINC) {
                // IINC can be simulated but requires multiple instructions, simplify:
                insnSize = 12; // load, push const, add, store (2+5+1+2 = 10? Actually: LOAD, PUSH const, ADD, STORE. Total: 2+5+1+2 = 10). Let's say 10.
            } else if (opcode == Opcodes.DUP || opcode == Opcodes.POP || opcode == Opcodes.SWAP) {
                insnSize = 1; // 1 byte opcode
            } else if (opcode == Opcodes.IALOAD || opcode == Opcodes.IASTORE || opcode == Opcodes.ARRAYLENGTH) {
                insnSize = 1; // 1 byte opcode
            } else {
                // Unsupported opcode for our simple VM
                return false;
            }

            vmCodeNodes.add(insn);
            currentOffset += insnSize;
        }

        // Ensure the code size doesn't exceed 65535 (due to 2-byte jump targets)
        if (currentOffset > 65535) return false;

        java.util.List<Byte> vmCode = new java.util.ArrayList<>();

        // Pass 2: Generate custom bytecode
        for (Object nodeObj : vmCodeNodes) {
            AbstractInsnNode insn = (AbstractInsnNode) nodeObj;
            if (insn instanceof LabelNode) continue;
            int opcode = insn.getOpcode();

            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                vmCode.add((byte) 0x01); // OP_PUSH
                int val = opcode - Opcodes.ICONST_0;
                addIntToBytes(vmCode, val);
            } else if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                vmCode.add((byte) 0x01); // OP_PUSH
                int val = ((IntInsnNode) insn).operand;
                addIntToBytes(vmCode, val);
            } else if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer) {
                vmCode.add((byte) 0x01); // OP_PUSH
                int val = (Integer) ((LdcInsnNode) insn).cst;
                addIntToBytes(vmCode, val);
            } else if (opcode == Opcodes.ILOAD) {
                vmCode.add((byte) 0x0E); // OP_LOAD
                vmCode.add((byte) (((VarInsnNode) insn).var & 0xFF));
            } else if (opcode == Opcodes.ISTORE) {
                vmCode.add((byte) 0x0F); // OP_STORE
                vmCode.add((byte) (((VarInsnNode) insn).var & 0xFF));
            } else if (opcode == Opcodes.IADD) { vmCode.add((byte) 0x02); // ADD
            } else if (opcode == Opcodes.ISUB) { vmCode.add((byte) 0x03); // SUB
            } else if (opcode == Opcodes.IMUL) { vmCode.add((byte) 0x04); // MUL
            } else if (opcode == Opcodes.IDIV) { vmCode.add((byte) 0x09); // DIV
            } else if (opcode == Opcodes.IREM) { vmCode.add((byte) 0x0A); // REM
            } else if (opcode == Opcodes.IAND) { vmCode.add((byte) 0x06); // AND
            } else if (opcode == Opcodes.IOR)  { vmCode.add((byte) 0x07); // OR
            } else if (opcode == Opcodes.IXOR) { vmCode.add((byte) 0x08); // XOR
            } else if (opcode == Opcodes.ISHL) { vmCode.add((byte) 0x0B); // SHL
            } else if (opcode == Opcodes.ISHR) { vmCode.add((byte) 0x0C); // SHR
            } else if (opcode == Opcodes.IUSHR){ vmCode.add((byte) 0x0D); // USHR
            } else if (opcode == Opcodes.IRETURN || opcode == Opcodes.RETURN) {
                vmCode.add((byte) 0x05); // RET
            } else if (opcode == Opcodes.GOTO) {
                vmCode.add((byte) 0x10); // OP_JMP
                JumpInsnNode jmp = (JumpInsnNode) insn;
                Integer target = labelOffsets.get(jmp.label);
                if (target == null) return false;
                addShortToBytes(vmCode, target);
            } else if (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ICMPLE) {
                // OP_IFEQ is 0x11, OP_IF_ICMPLE is 0x18.
                // ASM Opcodes.IFEQ is 153.
                byte vmOpcode = (byte) (0x11 + (opcode - Opcodes.IFEQ));
                vmCode.add(vmOpcode);
                JumpInsnNode jmp = (JumpInsnNode) insn;
                Integer target = labelOffsets.get(jmp.label);
                if (target == null) return false;
                addShortToBytes(vmCode, target);
            } else if (opcode == Opcodes.IINC) {
                IincInsnNode iinc = (IincInsnNode) insn;
                vmCode.add((byte) 0x0E); // OP_LOAD
                vmCode.add((byte) (iinc.var & 0xFF));
                vmCode.add((byte) 0x01); // OP_PUSH
                addIntToBytes(vmCode, iinc.incr);
                vmCode.add((byte) 0x02); // ADD
                vmCode.add((byte) 0x0F); // OP_STORE
                vmCode.add((byte) (iinc.var & 0xFF));
            } else if (opcode == Opcodes.DUP) {
                vmCode.add((byte) 0x19); // OP_DUP
            } else if (opcode == Opcodes.POP) {
                vmCode.add((byte) 0x1A); // OP_POP
            } else if (opcode == Opcodes.SWAP) {
                vmCode.add((byte) 0x1B); // OP_SWAP
            } else if (opcode == Opcodes.IALOAD) {
                vmCode.add((byte) 0x1C); // OP_IALOAD
            } else if (opcode == Opcodes.IASTORE) {
                vmCode.add((byte) 0x1D); // OP_IASTORE
            } else if (opcode == Opcodes.ARRAYLENGTH) {
                vmCode.add((byte) 0x1E); // OP_ARRAYLENGTH
            }
        }

        // Ensure method has return
        if (!vmCode.isEmpty() && vmCode.get(vmCode.size() - 1) != 0x05) {
            vmCode.add((byte) 0x05);
        }

        // Successfully virtualized. We will now replace the method instructions.
        instructions.clear();

        // Remove local variables to avoid issues since we handle them inside the VM execution.
        if (localVariables != null) {
            localVariables.clear();
        }

        byte[] finalCode = new byte[vmCode.size()];
        for (int i = 0; i < vmCode.size(); i++) {
            finalCode[i] = vmCode.get(i);
        }

        // Generate bytecode to push the finalCode byte array
        pushInt(instructions, finalCode.length);
        instructions.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE));

        for (int i = 0; i < finalCode.length; i++) {
            instructions.add(new InsnNode(Opcodes.DUP));
            pushInt(instructions, i);
            pushInt(instructions, finalCode[i]);
            instructions.add(new InsnNode(Opcodes.BASTORE));
        }

        // Initialize locals array
        pushInt(instructions, 64);
        instructions.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));

        int currentLocalIndex = 0;
        for (Type arg : args) {
            instructions.add(new InsnNode(Opcodes.DUP));
            pushInt(instructions, currentLocalIndex);
            instructions.add(new VarInsnNode(Opcodes.ILOAD, currentLocalIndex));
            instructions.add(new InsnNode(Opcodes.IASTORE));
            currentLocalIndex++;
        }

        // Call the VM execute method
        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/obfuscator/vm/VirtualMachine", "execute", "([B[I)I", false));

        Type returnType = Type.getReturnType(desc);
        if (returnType.getSort() == Type.VOID) {
            instructions.add(new InsnNode(Opcodes.POP));
            instructions.add(new InsnNode(Opcodes.RETURN));
        } else {
            // Need to handle primitive return types like boolean
            if (returnType.getSort() == Type.BOOLEAN || returnType.getSort() == Type.BYTE || returnType.getSort() == Type.CHAR || returnType.getSort() == Type.SHORT) {
                // Since execute returns an int, and JVM expects int for these on stack, this works.
            }
            instructions.add(new InsnNode(Opcodes.IRETURN));
        }

        maxStack = 5; // sufficient for array allocation and pushing

        return true;
    }

    private void addIntToBytes(java.util.List<Byte> list, int val) {
        list.add((byte) ((val >> 24) & 0xFF));
        list.add((byte) ((val >> 16) & 0xFF));
        list.add((byte) ((val >> 8) & 0xFF));
        list.add((byte) (val & 0xFF));
    }

    private void addShortToBytes(java.util.List<Byte> list, int val) {
        list.add((byte) ((val >> 8) & 0xFF));
        list.add((byte) (val & 0xFF));
    }

    private void shuffleBlocks() {
        if (instructions.size() < 10) return;
        if (!tryCatchBlocks.isEmpty()) return;

        java.util.List<InsnList> blocks = new java.util.ArrayList<>();
        InsnList currentBlock = new InsnList();
        LabelNode currentLabel = new LabelNode();
        currentBlock.add(currentLabel);

        for (AbstractInsnNode node : instructions) {
            instructions.remove(node);

            if (node instanceof LabelNode) {
                if (currentBlock.size() > 1) {
                    blocks.add(currentBlock);
                } else {
                    blocks.add(currentBlock);
                }
                currentBlock = new InsnList();
                currentLabel = (LabelNode) node;
                currentBlock.add(currentLabel);
            } else {
                currentBlock.add(node);
                int op = node.getOpcode();
                if (op == Opcodes.GOTO || op == Opcodes.IRETURN || op == Opcodes.RETURN ||
                    op == Opcodes.ARETURN || op == Opcodes.LRETURN || op == Opcodes.DRETURN ||
                    op == Opcodes.ATHROW || (node instanceof JumpInsnNode) ||
                    node instanceof LookupSwitchInsnNode || node instanceof TableSwitchInsnNode) {

                    blocks.add(currentBlock);
                    currentBlock = new InsnList();
                    currentLabel = new LabelNode();
                    currentBlock.add(currentLabel);
                }
            }
        }
        if (currentBlock.size() > 0) {
            blocks.add(currentBlock);
        }

        if (blocks.size() <= 1) {
            for (InsnList b : blocks) {
                instructions.add(b);
            }
            return;
        }

        for (int i = 0; i < blocks.size() - 1; i++) {
            InsnList b = blocks.get(i);
            AbstractInsnNode last = b.getLast();
            if (last != null) {
                int op = last.getOpcode();
                if (op != Opcodes.GOTO && op != Opcodes.IRETURN && op != Opcodes.RETURN &&
                    op != Opcodes.ARETURN && op != Opcodes.LRETURN && op != Opcodes.DRETURN &&
                    op != Opcodes.ATHROW && !(last instanceof LookupSwitchInsnNode) &&
                    !(last instanceof TableSwitchInsnNode) && !(last instanceof JumpInsnNode)) {
                    LabelNode nextLabel = (LabelNode) blocks.get(i + 1).getFirst();
                    b.add(new JumpInsnNode(Opcodes.GOTO, nextLabel));
                }
            }
        }

        // Just add blocks without shuffling. Shuffling causes frames compute out of bounds because local variables are uninitialized in some jumps or stack types are wrong on jumps.
        for (InsnList b : blocks) {
            instructions.add(b);
        }
    }

    private void flattenControlFlow() {
        // Find maximum local variables index so we can allocate a variable for the state
        int tempVarIndex = maxLocals;
        for (AbstractInsnNode node : instructions) {
            if (node instanceof VarInsnNode) {
                int var = ((VarInsnNode) node).var;
                int size = (node.getOpcode() == Opcodes.LLOAD || node.getOpcode() == Opcodes.LSTORE || node.getOpcode() == Opcodes.DLOAD || node.getOpcode() == Opcodes.DSTORE) ? 2 : 1;
                if (var + size > tempVarIndex) tempVarIndex = var + size;
            } else if (node instanceof IincInsnNode) {
                if (((IincInsnNode) node).var + 1 > tempVarIndex) tempVarIndex = ((IincInsnNode) node).var + 1;
            }
        }

        if (instructions.size() < 10) return; // Skip too simple methods

        // Check if method is suitable for real basic block splitting CFF
        // It must not have try-catch blocks or existing jump instructions to avoid breaking verifier or ASM
        boolean canSplit = tryCatchBlocks.isEmpty();
        if (canSplit) {
            for (AbstractInsnNode node : instructions) {
                if (node instanceof JumpInsnNode || node instanceof LabelNode || node instanceof LookupSwitchInsnNode || node instanceof TableSwitchInsnNode) {
                    canSplit = false;
                    break;
                }
            }
        }

        int stateLocal = tempVarIndex;
        maxLocals = stateLocal + 1;

        LabelNode loopStart = new LabelNode();
        LabelNode loopEnd = new LabelNode();
        LabelNode defaultCase = new LabelNode();

        if (canSplit) {
            // Real Control Flow Flattening (Basic Block Splitting)
            java.util.List<InsnList> blocks = new java.util.ArrayList<>();
            InsnList currentBlock = new InsnList();
            int insnCount = 0;

            for (AbstractInsnNode node : instructions) {
                instructions.remove(node);
                currentBlock.add(node);
                insnCount++;
                // Split every 3 instructions, or if it's a return instruction
                if (insnCount >= 3 || node.getOpcode() == Opcodes.IRETURN || node.getOpcode() == Opcodes.RETURN || node.getOpcode() == Opcodes.ARETURN || node.getOpcode() == Opcodes.LRETURN || node.getOpcode() == Opcodes.DRETURN || node.getOpcode() == Opcodes.ATHROW) {
                    blocks.add(currentBlock);
                    currentBlock = new InsnList();
                    insnCount = 0;
                }
            }
            if (currentBlock.size() > 0) {
                blocks.add(currentBlock);
            }

            int numBlocks = blocks.size();
            int[] states = new int[numBlocks + 1]; // +1 for end state
            java.util.Set<Integer> usedStates = new java.util.HashSet<>();
            for (int i = 0; i <= numBlocks; i++) {
                int state;
                do {
                    state = java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000);
                } while (!usedStates.add(state));
                states[i] = state;
            }

            InsnList prelude = new InsnList();
            pushInt(prelude, states[0]);
            prelude.add(new VarInsnNode(Opcodes.ISTORE, stateLocal));
            prelude.add(loopStart);
            prelude.add(new VarInsnNode(Opcodes.ILOAD, stateLocal));

            int dummyCount = numBlocks / 2; // Add some dummy states
            if (dummyCount < 2) dummyCount = 2;

            int totalSwitchCases = numBlocks + dummyCount;
            int[] keys = new int[totalSwitchCases];
            LabelNode[] handlers = new LabelNode[totalSwitchCases];
            int[] dummyStates = new int[dummyCount];

            for (int i = 0; i < numBlocks; i++) {
                keys[i] = states[i];
                handlers[i] = new LabelNode();
            }

            for (int i = 0; i < dummyCount; i++) {
                int dummyState;
                do {
                    dummyState = java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000);
                } while (!usedStates.add(dummyState));
                dummyStates[i] = dummyState;
                keys[numBlocks + i] = dummyState;
                handlers[numBlocks + i] = new LabelNode();
            }

            // Sort keys and handlers for LookupSwitchInsnNode
            int[] sortedKeys = keys.clone();
            LabelNode[] sortedHandlers = handlers.clone();
            for (int i = 0; i < sortedKeys.length - 1; i++) {
                for (int j = 0; j < sortedKeys.length - i - 1; j++) {
                    if (sortedKeys[j] > sortedKeys[j + 1]) {
                        int tempKey = sortedKeys[j];
                        sortedKeys[j] = sortedKeys[j + 1];
                        sortedKeys[j + 1] = tempKey;

                        LabelNode tempHandler = sortedHandlers[j];
                        sortedHandlers[j] = sortedHandlers[j + 1];
                        sortedHandlers[j + 1] = tempHandler;
                    }
                }
            }

            prelude.add(new LookupSwitchInsnNode(defaultCase, sortedKeys, sortedHandlers));
            instructions.add(prelude);

            for (int i = 0; i < numBlocks; i++) {
                instructions.add(handlers[i]);
                instructions.add(blocks.get(i));

                // If the block doesn't end with a return/throw, transition to next state
                AbstractInsnNode last = blocks.get(i).getLast();
                int op = last != null ? last.getOpcode() : -1;
                if (op != Opcodes.IRETURN && op != Opcodes.RETURN && op != Opcodes.ARETURN && op != Opcodes.LRETURN && op != Opcodes.DRETURN && op != Opcodes.ATHROW) {

                    int nextState = states[i + 1];

                    // Sometimes route through a dummy state first
                    if (java.util.concurrent.ThreadLocalRandom.current().nextBoolean()) {
                        int dummyIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(dummyCount);
                        nextState = dummyStates[dummyIndex];
                        // The dummy state will need to eventually route to states[i+1].
                        // To keep it simple, dummy states just route to loopStart directly, but they need to set the state.
                        // Actually, if we just set the state to states[i+1] in the dummy state, it works.
                        // But wait, we can't easily dynamically map dummy -> real without a complex map or altering dummy state logic.
                        // Let's just make dummy states trampoline to a random real state? No, that breaks control flow.
                        // Instead, let dummy state just be a dead-end that throws an exception, or we don't route to it.
                        // Let's just not route to dummy states from real blocks, but let them exist in the switch.
                        // Wait, if we don't route to them, they are dead code.
                        nextState = states[i + 1];
                    }

                    int key1 = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 10000);
                    int key2 = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 10000);

                    int currentState = states[i];

                    // Complex multi-variable non-linear transition:
                    // nextState = ((currentState ^ key1) * key2) - offset
                    // So offset = ((currentState ^ key1) * key2) - nextState
                    // Ensure key2 is not 0
                    if (key2 == 0) key2 = 1;
                    int offset = ((currentState ^ key1) * key2) - nextState;

                    instructions.add(new VarInsnNode(Opcodes.ILOAD, stateLocal)); // load currentState
                    pushInt(instructions, key1);
                    instructions.add(new InsnNode(Opcodes.IXOR));
                    pushInt(instructions, key2);
                    instructions.add(new InsnNode(Opcodes.IMUL));
                    pushInt(instructions, offset);
                    instructions.add(new InsnNode(Opcodes.ISUB));

                    instructions.add(new VarInsnNode(Opcodes.ISTORE, stateLocal));
                    instructions.add(new JumpInsnNode(Opcodes.GOTO, loopStart));
                }
            }

            // Add the dummy handlers
            for (int i = 0; i < dummyCount; i++) {
                instructions.add(handlers[numBlocks + i]);

                // Add some junk math
                pushInt(instructions, dummyStates[i]);
                pushInt(instructions, 42);
                instructions.add(new InsnNode(Opcodes.IXOR));
                instructions.add(new InsnNode(Opcodes.POP));

                // Route to a random state (since it's dead code, it doesn't matter, but it looks real)
                int randomNextState = states[java.util.concurrent.ThreadLocalRandom.current().nextInt(numBlocks)];
                pushInt(instructions, randomNextState);
                instructions.add(new VarInsnNode(Opcodes.ISTORE, stateLocal));
                instructions.add(new JumpInsnNode(Opcodes.GOTO, loopStart));
            }
        } else {
            // Fallback to simple simulated switch wrapper
            LabelNode case1 = new LabelNode(); // Trampoline 1
            LabelNode case2 = new LabelNode(); // Trampoline 2
            LabelNode case3 = new LabelNode(); // Trampoline 3
            LabelNode case4 = new LabelNode(); // The actual method code

            int state1, state2, state3, state4;

            while (true) {
                int seed = java.util.concurrent.ThreadLocalRandom.current().nextInt(100, 1000);
                state1 = seed;
                state2 = (state1 ^ 0x5a) + 0x11;
                state3 = (state2 ^ 0xc3) - 0x05;
                state4 = (state3 ^ 0x0f) + 0x22;

                if (state1 != state2 && state1 != state3 && state1 != state4 &&
                    state2 != state3 && state2 != state4 &&
                    state3 != state4) {
                    break;
                }
            }

            InsnList prelude = new InsnList();
            pushInt(prelude, state1);
            prelude.add(new VarInsnNode(Opcodes.ISTORE, stateLocal));
            prelude.add(loopStart);
            prelude.add(new VarInsnNode(Opcodes.ILOAD, stateLocal));

            // lookup switch with randomized values
            int[] keys = new int[] { state1, state2, state3, state4 };
            LabelNode[] handlers = new LabelNode[] { case1, case2, case3, case4 };

            // Sort keys and handlers for LookupSwitchInsnNode
            for (int i = 0; i < keys.length - 1; i++) {
                for (int j = 0; j < keys.length - i - 1; j++) {
                    if (keys[j] > keys[j + 1]) {
                        int tempKey = keys[j];
                        keys[j] = keys[j + 1];
                        keys[j + 1] = tempKey;

                        LabelNode tempHandler = handlers[j];
                        handlers[j] = handlers[j + 1];
                        handlers[j + 1] = tempHandler;
                    }
                }
            }

            prelude.add(new LookupSwitchInsnNode(defaultCase, keys, handlers));

            // Case 1: Transition to state2
            prelude.add(case1);
            prelude.add(new VarInsnNode(Opcodes.ILOAD, stateLocal));
            pushInt(prelude, 0x5a);
            prelude.add(new InsnNode(Opcodes.IXOR));
            pushInt(prelude, 0x11);
            prelude.add(new InsnNode(Opcodes.IADD));
            prelude.add(new VarInsnNode(Opcodes.ISTORE, stateLocal));
            prelude.add(new JumpInsnNode(Opcodes.GOTO, loopStart));

            // Case 2: Transition to state3
            prelude.add(case2);
            prelude.add(new VarInsnNode(Opcodes.ILOAD, stateLocal));
            pushInt(prelude, 0xc3);
            prelude.add(new InsnNode(Opcodes.IXOR));
            pushInt(prelude, 0x05);
            prelude.add(new InsnNode(Opcodes.ISUB));
            prelude.add(new VarInsnNode(Opcodes.ISTORE, stateLocal));
            prelude.add(new JumpInsnNode(Opcodes.GOTO, loopStart));

            // Case 3: Transition to state4
            prelude.add(case3);
            prelude.add(new VarInsnNode(Opcodes.ILOAD, stateLocal));
            pushInt(prelude, 0x0f);
            prelude.add(new InsnNode(Opcodes.IXOR));
            pushInt(prelude, 0x22);
            prelude.add(new InsnNode(Opcodes.IADD));
            prelude.add(new VarInsnNode(Opcodes.ISTORE, stateLocal));
            prelude.add(new JumpInsnNode(Opcodes.GOTO, loopStart));

            // Case 4: The actual method code
            prelude.add(case4);

            instructions.insert(prelude);
        }

        // Default case (exit loop, though the method code should have returned)
        InsnList epilogue = new InsnList();
        epilogue.add(defaultCase);

        // Add exception control flow obfuscation (fake try-catch with ATHROW)
        LabelNode fakeTryStart = new LabelNode();
        LabelNode fakeTryEnd = new LabelNode();
        LabelNode fakeCatch = new LabelNode();

        epilogue.add(fakeTryStart);
        // Throw a fake exception to confuse analysis
        epilogue.add(new TypeInsnNode(Opcodes.NEW, "java/lang/RuntimeException"));
        epilogue.add(new InsnNode(Opcodes.DUP));
        epilogue.add(new LdcInsnNode("Fake Exception"));
        epilogue.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false));
        epilogue.add(new InsnNode(Opcodes.ATHROW));
        epilogue.add(fakeTryEnd);

        // Catch block that will never actually execute but is registered in the ExceptionTable
        epilogue.add(fakeCatch);
        epilogue.add(new InsnNode(Opcodes.POP)); // Pop the exception object
        // Return null/throw actual to satisfy verifier
        epilogue.add(new InsnNode(Opcodes.ACONST_NULL));
        epilogue.add(new InsnNode(Opcodes.ATHROW));
        epilogue.add(loopEnd);

        // Add fake code to satisfy the JVM verifier for basic blocks
        // The issue is that TryCatchBlockNode needs a valid frame to jump to the catch block, but our block throws unconditionally.
        // It's safer to just insert this at the very end of the method.
        instructions.add(epilogue);

        // Register the fake try-catch block
        TryCatchBlockNode fakeTryCatchBlock = new TryCatchBlockNode(fakeTryStart, fakeTryEnd, fakeCatch, "java/lang/Exception");
        tryCatchBlocks.add(fakeTryCatchBlock);
    }

    private void injectInvalidLocalVariableTable() {
        if (instructions.size() == 0) return;

        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();

        instructions.insert(start);
        instructions.add(end);

        if (this.localVariables == null) {
            this.localVariables = new java.util.ArrayList<>();
        }

        // It is unsafe to inject local variables that overlap and are invalid types, it breaks modern ASM frame computation and verifier.
        // We remove fake local variables here to avoid java.lang.ArrayIndexOutOfBoundsException during computeAllFrames.
    }

    private void unboxPrimitive(Type type, InsnList list) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
                break;
            case Type.CHAR:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Character"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false));
                break;
            case Type.BYTE:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Byte"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false));
                break;
            case Type.SHORT:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Short"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false));
                break;
            case Type.INT:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Integer"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
                break;
            case Type.FLOAT:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Float"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
                break;
            case Type.LONG:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
                break;
            case Type.DOUBLE:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Double"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
                break;
        }
    }
}
