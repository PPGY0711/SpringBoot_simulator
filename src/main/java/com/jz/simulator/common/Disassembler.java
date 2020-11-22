package com.jz.simulator.common;

import com.jz.simulator.exception.SimulatorException;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * 反汇编器
 */
@Getter
public class Disassembler {
    //汇编代码
    private Map<Integer, String> AssembleCodeMap;

    public Disassembler(){
        AssembleCodeMap = new TreeMap<>();
    }

    public void resetDisassembler(){
        AssembleCodeMap.clear();
    }

    public String disassembleCode(String rawCode){
        try {
            ArrayList<Long> codeList = removeEmptyLines(rawCode);
            for(int i = 0; i < codeList.size();i++){
                parseMachineCode(i,codeList.get(i));
            }
        } catch (SimulatorException e) {
            System.out.println(e.getMessage());
            return e.getMessage().replaceAll("\r\n","<br/>");
        }
        return null;
    }

    public static ArrayList<Long> removeEmptyLines(String rawCode) throws SimulatorException {
        String[] codeList = rawCode.split("\n");
        ArrayList<Long> retList = new ArrayList<>();
        for(String string : codeList){
            if(string.equals("") || string.matches("\\s+")){
                continue;
            }
            else{
                retList.add(str2Int(string.replaceAll("\t","").trim()));
            }
        }
        return retList;
    }

    private static long str2Int(String str) throws SimulatorException {
        return Long.parseLong(str,16);
    }

    private void parseMachineCode(int lineNum, long machineCode) throws SimulatorException {
        //for R
        int opc,rs,rt,rd,sa,func;
        //for I
        short dat,dot;
        int datExtension,dotExtension;
        //for J
        int adr,adrValue;
        //for C
        int rc;
        opc = (int)((machineCode&0xFC000000) >> 26);
        rs = (int)((machineCode&0x03E00000) >> 21);
        rt = (int)((machineCode&0x001F0000) >> 16);
        rc = rd = (int)((machineCode&0x0000F800) >> 11);
        sa = (int)((machineCode&0x000007C0) >> 6);
        func = (int)(machineCode&0x000003F);
        dot = dat = (short)(machineCode&0x0000FFFF);
        if((dat&0x8000)!=0)
            datExtension = 0xFFFF0000 | dat&0x0000FFFF;
        else
            datExtension = dat&0x0000FFFF;
        dotExtension = dot&0x0000FFFF;
        adr = (int)((machineCode&0x03FFFFFF));
        adrValue = adr*2;
        //统一key格式
        String key = MipsCpu.getKey(opc,func,rs);

//        System.out.println("=============== key is : " + key);
        String assembleCode = "";
        if(!Mnemonic.MNEMONICMAP.containsKey(key)){
            //识别不出的一律翻译为.word XXXX
            assembleCode = ".word 0x" + String.format("%-8x",machineCode);
            AssembleCodeMap.put(lineNum,assembleCode);
        }
        else{
            String mnemonic = Mnemonic.MNEMONICMAP.get(key);
            switch (mnemonic){
                //这里写反汇编最主要的函数处理
                case "lui":
                {
                    if(Register.REVERSECOMMONMAP.containsKey(rt)){
                        assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rt) + ", " + datExtension;
                    }
                    else{
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    break;
                }
                case "add":
                case "sub":
                case "slt":
                case "sltu":
                case "and":
                case "or":
                case "xor":
                case "nor":
                case "sllv":
                case "srlv":
                case "srav":
                case "mul":
                {
                    if(!Register.REVERSECOMMONMAP.containsKey(rs) || !Register.REVERSECOMMONMAP.containsKey(rt) ||!Register.REVERSECOMMONMAP.containsKey(rd)){
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else{
                        assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rd) + ", " + Register.REVERSECOMMONMAP.get(rs) + ", " + Register.REVERSECOMMONMAP.get(rt);
                    }
                    break;
                }
                case "sll":
                case "srl":
                case "sra":
                {
                    if(!Register.REVERSECOMMONMAP.containsKey(rs) || !Register.REVERSECOMMONMAP.containsKey(rd)){
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else {
                        assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rd) + ", " + Register.REVERSECOMMONMAP.get(rs) + ", " + sa;
                    }
                    break;
                }
                case "addi":
                case "slti":
                case "beq":
                case "bne":
                case "bgezal":
                {
                    if(!Register.REVERSECOMMONMAP.containsKey(rs) || !Register.REVERSECOMMONMAP.containsKey(rt)){
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else {
                        if(mnemonic.equals("bgezal")){
                            if(rt != 17){
                                throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                            }
                        }
                        if(mnemonic.equals("addi") || mnemonic.equals("slti"))
                            assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rt) + ", " + Register.REVERSECOMMONMAP.get(rs) + ", " + datExtension;
                        else
                            assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rs) + ", " + Register.REVERSECOMMONMAP.get(rt) + ", " + datExtension;
                    }
                    break;
                }
                case "lw":
                case "lwx":
                case "lh":
                case "lhx":
                case "lhu":
                case "lhux":
                case "sw":
                case "swx":
                case "sh":
                case "shx":
                {
                    if(!Register.REVERSECOMMONMAP.containsKey(rt) || !Register.REVERSECOMMONMAP.containsKey(rs)){
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else {
                        assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rt) + ", " + datExtension + "(" + Register.REVERSECOMMONMAP.get(rs) + ")";
                    }
                    break;
                }
                case "sltiu":
                case "andi":
                case "ori":
                case "xori":
                {
                    if(!Register.REVERSECOMMONMAP.containsKey(rt) || !Register.REVERSECOMMONMAP.containsKey(rs)){
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else {
                        assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rt) + ", " +  Register.REVERSECOMMONMAP.get(rs) + ", " + dotExtension;
                    }
                    break;
                }
                case "j":
                case "jal":
                {
                    assembleCode = mnemonic + " " + adrValue;
                    break;
                }
                case "jr":
                case "mthi":
                case "mtlo":
                {
                    if(!Register.REVERSECOMMONMAP.containsKey(rs)){
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else
                        assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rs);
                    break;
                }
                case "mfhi":
                case "mflo":
                {
                    if(!Register.REVERSECOMMONMAP.containsKey(rd)){
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else
                        assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rd);
                    break;
                }
                case "jalr":
                {
                    if(!Register.REVERSECOMMONMAP.containsKey(rs) || !Register.REVERSECOMMONMAP.containsKey(rd)){
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else {
                        assembleCode = mnemonic + " " +Register.REVERSECOMMONMAP.get(rs) + ", " + Register.REVERSECOMMONMAP.get(rd);
                    }
                    break;
                }
                case "eret":
                case "syscall":
                {
                    assembleCode = mnemonic;
                    break;
                }
                case "mult":
                case "multu":
                case "div":
                case "divu":
                {
                    if(!Register.REVERSECOMMONMAP.containsKey(rs) || !Register.REVERSECOMMONMAP.containsKey(rt)){
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else
                        assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rs) + ", " + Register.REVERSECOMMONMAP.get(rt);
                    break;
                }
                case "mfc0":
                case "mtc0":
                {
                    if(!Register.REVERSECOMMONMAP.containsKey(rt) || !Register.REVERSECOPROCESSORMAP.containsKey(rc)){
                        throw new SimulatorException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else
                        assembleCode = mnemonic + " " + Register.REVERSECOMMONMAP.get(rt) + ", " + Register.REVERSECOPROCESSORMAP.get(rc);
                    break;
                }
            }
            AssembleCodeMap.put(lineNum,assembleCode);
        }
    }
}
