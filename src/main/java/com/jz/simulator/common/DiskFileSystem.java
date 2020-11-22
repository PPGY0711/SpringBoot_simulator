package com.jz.simulator.common;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.core.io.DefaultResourceLoader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * 磁盘文件
 * 读取磁盘vdisk.vhd
 * 格式为FAT16
 * 小端存储
 */

public class DiskFileSystem {
    private static final String DISKNAME = "vdisk.vhd";
    private static final DefaultResourceLoader LOADER = new DefaultResourceLoader();
    private static final int VOLUME = 16777216; //2^24=16MB
    private int relativeSectionNum;    //相对扇区数
    private int sectionBytes;        //每扇区字节数
    private int logicSectionNum;     //逻辑扇区总数量
    private int reserveSectionNum;   //保留扇区数
    private int hiddenSectionNum;    //隐藏扇区数
    private int fatSize16;           //FAT表大小
    private int fat1Location;        //FAT表1位置
    private int fat2Location;        //FAT表2位置
    private int rootDirectory;       //根目录位置
    private int dataAndSubDirectory; //数据及子目录位置
    private int sectionPerCluster;   //每簇扇区数
    private int clusterBias;         //簇号偏移量（从0x02开始计为0）
    private int directorySectionNum; //目录所占扇区数
    private byte[] diskContent;      //磁盘内容
    private JSONArray fileArray;
    private static DiskFileSystem Instance = null;

