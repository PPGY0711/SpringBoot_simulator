package com.jz.simulator.common;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 助记符
 */
public class Mnemonic {
    private static final String[] ALLMNEMONICS =
            {"lui", "add", "addi", "sub", "slt", "slti", "sltu", "sltiu",
            "and", "andi", "or", "ori", "xor", "xori", "nor", "sll", "sllv",
            "srl", "srlv", "sra", "srav", "lw", "lwx", "lh", "lhx", "lhu",
            "lhux", "sw", "swx", "sh", "shx", "beq", "bne", "bgezal", "j",
            "jal", "jr", "jalr", "mfc0", "mtc0", "eret", "syscall", "mul",
            "mult", "multu", "div", "divu", "mfhi", "mflo", "mthi", "mtlo"};
    private static final int[] ALLOPCS =
            {15, 0, 8, 0, 0, 10, 0, 11, 0, 12, 0, 13, 0, 14, 0, 0, 0, 0, 0,
            0, 0, 35, 34, 33, 32, 37, 36, 43, 42, 41, 40, 4, 5, 1, 2, 3,
            0, 0, 16, 16, 16, 0, 28, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] ALLFUNCS =
            {0, 32, 0, 34, 42, 0, 43, 0, 36, 0, 37, 0, 38, 0, 39, 0,
            4, 2, 6, 3, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 8, 9, 0, 0, 24, 12, 2, 24, 25, 26, 27, 16, 18, 17, 19};
    public static final Map<String, Integer> OPCMAP = new HashMap<>();
    public static final Map<String, Integer> FUNCMAP = new HashMap<>();
    public static final Map<String, String> MNEMONICMAP = new TreeMap<>();

    //初始化
    static{
        for(int i = 0; i < ALLMNEMONICS.length;i++){
            OPCMAP.put(ALLMNEMONICS[i],ALLOPCS[i]);
            FUNCMAP.put(ALLMNEMONICS[i],ALLFUNCS[i]);
        }
        for(int i = 0; i < ALLMNEMONICS.length; i++){
            String key = "(" + Mnemonic.ALLOPCS[i]+","+Mnemonic.ALLFUNCS[i]+")";
            MNEMONICMAP.put(key,Mnemonic.ALLMNEMONICS[i]);
        }
        MNEMONICMAP.remove("(16,0)");
        MNEMONICMAP.put("(16,0),0","mfc0");
        MNEMONICMAP.put("(16,0),4","mtc0");
    }
}
