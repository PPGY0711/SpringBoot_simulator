package com.jz.simulator.common;

import java.util.HashMap;
import java.util.Map;
/**
 * 寄存器
 */
public class Register {
    private static final String[] COMMONREGISTERNAMES = {"$zero","$at","$v0","$v1","$a0","$a1","$a2","$a3",
            "$t0","$t1","$t2","$t3","$t4","$t5","$t6","$t7",
            "$s0","$s1","$s2","$s3","$s4","$s5","$s6","$s7",
            "$t8","$t9","$k0","$k1","$gp","$sp","$fp","$ra"};

    public static final Map<String, Integer> COMMONREGISTERMAP = new HashMap<>();

    public static final Map<String, Integer> COPROCESSORREGISTERMAP = new HashMap<>();

    public static final Map<Integer, String> REVERSECOMMONMAP = new HashMap<>();

    public static final Map<Integer, String> REVERSECOPROCESSORMAP = new HashMap<>();

    //初始化Map
    static{
        for(int i=0;i<COMMONREGISTERNAMES.length;i++){
            COMMONREGISTERMAP.put(COMMONREGISTERNAMES[i],i);
            REVERSECOMMONMAP.put(i,COMMONREGISTERNAMES[i]);
        }
        COPROCESSORREGISTERMAP.put("STATUS",12);
        COPROCESSORREGISTERMAP.put("CAUSE",13);
        COPROCESSORREGISTERMAP.put("EPC",14);
        REVERSECOPROCESSORMAP.put(12,"STATUS");
        REVERSECOPROCESSORMAP.put(13,"CAUSE");
        REVERSECOPROCESSORMAP.put(14,"EPC");
    }
}
