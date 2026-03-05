package com.obfuscator.vm;

/**
 * A simple Virtual Machine interpreter for executing extracted bytecode.
 * Used for basic VM Protection obfuscation.
 */
public class VirtualMachine {

    // Instruction set
    public static final byte OP_PUSH = 0x01;
    public static final byte OP_ADD = 0x02;
    public static final byte OP_SUB = 0x03;
    public static final byte OP_MUL = 0x04;
    public static final byte OP_RET = 0x05;
    public static final byte OP_AND = 0x06;
    public static final byte OP_OR = 0x07;
    public static final byte OP_XOR = 0x08;
    public static final byte OP_DIV = 0x09;
    public static final byte OP_REM = 0x0A;
    public static final byte OP_SHL = 0x0B;
    public static final byte OP_SHR = 0x0C;
    public static final byte OP_USHR = 0x0D;

    // Local variable operations
    public static final byte OP_LOAD = 0x0E;
    public static final byte OP_STORE = 0x0F;

    // Control flow operations
    public static final byte OP_JMP = 0x10;
    public static final byte OP_IFEQ = 0x11;
    public static final byte OP_IFNE = 0x12;
    public static final byte OP_IF_ICMPEQ = 0x13;
    public static final byte OP_IF_ICMPNE = 0x14;
    public static final byte OP_IF_ICMPLT = 0x15;
    public static final byte OP_IF_ICMPGE = 0x16;
    public static final byte OP_IF_ICMPGT = 0x17;
    public static final byte OP_IF_ICMPLE = 0x18;

    /**
     * Executes the given custom bytecode.
     * @param bytecode The byte array representing the instructions to execute.
     * @param locals The initial local variables (arguments).
     * @return The result of the execution.
     */
    public static int execute(byte[] bytecode, int[] locals) {
        int[] stack = new int[64];
        int sp = 0;
        int pc = 0;

        while (pc < bytecode.length) {
            byte opcode = bytecode[pc++];

            switch (opcode) {
                case OP_PUSH:
                    // Read next 4 bytes as integer to push
                    int val = ((bytecode[pc++] & 0xFF) << 24) |
                              ((bytecode[pc++] & 0xFF) << 16) |
                              ((bytecode[pc++] & 0xFF) << 8)  |
                              (bytecode[pc++] & 0xFF);
                    stack[sp++] = val;
                    break;
                case OP_ADD:
                    int bAdd = stack[--sp];
                    int aAdd = stack[--sp];
                    stack[sp++] = aAdd + bAdd;
                    break;
                case OP_SUB:
                    int bSub = stack[--sp];
                    int aSub = stack[--sp];
                    stack[sp++] = aSub - bSub;
                    break;
                case OP_MUL:
                    int bMul = stack[--sp];
                    int aMul = stack[--sp];
                    stack[sp++] = aMul * bMul;
                    break;
                case OP_AND:
                    int bAnd = stack[--sp];
                    int aAnd = stack[--sp];
                    stack[sp++] = aAnd & bAnd;
                    break;
                case OP_OR:
                    int bOr = stack[--sp];
                    int aOr = stack[--sp];
                    stack[sp++] = aOr | bOr;
                    break;
                case OP_XOR:
                    int bXor = stack[--sp];
                    int aXor = stack[--sp];
                    stack[sp++] = aXor ^ bXor;
                    break;
                case OP_DIV:
                    int bDiv = stack[--sp];
                    int aDiv = stack[--sp];
                    stack[sp++] = aDiv / bDiv;
                    break;
                case OP_REM:
                    int bRem = stack[--sp];
                    int aRem = stack[--sp];
                    stack[sp++] = aRem % bRem;
                    break;
                case OP_SHL:
                    int bShl = stack[--sp];
                    int aShl = stack[--sp];
                    stack[sp++] = aShl << bShl;
                    break;
                case OP_SHR:
                    int bShr = stack[--sp];
                    int aShr = stack[--sp];
                    stack[sp++] = aShr >> bShr;
                    break;
                case OP_USHR:
                    int bUshr = stack[--sp];
                    int aUshr = stack[--sp];
                    stack[sp++] = aUshr >>> bUshr;
                    break;
                case OP_LOAD:
                    // Read next 1 byte as local variable index
                    int loadIndex = bytecode[pc++] & 0xFF;
                    stack[sp++] = locals[loadIndex];
                    break;
                case OP_STORE:
                    // Read next 1 byte as local variable index
                    int storeIndex = bytecode[pc++] & 0xFF;
                    locals[storeIndex] = stack[--sp];
                    break;
                case OP_JMP:
                    // Read next 2 bytes as jump target pc
                    int jmpTarget = ((bytecode[pc++] & 0xFF) << 8) | (bytecode[pc++] & 0xFF);
                    pc = jmpTarget;
                    break;
                case OP_IFEQ:
                    int valIfeq = stack[--sp];
                    int targetIfeq = ((bytecode[pc++] & 0xFF) << 8) | (bytecode[pc++] & 0xFF);
                    if (valIfeq == 0) pc = targetIfeq;
                    break;
                case OP_IFNE:
                    int valIfne = stack[--sp];
                    int targetIfne = ((bytecode[pc++] & 0xFF) << 8) | (bytecode[pc++] & 0xFF);
                    if (valIfne != 0) pc = targetIfne;
                    break;
                case OP_IF_ICMPEQ:
                    int bIcmpeq = stack[--sp];
                    int aIcmpeq = stack[--sp];
                    int targetIcmpeq = ((bytecode[pc++] & 0xFF) << 8) | (bytecode[pc++] & 0xFF);
                    if (aIcmpeq == bIcmpeq) pc = targetIcmpeq;
                    break;
                case OP_IF_ICMPNE:
                    int bIcmpne = stack[--sp];
                    int aIcmpne = stack[--sp];
                    int targetIcmpne = ((bytecode[pc++] & 0xFF) << 8) | (bytecode[pc++] & 0xFF);
                    if (aIcmpne != bIcmpne) pc = targetIcmpne;
                    break;
                case OP_IF_ICMPLT:
                    int bIcmplt = stack[--sp];
                    int aIcmplt = stack[--sp];
                    int targetIcmplt = ((bytecode[pc++] & 0xFF) << 8) | (bytecode[pc++] & 0xFF);
                    if (aIcmplt < bIcmplt) pc = targetIcmplt;
                    break;
                case OP_IF_ICMPGE:
                    int bIcmpge = stack[--sp];
                    int aIcmpge = stack[--sp];
                    int targetIcmpge = ((bytecode[pc++] & 0xFF) << 8) | (bytecode[pc++] & 0xFF);
                    if (aIcmpge >= bIcmpge) pc = targetIcmpge;
                    break;
                case OP_IF_ICMPGT:
                    int bIcmpgt = stack[--sp];
                    int aIcmpgt = stack[--sp];
                    int targetIcmpgt = ((bytecode[pc++] & 0xFF) << 8) | (bytecode[pc++] & 0xFF);
                    if (aIcmpgt > bIcmpgt) pc = targetIcmpgt;
                    break;
                case OP_IF_ICMPLE:
                    int bIcmple = stack[--sp];
                    int aIcmple = stack[--sp];
                    int targetIcmple = ((bytecode[pc++] & 0xFF) << 8) | (bytecode[pc++] & 0xFF);
                    if (aIcmple <= bIcmple) pc = targetIcmple;
                    break;
                case OP_RET:
                    return sp > 0 ? stack[--sp] : 0;
                default:
                    throw new RuntimeException("Unknown VM opcode: " + opcode);
            }
        }

        return 0;
    }
}
