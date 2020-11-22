package com.jz.simulator.common;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jz.simulator.exception.InterruptException;
import com.jz.simulator.exception.SimulatorException;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 处理器模拟类
 * 指令需要存入内存才能执行
 */
public class MipsCpu {
    private long pc;                                        //程序计数器
    private long lastPc;                                    //上一个pc
    private Map<Integer, Long> commonRegisterContent;       //通用寄存器内容
    private Map<Integer, Long> coprocessorRegisterContent;  //协处理器寄存器内容
    private long hiRegister,loRegister;                     //HI;LO
    private MemoryManageUnit mmu;                           //存储管理单元
    private static MipsCpu Instance=null;                   //使用单例模式创建
    private int intMask;                                    //中断状态
    private InterruptException interruptException;          //中断异常
    private JSONObject interruptResult;                     //中断返回结果
    private int maxAddr;                                    //记录最大程序段地址
    private Map<Integer, JSONObject> fileMap;               //文件描述符对应表
    private int fileDescriptionSymbol;                      //文件描述符

    private MipsCpu(){
        lastPc = pc = 0L;
        maxAddr = 0;
        mmu = new MemoryManageUnit();
        commonRegisterContent = new TreeMap<>();
        coprocessorRegisterContent = new TreeMap<>();
        initRegisterValue();
        intMask = 0;
        interruptException = new InterruptException();
        interruptResult = null;
        fileMap = new HashMap<>();
        fileDescriptionSymbol = 0;
    }

    public String getDispStr(){
        return mmu.getDispStr();
    }

    public void setMaxAddr(int maxAddr){
        this.maxAddr = maxAddr;
    }

    public void setInterruptResult(JSONObject interruptResult){
        this.interruptResult = interruptResult;
    }

    private void initRegisterValue(){
        for(int i = 0; i < Register.COMMONREGISTERMAP.size();i++){
            commonRegisterContent.put(i,0L);
        }
        //初始化$sp内容，指向栈底
        commonRegisterContent.put(Register.COMMONREGISTERMAP.get("$sp"),mmu.getVolume());
        coprocessorRegisterContent.put(12,0L);
        coprocessorRegisterContent.put(13,0L);
        coprocessorRegisterContent.put(14,0L);
        hiRegister = loRegister = 0L;
    }

    public void resetMipsCpu(){
        lastPc = pc = 0L;
        intMask = 0;
        maxAddr = 0;
        initRegisterValue();
        mmu = new MemoryManageUnit();
        interruptException = new InterruptException();
        interruptResult = null;
        fileMap = new HashMap<>();
        fileDescriptionSymbol = 0;
    }

    public static MipsCpu getInstance(){
        if(Instance==null)
            Instance = new MipsCpu();
        return Instance;
    }

    public void setROMLength(int length){
        Instance.mmu.setRomLength(length);
    }

    private void updateRegisterValue(int regNum, long value) throws SimulatorException {
        if(regNum!=0){
            commonRegisterContent.put(regNum,value);
        }
        else {
            throw new SimulatorException("Error: Register $zero equals 0 as a constant and can't be changed.");
        }
    }

    private int getSignedIntFromLong(long value){
        if((value&0x80000000L) !=0){
            return (int)(value-0x80000000L);
        }
        else{
            return Integer.valueOf(value+"",10);
        }
    }

    public void handleInterruptException(int initialStatus) throws UnsupportedEncodingException, SimulatorException, InterruptException {
        intMask = 1;
        pc = lastPc;    //恢复PC
        if(initialStatus == 0){
            executeWholeProgram();
        }
        else{
            singleStepDebugProgram();
        }
        intMask = 0;
    }

    public void executeWholeProgram() throws UnsupportedEncodingException, SimulatorException, InterruptException {
        while(pc<maxAddr){
            executeCurrentInstruction(interruptResult);
        }
    }

    public int singleStepDebugProgram() throws UnsupportedEncodingException, SimulatorException, InterruptException {
        System.out.println(maxAddr);
        if(pc<maxAddr){
            executeCurrentInstruction(interruptResult);
            if(pc<maxAddr)
                return 1;
            else return 0;
        }
        else return 0;
    }

