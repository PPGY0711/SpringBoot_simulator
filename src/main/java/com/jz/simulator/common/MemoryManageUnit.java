package com.jz.simulator.common;

import com.jz.simulator.exception.SimulatorException;
import com.jz.simulator.util.EncodingUtil;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.TreeMap;

/**
 * 存储管理单元
 * 负责与存储器有关的所有操作L/S
 */
public class MemoryManageUnit {
    /**内存单元，以zjie为单位，包含程序段、数据段、显存、堆栈
     * 程序段：0x0000-0x3FFF
     * 数据段：0x4000-0x6FFF
     * 显存：0x7000-0x8FFF
     * 堆栈：0x9000-0x9FFF
     * 寻址单位为zjie,存取整字指令
     */
    public static final long PROGRAMVOLUME = 0x7000L;
    private Map<Long,Short> memoryMap;      //内存单元
    private Long volume;                    //容量
    private Long dispPC;                    //显存指针
    private String dispStr;
    private int romLength;                  //ROM length

    public Long getVolume(){
        return volume;
    }

    public void setRomLength(int length){
        this.romLength = length;
    }

    public int getRomLength(){
        return romLength;
    }

    public Map<Long, Short> getMemoryMap(){
        return memoryMap;
    }
    public Long getDispPC(){
        return dispPC;
    }
    public void setDispPC(long dispPC){
        this.dispPC = dispPC;
    }

    MemoryManageUnit(){
        volume = 0xA000L;         //容量
        dispPC = 0x7000L;
        romLength = 0;
        dispStr = "";
        memoryMap = new TreeMap<>();
        for(long i = 0; i < volume; i++){
            memoryMap.put(i,(short)0);
        }
        //ROM

    }

    public String getDispStr(){
        return dispStr;
    }

    //存储器操作
    private void checkInvalidAddress(long addr) throws SimulatorException {
        if(addr>0x9FFF){
            throw new SimulatorException("Error: Invalid memory address.");
        }
    }

    //big-endian
    public long lw(long addr) throws SimulatorException {
        checkInvalidAddress(addr);
        return (memoryMap.get(addr)&0xFFFF)<<16+(memoryMap.get(addr+1)&0xFFFF);
    }

    //small-endian
    public long lwx(long addr) throws SimulatorException{
        checkInvalidAddress(addr);
        return (memoryMap.get(addr+1)&0xFFFF)<<16+(memoryMap.get(addr)&0xFFFF);
    }

    public long lh(long addr) throws SimulatorException{
        checkInvalidAddress(addr);
        return memoryMap.get(addr);
    }

    public long lhx(long addr) throws SimulatorException{
        checkInvalidAddress(addr);
        return memoryMap.get(addr);
    }

    public long lhu(long addr) throws SimulatorException{
        checkInvalidAddress(addr);
        return Long.parseLong(String.format("%x",memoryMap.get(addr)&0xFFFF)+"",16);
    }

    public long lhux(long addr) throws SimulatorException{
        checkInvalidAddress(addr);
        return Long.parseLong(String.format("%x",memoryMap.get(addr)&0xFFFF)+"",16);
    }

    public void sw(long addr, long value) throws SimulatorException {
        checkInvalidAddress(addr);
        memoryMap.put(addr, (short)(value&0xFFFF0000L));
        memoryMap.put(addr+1, (short)(value&0xFFFFL));
    }

    public void swx(long addr, long value) throws SimulatorException {
        checkInvalidAddress(addr);
        memoryMap.put(addr+1, (short)(value&0xFFFF0000L));
        memoryMap.put(addr, (short)(value&0xFFFFL));
    }

    public void sh(long addr, long value) throws SimulatorException{
        checkInvalidAddress(addr);
//        System.out.println("Sh: " + String.format("%x",(short)(value&0xFFFFL)));
        memoryMap.put(addr,(short)(value&0xFFFFL));
    }

    public void shx(long addr, long value) throws SimulatorException{
        checkInvalidAddress(addr);
        memoryMap.put(addr,(short)(value&0xFFFFL));
    }

    public void refreshDisplay(int type, long dispStart, long dispEnd) throws UnsupportedEncodingException {
        //刷新显存
        switch (type){
            case 0://print_int
            {
                int value = (memoryMap.get(dispStart)<<16) + memoryMap.get(dispStart+1)&0xFFFF;
                dispStr += value;
                break;
            }
            case 2://print_char
            case 1://print_string
            {
                byte[] tmpBytes = new byte[(int)(dispEnd-dispStart)*2];
                int id = 0;
//                System.out.println("========== in mmu before transform =========== ");
                for(long i = dispStart; i < dispEnd; i++){
                    short unit = memoryMap.get(i);
//                    System.out.println(String.format("%x",unit));
                    if((unit&0x8000)!=0){
                        tmpBytes[id] = (byte)((unit&0xFF00)>>8);
                        tmpBytes[id+1] = (byte)((unit&0x00FF));
                        id+=2;
                    }
                    else{
                        tmpBytes[id] = (byte)((unit&0x00FF));
                        id+=1;
                    }
                }

                byte[] gb2312AndAsciiBytes = new byte[id];
                System.arraycopy(tmpBytes,0,gb2312AndAsciiBytes,0,id);
                byte[] utf8Bytes = EncodingUtil.convertEncoding_ByteArr(gb2312AndAsciiBytes,"gbk","utf8");
                dispStr += new String(utf8Bytes);
                break;
            }
        }
    }

    public String readCFormatString(long addr){
        byte[] bytes = new byte[11];
        int j = 0;
        for(long i = 0;memoryMap.get(addr+i) != 0;i++){
            short s =memoryMap.get(addr+i);
            if((s&0x8000)!=0){
                bytes[j++] = (byte)(((s&0xFF00)>>8)&0xFF);
                bytes[j++] = (byte)((s&0xFF)&0xFF);
            }
            else
                bytes[j++] = (byte)((s&0xFF)&0xFF);
        }
        byte[] gbkBytes = new byte[j];
        System.arraycopy(bytes,0,gbkBytes,0,j);
        for(int i = 0; i < j; i++){
            System.out.println("fileNameBytes: " + String.format("%x",gbkBytes[i]));
        }
        try {
            byte[] utf8Bytes = EncodingUtil.convertEncoding_ByteArr(gbkBytes,"gbk","utf8");
            return new String(utf8Bytes,"utf8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
