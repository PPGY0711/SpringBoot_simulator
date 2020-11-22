package com.jz.simulator.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jz.simulator.exception.InterruptException;
import com.jz.simulator.exception.SimulatorException;
import com.jz.simulator.util.EncodingUtil;
import com.jz.simulator.util.SimulatorUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;


@Controller
public class SimController {

    @RequestMapping({"/","/simulator"})
    public String getSimulator(){
        return "simulator";
    }

    @ResponseBody
    @RequestMapping("/rebootSimulator")
    public JSONObject rebootSimulator(){
        return SimulatorUtil.rebootSimulator();
    }

    @ResponseBody
    @RequestMapping("/sendBinFile")
    public JSONObject getBinFile(@RequestBody String jsonStr){
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        JSONArray data = jsonObject.getJSONArray("binData");
        JSONObject retObject = new JSONObject();
        int count = 0;
        for(int i = 0; i < data.size(); i+=2){
            JSONObject highObject = data.getJSONObject(i);
            int high = highObject.getInteger("value");
            JSONObject lowObject = data.getJSONObject(i+1);
            int low = lowObject.getInteger("value");
            short s = (short)((high&0xFF)<<8);
            s +=  + (short)(low&0xFF);
            try{
                SimulatorUtil.insertMachineCodeToMemory(i/2,s);
                count++;
            }catch (SimulatorException e){
                retObject.put("errorMsg","Wrong Address Used! Load ROM File failed!");
                return retObject;
            }
        }
        SimulatorUtil.setROMLength(count);
        return SimulatorUtil.getCpuInfo();
    }

    @ResponseBody
    @RequestMapping("/executeMachineCode")
    public JSONObject executeMachineCode(@RequestBody String jsonStr){
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        String data = jsonObject.getString("data");
        JSONObject retObject = new JSONObject();
        System.out.println(data);
        if(data == null || data.equals("")){
            //执行ROM的代码
            try {
                System.out.println("execute ROM");
                retObject = SimulatorUtil.executeProgramInROM();
            } catch (InterruptException e) {
                retObject.put("intRequest",e.getIntRequest());
                retObject.put("errorMsg",e.getMessage());
            }catch (Exception e){
                retObject.put("errorMSg",e.getMessage());
            }
            return retObject;
        }
        else{
            try {
                System.out.println("execute MachineCode");
                retObject = SimulatorUtil.executeProgram(data);
            }catch (InterruptException e) {
                retObject.put("intRequest",e.getIntRequest());
                retObject.put("errorMsg",e.getMessage());
            }catch (Exception e){
                retObject.put("errorMSg",e.getMessage());
            }
            return retObject;
        }
    }

    @ResponseBody
    @RequestMapping("/enterDebugMode")
    public JSONObject enterDebugMode(@RequestBody String jsonStr, HttpServletRequest request){
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        String data = jsonObject.getString("data");
        JSONObject retObject = new JSONObject();
        if(data == null || data.equals("")){
            int size = SimulatorUtil.getROMLength();
            request.setAttribute("dataSize",size);
            retObject = SimulatorUtil.getCpuInfo();
            retObject.put("dataSize",size);
            return retObject;
        }
        else{
            try {
                int size = SimulatorUtil.insertWholeProgram(data);
                request.setAttribute("dataSize",size);
                retObject = SimulatorUtil.getCpuInfo();
                retObject.put("dataSize",size);
            } catch (SimulatorException e) {
                request.setAttribute("dataSize",-1);
                retObject.put("errorMsg",e.getMessage());
                retObject.put("dataSize",-1);
            }
            return retObject;
        }
    }

    @ResponseBody
    @RequestMapping("/singleStepDebug")
    public JSONObject singleStepDebug(@RequestBody String jsonStr){
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        int dataSize = jsonObject.getInteger("dataSize");
        JSONObject retObject = new JSONObject();
        try {
            retObject = SimulatorUtil.singleStepDebugProgram(dataSize);
        }catch (InterruptException e) {
            retObject.put("intRequest",e.getIntRequest());
            retObject.put("errorMsg",e.getMessage());
        }catch (Exception e){
            retObject.put("errorMSg",e.getMessage());
        }
        return retObject;
    }

    @ResponseBody
    @RequestMapping("/handleInterruptRequest")
    public JSONObject handleInterruptRequest(@RequestBody String jsonStr) throws UnsupportedEncodingException {
        System.out.println(jsonStr);
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        int intRequest = jsonObject.getInteger("intRequest");
        switch (intRequest){
            case 5:{
                String readIntStr = jsonObject.getString("data");
                int readInt = Integer.parseInt(readIntStr.trim().replaceAll("\\s+",""),10);
                jsonObject.put("readInt",readInt);
                break;
            }
            case 8:
            {
                String s = jsonObject.getString("data").replaceAll("\\s+"," ").trim();
                jsonObject.put("readStringBytes", EncodingUtil.getGB2312Bytes(s));
                break;
            }
            case 12:
            {
                String s = jsonObject.getString("data").replaceAll("\\s+","");
                byte[] charBytes = new byte[2];
                int j = 0;
                if(s!=null && !s.equals("")){
                    byte[] gbkArr = EncodingUtil.getGB2312Bytes(s);
                    for(int i = 0; i < gbkArr.length && i <= 1;i++){
                        if((gbkArr[i]&0x80)!=0){
                            charBytes[j] = gbkArr[i];
                            j++;
                        }
                        if(i ==0 && (gbkArr[i]&0x80)==0){
                            charBytes[j] = 0;
                            j++;
                        }
                        if(i==1 && (gbkArr[i]&0x80)==0){
                            charBytes[j] = gbkArr[i];
                            j++;
                        }
                    }
                }
                jsonObject.put("readCharBytes",charBytes);
                break;
            }
        }
        try {
            SimulatorUtil.setInterruptResult(jsonObject);
            if(jsonObject.getInteger("status")==0){
                jsonObject.put("refreshData",SimulatorUtil.getCpuInfo());
            }
        } catch (SimulatorException e) {
            e.printStackTrace();
        } catch (InterruptException e) {
            e.printStackTrace();
        }
        jsonObject.put("msg","Return from interrupt Request");
        return jsonObject;
    }

}