    public static String getKey(int opc,int func, int rs){
        String key = "";
        if(opc == 0 || opc == 28 || opc == 16)
            key = "(" + opc + "," + func + ")";
        else
            key = "(" + opc + ",0)";
        if(key.equals("(16,0)")){
            key = key+"," + rs;
        }
        return key;
    }
    //假设在程序运行前，已经将数据、程序段全部存入内存
    private void executeCurrentInstruction(JSONObject readResult) throws SimulatorException, UnsupportedEncodingException, InterruptException {
        //IF 取指令
        int machineCode = ((mmu.getMemoryMap().get(pc)&0xFFFF)<<16) + (mmu.getMemoryMap().get(pc+1)&0xFFFF);
        lastPc = pc;
        pc+=2;
        System.out.println("pc: " + pc);
        //未中断
        if(intMask == 0){
            //ID 译码
            boolean dispFlag = false;
            //for R
            int opc,rs,rt,rd,sa,func;
            //for I
            short dat,dot,ofs;
            //for J
            int adr;
            //for C
            int rc;
            opc = ((machineCode&0xFC000000) >> 26)&0x0000003F;
            rs = ((machineCode&0x03E00000) >> 21)&0x0000001F;
            rt = ((machineCode&0x001F0000) >> 16)&0x0000001F;
            rc = rd =((machineCode&0x0000F800) >> 11)&0x0000001F;
            sa = ((machineCode&0x000007C0) >> 6)&0x0000001F;
            func = (machineCode&0x000003F);
            ofs = dot = dat = (short)(machineCode&0x0000FFFF);
            adr = ((machineCode&0x03FFFFFF));

            //统一key格式
            String key = getKey(opc,func, rs);
            System.out.println("================== key is: " + key);
            if(!Mnemonic.MNEMONICMAP.containsKey(key)){
                throw new SimulatorException("Error: Can't execute invalid instruction '" + machineCode + "' at address [0x" + String.format("%5x",pc-2) +"]");
            }
            else{
                switch (Mnemonic.MNEMONICMAP.get(key)){
                    //这里写反汇编最主要的函数处理
                    case "lui":
                    {
                        //lui rt,dat->rt=dat<<16
                        updateRegisterValue(rt,((long)dat<<16));
                        break;
                    }
                    case "add": //add rd,rs,rt->rd=rs+rt
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        int rtIntValue = getSignedIntFromLong(rtLongValue);
                        updateRegisterValue(rd,rsIntValue+rtIntValue);
                        break;
                    }
                    case "sub": //sub rd,rs,rt->rd=rs-rt
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        int rtIntValue = getSignedIntFromLong(rtLongValue);
                        updateRegisterValue(rd,rsIntValue-rtIntValue);
                        break;
                    }
                    case "slt": //slt rd,rs,rt->if(rs<rt) rd=1 else rd = 0
                    {
                        //有符号的小于判断
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        int rtIntValue = getSignedIntFromLong(rtLongValue);
                        if(rsIntValue<rtIntValue){
                            updateRegisterValue(rd,1L);
                        }
                        else updateRegisterValue(rd,0L);
                        break;
                    }
                    case "sltu":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        if(rsLongValue<rtLongValue){
                            updateRegisterValue(rd,1L);
                        }
                        else updateRegisterValue(rd,0L);
                        break;
                    }
                    case "and":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        updateRegisterValue(rd,(rsLongValue&rtLongValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "or":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        updateRegisterValue(rd,(rsLongValue|rtLongValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "xor":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        updateRegisterValue(rd,(rsLongValue^rtLongValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "nor":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        updateRegisterValue(rd,(~(rsLongValue|rtLongValue))&0xFFFFFFFFL);
                        break;
                    }
                    case "sllv": //sllv rd,rs,rt->rd = rs<<rt,左移，多出来的位数使用0填充
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        int rtIntValue = getSignedIntFromLong(rtLongValue);
                        updateRegisterValue(rd,(rsIntValue<<rtIntValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "srlv": //srlv rd,rs,rt->rd = rs>>rt，右移，多出来的位数使用0填充
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        int rtIntValue = getSignedIntFromLong(rtLongValue);
                        updateRegisterValue(rd,(rsIntValue>>>rtIntValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "srav":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        int rtIntValue = getSignedIntFromLong(rtLongValue);
                        updateRegisterValue(rd,(rsIntValue>>rtIntValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "mul":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        int rtIntValue = getSignedIntFromLong(rtLongValue);
                        updateRegisterValue(rd,(rsIntValue*rtIntValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "sll":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        updateRegisterValue(rd,(rsIntValue<<sa)&0xFFFFFFFFL);
                        break;
                    }
                    case "srl":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        updateRegisterValue(rd,(rsIntValue>>>sa)&0xFFFFFFFFL);
                        break;
                    }
                    case "sra":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        updateRegisterValue(rd,(rsIntValue>>sa)&0xFFFFFFFFL);
                        break;
                    }
                    case "addi":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        int datValue = dat;
                        updateRegisterValue(rt,(rsIntValue+datValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "slti":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        int datValue = dat;
                        if(rsIntValue<datValue){
                            updateRegisterValue(rt,1L);
                        }
                        else updateRegisterValue(rt,0L);
                        break;
                    }
                    case "sltiu":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long datValue = Long.valueOf(String.format("%x",dat),16);
                        if(rsLongValue<datValue){
                            updateRegisterValue(rt,1L);
                        }
                        else updateRegisterValue(rt,0L);
                        break;
                    }
                    case "andi":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long datValue = Long.valueOf(String.format("%x",dat),16);
                        updateRegisterValue(rt, (rsLongValue&datValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "ori":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long datValue = Long.valueOf(String.format("%x",dat),16);
                        updateRegisterValue(rt, (rsLongValue|datValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "xori":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long datValue = Long.valueOf(String.format("%x",dat),16);
                        updateRegisterValue(rt, (rsLongValue^datValue)&0xFFFFFFFFL);
                        break;
                    }
                    case "beq": //beq rs,rt,label-> if(rs==rt) goto label
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        int jumpTo = (ofs<<1)+(int)(pc&0xFFFFFFFFL);
                        if(rsLongValue==rtLongValue){
                            pc = Long.parseLong(jumpTo+"",10);
                        }
                        break;
                    }
                    case "bne":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        int jumpTo = (ofs<<1)+(int)(pc&0xFFFFFFFFL);
                        if(rsLongValue!=rtLongValue){
                            pc = Long.parseLong(jumpTo+"",10);
                        }
                        break;
                    }
                    case "bgezal":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        int rsIntValue = getSignedIntFromLong(rsLongValue);
                        int jumpTo = (ofs<<1)+(int)(pc&0xFFFFFFFFL);
                        if(rsIntValue>=0){
                            pc = Long.parseLong(jumpTo+"",10);
                        }
                        break;
                    }
                    case "lw":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        commonRegisterContent.put(rt,mmu.lw(rsLongValue+dat));
                        break;
                    }
                    case "lwx":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        commonRegisterContent.put(rt,mmu.lwx(rsLongValue+dat));
                        break;
                    }
                    case "lh":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        commonRegisterContent.put(rt,mmu.lh(rsLongValue+dat));
                        break;
                    }
                    case "lhx":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        commonRegisterContent.put(rt, mmu.lhx(rsLongValue + dat));
                        break;
                    }
                    case "lhu":
                    {
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        commonRegisterContent.put(rt,mmu.lhu(rsLongValue+dat));
                        break;
                    }
                    case "lhux":{
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        commonRegisterContent.put(rt,mmu.lhux(rsLongValue+dat));
                        break;
                    }
                    case "sw":
                    {
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        mmu.sw(rsLongValue+dat,rtLongValue);
                        break;
                    }
                    case "swx":
                    {
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        mmu.swx(rsLongValue+dat,rtLongValue);
                        break;
                    }
                    case "sh":
                    {
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        mmu.sh(rsLongValue+dat,rtLongValue);
                        break;
                    }
                    case "shx":
                    {
                        long rtLongValue = commonRegisterContent.get(rt) & 0xFFFFFFFFL;
                        long rsLongValue = commonRegisterContent.get(rs) & 0xFFFFFFFFL;
                        mmu.shx(rsLongValue+dat,rtLongValue);
                        break;
                    }
                    case "j":
                    {
                        pc = (pc&0xF8000000L) + adr<<1;
                        break;
                    }
                    case "jal":
                    {
                        commonRegisterContent.put(31,pc);
                        pc = (pc&0xF8000000L) + adr<<1;
                        break;
                    }
                    case "jr":
                    {
                        pc = commonRegisterContent.get(rs)& 0xFFFFFFFFL;
                        break;
                    }
                    case "jalr":
                    {
                        commonRegisterContent.put(rt,pc);
                        pc = commonRegisterContent.get(rs)& 0xFFFFFFFFL;
                        break;
                    }
                    case "mthi":
                    {
                        hiRegister = commonRegisterContent.get(rs)& 0xFFFFFFFFL;
                        break;
                    }
                    case "mtlo":
                    {
                        loRegister = commonRegisterContent.get(rs)& 0xFFFFFFFFL;
                        break;
                    }
                    case "mfhi":
                    {
                        commonRegisterContent.put(rd,hiRegister& 0xFFFFFFFFL);
                        break;
                    }
                    case "mflo":
                    {
                        commonRegisterContent.put(rd,loRegister& 0xFFFFFFFFL);
                        break;
                    }
                    case "eret":
                    {
                        pc = coprocessorRegisterContent.get(14);
                        coprocessorRegisterContent.put(12,coprocessorRegisterContent.get(12)&(~intMask));
                        break;
                    }
                    case "syscall":
                    {
                        //从$v0中获取syscall号
                        long intRequest = commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$v0"));
                        switch ((int)intRequest){
                            //选择中断函数执行
                            case 1://print_int
                            {
                                long paramLongValue = commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$a0"));
//                            int paramIntValue = getSignedIntFromLong(paramLongValue);
                                long dispPc = mmu.getDispPC();
                                mmu.sw(dispPc,paramLongValue);
                                mmu.setDispPC(dispPc+2);
                                mmu.refreshDisplay(0,dispPc,dispPc+2);
                                break;
                            }
                            case 4://print_string
                            {
                                //传的是string的地址，假设string以C语言字符串形式定义，末尾标志字符为'\0'，以GB2312存储汉字字符，以ASCII字符存储英文字符
                                //转为UTF8字符串传出给显示
                                long paramLongValue = commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$a0"));
                                int count = 0;
                                for(long addr = paramLongValue;mmu.getMemoryMap().get(addr)!=0;addr++){
                                    long dispPc = mmu.getDispPC();
                                    mmu.sh(dispPc,mmu.getMemoryMap().get(addr));
                                    mmu.setDispPC(dispPc+1);
                                    count++;
                                }
                                mmu.refreshDisplay(1,paramLongValue,paramLongValue+count);
                                break;
                            }
                            case 11://print_char
                            {
                                long paramLongValue = commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$a0"));
                                long dispPc = mmu.getDispPC();
                                mmu.sh(dispPc, paramLongValue);
                                mmu.setDispPC(dispPc+1);
                                mmu.refreshDisplay(2,dispPc,dispPc+1);
                                break;
                            }
                            case 5://read_int
                            {
                                //给一个信号去中断，接收键盘的输入
                                interruptException.setIntRequest(5);
                                throw interruptException;
                            }
                            case 8://read_string
                            {
                                interruptException.setIntRequest(8);
                                throw interruptException;
                            }
                            case 12://read_char
                            {
                                interruptException.setIntRequest(12);
                                throw interruptException;
                            }
                            case 13://open
                            {
                                //为了简化操作仅仅需提供文件名，默认以只读方式打开
                                long fileNameAddr = commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$a0"));
                                String fileName = mmu.readCFormatString(fileNameAddr);
                                System.out.println("fileName: " + fileName);
                                if(fileName!=null){
                                    DiskFileSystem system = DiskFileSystem.getInstance();
                                    JSONObject jsonObject = system.getFilePtr(fileName.substring(0,fileName.indexOf(".")),fileName.substring(fileName.indexOf(".")+1));
                                    commonRegisterContent.put(Register.COMMONREGISTERMAP.get("$a0"),(long)fileDescriptionSymbol);
                                    fileMap.put(fileDescriptionSymbol,jsonObject);
                                    fileDescriptionSymbol++;
                                }
                                else {
                                    commonRegisterContent.put(Register.COMMONREGISTERMAP.get("$a0"),0L);
                                }
                                break;
                            }
                            case 14://read
                            {
                                int filePointer = (int)(commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$a0"))&0xFFFFFFFFL);
                                if(fileMap.containsKey(filePointer)){
                                    JSONObject jsonObject = fileMap.get(filePointer);
                                    String content = jsonObject.getString("fileContent");
                                    byte[] contentBytes = content.getBytes("gbk");
                                    long addr = commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$a1"));
                                    long length = commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$a2"));
                                    int j = 0,count = 0;
                                    short[] shorts = new short[Math.max((int)(length&0xFFFFFFFFL),contentBytes.length)];
                                    for(int i = 0; i<contentBytes.length&&j<length;i++){
//                                        System.out.println("content char at[" +i+"]" + content.charAt(i));
                                        if((contentBytes[i]&0x80)==0){
                                            shorts[j++] = (short)((contentBytes[i]&0xFF)&0xFF);
                                            mmu.sh(addr+j-1,shorts[j-1]);
                                        }
                                        else {
                                            if(count==0){
                                                shorts[j] = (short)(((contentBytes[i]&0xFF)<<8)&0xFF00);
                                                count++;
                                            }
                                            else {
                                                shorts[j++] += (short)((contentBytes[i]&0xFF)&0xFF);
                                                mmu.sh(addr+j-1,shorts[j-1]);
                                                count=0;
                                            }
                                        }
                                    }
                                    commonRegisterContent.put(Register.COMMONREGISTERMAP.get("$a0"),(long)shorts.length);
                                }
                                break;
                            }
                            case 16://close
                            {
                                int filePointer = (int)(commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$a0"))&0xFFFFFFFFL);
                                if(fileMap.containsKey(filePointer)){
                                    fileMap.remove(filePointer);
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case "mult":
                    case "multu":
                    {
                        long rsLongValue = commonRegisterContent.get(rs)& 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt)& 0xFFFFFFFFL;
                        long product = rsLongValue*rtLongValue;
                        hiRegister = product>>32;
                        loRegister = product&0xFFFFFFFFL;
                        break;
                    }
                    case "div":
                    case "divu":
                    {
                        long rsLongValue = commonRegisterContent.get(rs)& 0xFFFFFFFFL;
                        long rtLongValue = commonRegisterContent.get(rt)& 0xFFFFFFFFL;
                        loRegister = rsLongValue/rtLongValue;
                        hiRegister = rsLongValue%rtLongValue;
                        break;
                    }
                    case "mfc0":
                    {
                        commonRegisterContent.put(rt,coprocessorRegisterContent.get(rc)& 0xFFFFFFFFL);
                        break;
                    }
                    case "mtc0":
                    {
                        coprocessorRegisterContent.put(rc,commonRegisterContent.get(rt)& 0xFFFFFFFFL);
                        break;
                    }
                }
            }
        }
        else{
            //中断状态下
            System.out.println("MIPS CPU set interrupt result： " + readResult);
            switch (readResult.getInteger("intRequest")){
                case 5:{//int
                    int res = readResult.getInteger("readInt");
                    commonRegisterContent.put(Register.COMMONREGISTERMAP.get("$v0"),((long)res)&0xFFFFFFFFL);
                    break;
                }
                case 8:{//string
                    //$a0 -> 起始地址
                    //$a1 -> 读取长度
                    //默认一个字符占16位-》1zjie
                    long start = commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$a0"));
                    long length = commonRegisterContent.get(Register.COMMONREGISTERMAP.get("$a1"));
                    byte[] bytes = readResult.getBytes("readStringBytes");
                    short[] zjies = new short[bytes.length];
                    int j = 0,count=0;
                    for(int i = 0; i<bytes.length;i++){
                        if((bytes[i]&0x80) == 0){
                            zjies[j] = bytes[i];
                            j++;
                        }
                        else{
                            if(count == 0){
                                zjies[j] = (short)((bytes[i]&0xFF)<<8);
                                count++;
                            }
                            else{
                                zjies[j] += (short)((bytes[i]&0xFF)&0xFF);
                                count = 0;
                                j++;
                            }
                        }
                    }
                    for(long i = 0; i< length&&i<zjies.length;i++){
                        mmu.sh(start+i,zjies[(int)i]);
                    }
                    break;
                }
                case 12:{//char
                    byte[] charBytes = readResult.getBytes("readCharBytes");
                    for(int i = 0; i<charBytes.length;i++)
                        System.out.println(String.format("%x",charBytes[i]));
                    short res = (short)((charBytes[0] & 0xFF)<<8);
                    res += (short)(charBytes[1]&0xFF);
                    commonRegisterContent.put(Register.COMMONREGISTERMAP.get("$a0"),((long)res)&0xFFFFL);
                    break;
                }
            }
            intMask = 0;
            interruptResult = null;
        }
    }

    public JSONArray getCommonRegisterContent(){
        JSONArray registerArray = new JSONArray();
        for(Map.Entry<Integer, Long> entry : commonRegisterContent.entrySet()){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id",entry.getKey());
            jsonObject.put("name",Register.REVERSECOMMONMAP.get(entry.getKey()));
            jsonObject.put("content",entry.getValue());
            registerArray.add(jsonObject);
        }
        return registerArray;
    }

    public JSONArray getCoprocessorRegisterContent(){
        JSONArray registerArray = new JSONArray();
        for(Map.Entry<Integer, Long> entry: coprocessorRegisterContent.entrySet()){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id",entry.getKey()-12);
            jsonObject.put("content",entry.getValue());
            registerArray.add(jsonObject);
        }
        JSONObject hi = new JSONObject();
        hi.put("id",3);
        hi.put("content",hiRegister);
        registerArray.add(hi);
        JSONObject lo = new JSONObject();
        lo.put("id",4);
        lo.put("content",loRegister);
        registerArray.add(lo);
        return registerArray;
    }

    private String getOneLineAsmCode(int machineCode){
        Disassembler disassembler = new Disassembler();
        disassembler.disassembleCode(getHexFormat(machineCode)+"");
        return disassembler.getAssembleCodeMap().get(0);
    }

    public JSONObject getControlPanel(){
        int machineCode = ((mmu.getMemoryMap().get(lastPc)&0xFFFF)<<16) + (mmu.getMemoryMap().get(lastPc+1)&0xFFFF);
        String asmCode = getOneLineAsmCode(machineCode);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("instr", asmCode);
        jsonObject.put("op",((machineCode&0xFC000000)>>26)&0x0000003F);
        jsonObject.put("rs",((machineCode&0x03E00000) >> 21)&0x0000001F);
        jsonObject.put("rt",((machineCode&0x001F0000) >> 16)&0x0000001F);
        jsonObject.put("rd",((machineCode&0x0000F800) >> 11)&0x0000001F);
        jsonObject.put("sa",((machineCode&0x000007C0) >> 6)&0x0000001F);
        jsonObject.put("func",(machineCode&0x000003F));
        jsonObject.put("dat",machineCode&0x0000FFFF);
        jsonObject.put("adr",(machineCode&0x03FFFFFF));
        jsonObject.put("memory",machineCode);
        return jsonObject;
    }

    public JSONObject getNextInstruction(){
        System.out.println("next PC:" + pc);
        int machineCode = ((mmu.getMemoryMap().get(pc)&0xFFFF)<<16) + (mmu.getMemoryMap().get(pc+1)&0xFFFF);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("pc",pc);
        jsonObject.put("inst",getOneLineAsmCode(machineCode));
        return jsonObject;
    }

    public String getMemoryInfo(){
        StringBuilder builder = new StringBuilder();
        //一排显示32个zjie单元
        Map<Long, Short> map = mmu.getMemoryMap();
        for(long i =0; i < map.size(); i++){
            if(i%16 == 0){
                builder.append("[0x"+String.format("%-4x", i)+"]: ");
            }
            builder.append(getHexFormat(map.get(i)) + " ");
            if(i%8==7 && i%16!=15){
                builder.append(" - ");
            }
            if(i%16 == 15){
                builder.append("\r\n");
            }
        }
        return builder.toString();
    }

    public String getProgramInfo(){
        //把程序段所在内存翻译成汇编代码
        StringBuilder builder = new StringBuilder();
        for(long i = 0; i<MemoryManageUnit.PROGRAMVOLUME;i+=2){
            int machineCode = ((mmu.getMemoryMap().get(i)&0xFFFF)<<16) + (mmu.getMemoryMap().get(i+1)&0xFFFF);
            builder.append(getHexFormat(machineCode) + "\n");
        }
        Disassembler disassembler = new Disassembler();
        disassembler.disassembleCode(builder.toString());
//        System.out.println(builder.toString());
        builder.setLength(0);
        for(Map.Entry<Integer, String> entry:disassembler.getAssembleCodeMap().entrySet()){
            builder.append("[0x"+String.format("%-4x",entry.getKey()*2)+"] " + entry.getValue()+"\r\n");
        }
        return builder.toString();
    }

    private String getHexFormat(short s){
        //以无符号数的形式转成4位十六进制
        String hex = String.format("%x",s);
        String zeros = "0000";
        if(hex.length()<4){
            hex = zeros.substring(0,4-hex.length()) + hex;
        }
        return hex;
    }

    private String getHexFormat(int i){
        String hex = String.format("%x",i);
        String zeros = "00000000";
        if(hex.length()<8){
            hex = zeros.substring(0,8-hex.length()) + hex;
        }
        return hex;
    }

    public void insertMachineCodeToMemory(int i, short s) throws SimulatorException {
        long addr = (long)i;
        mmu.sh(addr,s);
    }

    public void executeROM() throws UnsupportedEncodingException, SimulatorException, InterruptException {
        executeWholeProgram();
    }

    public int getROMLength(){
        return mmu.getRomLength();
    }

}
