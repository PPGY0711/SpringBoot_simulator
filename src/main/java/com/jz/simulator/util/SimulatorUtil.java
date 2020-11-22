package com.jz.simulator.util;

import com.alibaba.fastjson.JSONObject;
import com.jz.simulator.common.Disassembler;
import com.jz.simulator.common.MipsCpu;
import com.jz.simulator.exception.InterruptException;
import com.jz.simulator.exception.SimulatorException;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * 模拟器帮助类
 */
public class SimulatorUtil {

    public static MipsCpu Instance = MipsCpu.getInstance();

    public static JSONObject interruptResult = null;

    public static JSONObject rebootSimulator(){
        Instance.resetMipsCpu();
        return getCpuInfo();
    }

    /**
     * 更新CPU执行后各部件的信息
     * @return
     */
    public static JSONObject getCpuInfo(){
        JSONObject jsonObject = new JSONObject();
        //寄存器
        jsonObject.put("commonRegisterContent",Instance.getCommonRegisterContent());
        jsonObject.put("coprocessorRegisterContent",Instance.getCoprocessorRegisterContent());
        //控制面板
        jsonObject.put("controlPanelInfo",Instance.getControlPanel());
        //存储器方式看内存
        jsonObject.put("memoryInfo",Instance.getMemoryInfo());
        //程序方式看内存
        jsonObject.put("programInfo",Instance.getProgramInfo());
        //获取下条指令信息
        jsonObject.put("nextInstructionInfo",Instance.getNextInstruction());
        //获取显存信息
        jsonObject.put("dispStr",Instance.getDispStr());
        return jsonObject;
    }
    
    public static void insertMachineCodeToMemory(int i, short s) throws SimulatorException {
        Instance.insertMachineCodeToMemory(i,s);
    }

    public static JSONObject executeProgram(String machineCodes) throws SimulatorException, UnsupportedEncodingException, InterruptException {
        int size = insertWholeProgram(machineCodes);
        Instance.setMaxAddr(size*2);
        Instance.executeWholeProgram();
        return getCpuInfo();
    }

    /**
     * 存储程序到内存
     * @param machineCodes
     * @return
     * @throws SimulatorException
     */
    public static int insertWholeProgram(String machineCodes) throws SimulatorException {
        Instance.resetMipsCpu();
        ArrayList<Long> codeList = Disassembler.removeEmptyLines(machineCodes);
        System.out.println(codeList.size());
        int j = 0;
        for(int i = 0; i < codeList.size(); i++){
            long machineCode = codeList.get(i);
            short high = (short)((machineCode&0xFFFF0000)>>16);
            insertMachineCodeToMemory(j++,high);
            short low = (short)(machineCode&0xFFFF);
            insertMachineCodeToMemory(j++,low);
        }
        return codeList.size();
    }

    public static JSONObject executeProgramInROM() throws UnsupportedEncodingException, SimulatorException, InterruptException {
        Instance.executeROM();
        Instance.setMaxAddr(Instance.getROMLength()*2);
        return getCpuInfo();
    }

    public static JSONObject singleStepDebugProgram(int size) throws UnsupportedEncodingException, SimulatorException, InterruptException {
        JSONObject retObject;
        Instance.setMaxAddr(size*2);
        if(Instance.singleStepDebugProgram() == 1) {
            System.out.println("---------- not end ----------");
            retObject = getCpuInfo();
            retObject.put("end",false);
        }
        else {
            System.out.println("---------- end ----------");
            retObject = getCpuInfo();
            retObject.put("end",true);
        }
        return retObject;
    }

    public static void setROMLength(int length){
        Instance.setROMLength(length);
    }

    public static int getROMLength(){
        return Instance.getROMLength();
    }

//    public static JSONObject getInterruptResult(){
//        if(interruptResult!=null){
//            JSONObject jsonObject = interruptResult;
//            interruptResult = null;
//            return jsonObject;
//        }
//        else return interruptResult;
//    }

    public static void setInterruptResult(JSONObject result) throws SimulatorException, UnsupportedEncodingException, InterruptException {
        interruptResult = result;
        System.out.println("util set interrupt result： " + interruptResult);
        Instance.setInterruptResult(interruptResult);
        int status = result.getInteger("status");
        handleInterruptException(status);
    }

    public static void handleInterruptException(int status) throws SimulatorException, UnsupportedEncodingException, InterruptException {
        Instance.handleInterruptException(status);
    }
}
