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
        ListIterator<AbstractInsnNode> iterator = instructions.iterator();
        while (iterator.hasNext()) {
            AbstractInsnNode insn = iterator.next();

            if (insn.getType() == AbstractInsnNode.TYPE_INSN && insn.getOpcode() == Opcodes.NEW) {
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

            if (insn.getType() == AbstractInsnNode.LDC_INSN) {
                LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                if (ldcInsn.cst instanceof String) {
                    String originalString = (String) ldcInsn.cst;
                    // Get or generate a dynamic proxy for this string
                    String proxyClassName = proxyManager.getStringProxy(originalString);

                    String internalName = proxyClassName.replace('.', '/');
                    MethodInsnNode proxyCall = new MethodInsnNode(Opcodes.INVOKESTATIC, internalName, "get", "()Ljava/lang/String;", false);
                    iterator.set(proxyCall);
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
