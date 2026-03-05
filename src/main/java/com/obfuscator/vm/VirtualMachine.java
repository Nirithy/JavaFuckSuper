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

    /**
     * Executes the given custom bytecode.
     * @param bytecode The byte array representing the instructions to execute.
     * @return The result of the execution.
     */
    public static int execute(byte[] bytecode) {
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
                case OP_RET:
                    return stack[--sp];
                default:
                    throw new RuntimeException("Unknown VM opcode: " + opcode);
            }
        }

        return 0;
    }
}