    private DiskFileSystem(){
        try{
            InputStream is = LOADER.getResource("classpath:static/disk/"+DISKNAME).getInputStream();
            diskContent = new byte[VOLUME];
            int realVolume = is.read(diskContent);
            relativeSectionNum = (diskContent[0x1c6]&0xFF) | ((diskContent[0x1c6+1]&0xFF)<<8) | ((diskContent[0x1c6+2]&0xFF)<<16) | ((diskContent[0x1c6+3]&0xFF)<<24);
            logicSectionNum = (diskContent[0x1ca]&0xFF) | ((diskContent[0x1ca+1]&0xFF)<<8) | ((diskContent[0x1ca+2]&0xFF)<<16) | ((diskContent[0x1ca+3]&0xFF)<<24);
            int logic0 = relativeSectionNum*512;
            sectionBytes = (diskContent[logic0+0x0b]&0xFF) + ((diskContent[logic0+0x0c]&0xFF)<<8);
            sectionPerCluster = (diskContent[logic0+0x0d]&0xFF);
            reserveSectionNum = (diskContent[logic0+0x0e]&0xFF) + ((diskContent[logic0+0x0f]&0xFF)<<8);
            fatSize16 = (diskContent[logic0+0x16]&0xFF) + ((diskContent[logic0+0x17]&0xFF)<<8);
            hiddenSectionNum = (diskContent[logic0+0x1c]&0xFF) | ((diskContent[logic0+0x1c+1]&0xFF)<<8) | ((diskContent[logic0+0x1c+2]&0xFF)<<16) | ((diskContent[logic0+0x1c+3]&0xFF)<<24);
            clusterBias = 2;
            directorySectionNum = 32;
            fat1Location = logic0 + reserveSectionNum*512;
            fat2Location = fat1Location + fatSize16*512;
            rootDirectory = fat2Location + fatSize16*512;
            dataAndSubDirectory = rootDirectory + directorySectionNum*512;
            fileArray = getFiles();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * 单例模式创建磁盘文件
     * @return
     */
    public static DiskFileSystem getInstance() {
        if(Instance==null)
        {
            Instance = new DiskFileSystem();
        }
        return Instance;
    }

    /**
     * 列出磁盘文件目录信息
     * @return
     */
    private JSONArray getFiles(){
        JSONArray array = new JSONArray();
        byte[] backupFileNameBytes = {(byte)0xE5,(byte)0xC2,(byte)0xBD,(byte)0xA8,(byte)0xCE,(byte)0xC4,(byte)0x7E,(byte)0x31};
        String tmpStr = new String(backupFileNameBytes);
        //初始化目录寻址相关变量
        int clusterIndex = (0x06)<<1;
        byte[] fatTable = fillBytes(diskContent, fat1Location, fat2Location-1);
        for(int i = rootDirectory; i < dataAndSubDirectory ; i+= 32){
            JSONObject fileInfoJson = new JSONObject();
            byte[] fileInfoBytes = DiskFileSystem.fillBytes(diskContent, i,i+31);
            if(isValidDirectory(fileInfoBytes)){
                //分离文件名
                byte[] fileNameBytes = fillBytes(fileInfoBytes,0,7);
                fileInfoJson.put("fileName",(new String(fileNameBytes)).trim());
                //后缀
                byte[] suffixBytes = fillBytes(fileInfoBytes,8,10);
                fileInfoJson.put("suffix",(new String(suffixBytes)).trim());
                //文件长度
                int fileLength = (fileInfoBytes[0x1c]&0xFF) + ((fileInfoBytes[0x1c+1]&0xFF)<<8) + ((fileInfoBytes[0x1c+2]&0xFF)<<16) + ((fileInfoBytes[0x1c+3]&0xFF)<<24);
                fileInfoJson.put("size",fileLength);
                //创建时间
                int createTime = (fileInfoBytes[0x0e]&0xFF) + ((fileInfoBytes[0x0e+1]&0xFF)<<8);
                fileInfoJson.put("createTime", String.format("%d:%d:%d", (createTime&0xF800)>>11,(createTime&0x07E0)>>5,(createTime&0x001F)<<1));
                int createDate = (fileInfoBytes[0x10]&0xFF) + ((fileInfoBytes[0x10+1]&0xFF)<<8);
                fileInfoJson.put("createDate", String.format("%d-%d-%d", 1980+((createDate&0xFE00)>>9), (createDate&0x01E0)>>5, (createDate&0x001F)));
                //修改时间
                int modifyTime = (fileInfoBytes[0x16]&0xFF) + ((fileInfoBytes[0x16+1]&0xFF)<<8);
                fileInfoJson.put("modifyTime", String.format("%d:%d:%d", (modifyTime&0xF800)>>11,(modifyTime&0x07E0)>>5,(modifyTime&0x001F)<<1));
                int modifyDate = (fileInfoBytes[0x18]&0xFF) + ((fileInfoBytes[0x18+1]&0xFF)<<8);
                fileInfoJson.put("modifyDate", String.format("%d-%d-%d", 1980+((modifyDate&0xFE00)>>9), (modifyDate&0x01E0)>>5, (modifyDate&0x001F)));
                //首簇
                int firstCluster = ((fileInfoBytes[0x14]&0xFF) << 16) + ((fileInfoBytes[0x14+1]&0xFF) << 24) + ((fileInfoBytes[0x1A]&0xFF)) + ((fileInfoBytes[0x1A+1]&0xFF) << 8);
                fileInfoJson.put("firstCluster", firstCluster-clusterBias);
//                System.out.println(String.format("%8x",firstCluster&0xFFFF));
                if((firstCluster&0xFFFF)>0x06 && !(new String(fileNameBytes).equals(tmpStr)) && 1980+((createDate&0xFE00)>>9) != 1980) {
                    ArrayList<Integer> clusterList = new ArrayList<>();
                    clusterList.add(firstCluster - clusterBias);
                    clusterIndex += 2; //消除上一次的目录寻址偏差
                    int nextClusterNum = (fatTable[clusterIndex] & 0xFF) + ((fatTable[clusterIndex + 1] & 0xFF) << 8);
                    while (nextClusterNum != 0xFFFF) {
                        clusterList.add(nextClusterNum - clusterBias);
                        clusterIndex += 2;
                        //System.out.println(String.format("%x",clusterIndex));
                        nextClusterNum = (fatTable[clusterIndex] & 0xFF) + ((fatTable[clusterIndex + 1] & 0xFF) << 8);
//                        System.out.println("clusterIndex: "+ String.format("%x",clusterIndex) +", next ClusterNum: " + String.format("%x",nextClusterNum));
                    }
//                    System.out.println("size: " + clusterList.size());
                    byte[] fileContent = new byte[fileLength];
                    int remainSize = fileLength;
                    int readIndex = 0;
                    for (int j = 0; j < clusterList.size(); j++) {
                        int clusterAddress = dataAndSubDirectory + clusterList.get(j) * 512;
                        int readLength = remainSize > 512 ? 512 : remainSize;
                        for (int k = 0; k < readLength; k++) {
                            fileContent[readIndex++] = (diskContent[clusterAddress + k]);
                        }
                        remainSize -= readLength;
                    }
                    fileInfoJson.put("fileContent", new String(fileContent));
                    array.add(fileInfoJson);
                }
//            array.add(fileInfoJson);
            }
            else break;
        }
        return array;
    }

    public String ls(){
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < fileArray.size();i++){
            builder.append(toFileInfoStr(fileArray.getJSONObject(i)) + "\r\n");
        }
        System.out.println(builder.toString());
        return builder.toString();
    }

    public String readFile(String fileName, String suffix){
        for(int i = 0; i < fileArray.size(); i++){
            JSONObject fileInfoJson = fileArray.getJSONObject(i);
            if(fileName.toUpperCase().equals(fileInfoJson.getString("fileName"))
                && suffix.toUpperCase().equals(fileInfoJson.getString("suffix"))){
                return fileInfoJson.getString("fileContent");
            }
        }
        return "";
    }

    public JSONObject getFilePtr(String fileName, String suffix){
        for(int i = 0; i < fileArray.size(); i++){
            JSONObject fileInfoJson = fileArray.getJSONObject(i);
            if(fileName.toUpperCase().equals(fileInfoJson.getString("fileName"))
                    && suffix.toUpperCase().equals(fileInfoJson.getString("suffix"))){
                return fileInfoJson;
            }
        }
        return null;
    }

    private String toFileInfoStr(JSONObject fileJson){
        String totalFileName;
//        System.out.println(fileJson.getString("suffix").length());
        if(fileJson.getString("suffix").length() == 0)
            totalFileName = fileJson.getString("fileName");
        else
            totalFileName = fileJson.getString("fileName") + "." + fileJson.getString("suffix");
        return totalFileName +", created at: " + fileJson.getString("createDate") + " " + fileJson.getString("createTime")
                + ", latest modified at: " + fileJson.getString("modifyDate") + " " + fileJson.getString("modifyTime")
                + ", size: " + fileJson.getInteger("size") + " B";
//                + ", fileContent: \n" + fileJson.getString("fileContent");
//                + ", firstCluster: " + fileJson.getInteger("firstCluster");
    }

    private static byte[] fillBytes(byte[] origin,int start, int end){
//        System.out.println(String.format("%x",start));
//        System.out.println(String.format("%x",end));
        byte[] bytes = new byte[end-start+1];
        for(int i = start,j=0; i <= end; i++,j++){
            bytes[j] = origin[i];
        }
        return bytes;
    }

    private boolean isValidDirectory(byte[] bytes){
        for(int i = 0; i< bytes.length;i++)
            if(bytes[i]!=0)
                return true;
        return false;
    }

    public static void main(String[] args) {
        //列出磁盘文件列表
        DiskFileSystem.getInstance().ls();
    }
}
