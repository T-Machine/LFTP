package tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class FileIO {
    private static final int BLOCK_SIZE = 50 * 1024 * 1024;
    private static final int MAX_BYTE = 1024;

    private static List<byte[]> blockToByteList(byte[] data, int size){
        int totalByte = (int)Math.floor(size / MAX_BYTE);
        List<byte[]> datas = new ArrayList<>();
        int leave = size % MAX_BYTE;
        // block的byte开始的位置
        int pos = 0;
        for(int i = 0; i < totalByte; i++, pos += MAX_BYTE) {
            byte[]outputData = new byte[MAX_BYTE];
            for(int j = 0; j < MAX_BYTE; j++){
                outputData[j] = data[j + pos];
            }
            datas.add(outputData);
        }
        if(leave == 0) return datas;
            // 处理最后剩余的部分字符
            byte[]outputData = new byte[leave];
            for(int i = 0; i < leave;i++, pos++){
                outputData[i] = data[pos];
            }
            datas.add(outputData);
        return datas;
    }

    // 按照块进行读取，每个块大小8MB
    public static List<byte[]> divideToList(String path, int blockNum) {
        if(blockNum < 0) return null;
        try {
            FileInputStream inStream =new FileInputStream(new File(path));
            List<byte[]> datas = new ArrayList<>();
            // 获得文件输入流的总量
            int streamTotal = inStream.available();

            // 查看块的数量
            int blockTotal = (int)Math.floor(streamTotal/BLOCK_SIZE);
            if(blockNum > blockTotal) return null;
            int leave = streamTotal % MAX_BYTE;
            for(int i = 0; i < blockTotal; i++){
                byte[] blockData = new byte[BLOCK_SIZE];
                inStream.read(blockData, 0, BLOCK_SIZE);
                if(i == blockNum){
                    datas = blockToByteList(blockData, BLOCK_SIZE);
                }
            }
            if(blockNum == blockTotal) {
                int leaveBlock = streamTotal % BLOCK_SIZE;
                byte[] blockData = new byte[leaveBlock];
                inStream.read(blockData, 0, leaveBlock);
                datas = blockToByteList(blockData, leaveBlock);
            }
            inStream.close();
            System.out.println("读取区块" + blockNum + "完毕! 区块大小" + datas.size());
            return datas;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("读取越界");
            return null;
        }
    }

    // 在接收后直接写入文件不进行分块
    public static void byte2file(String path,List<byte[]> datas) {
        try {
            FileOutputStream outputStream  =new FileOutputStream(new File(path), true);
            for(int i = 0; i < datas.size(); i++) {
                outputStream.write(datas.get(i));
                outputStream.flush();
            }
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getBlockLength(String path) {
        try{
            FileInputStream inStream =new FileInputStream(new File(path));
            // 获得文件输入流的总量
            int streamTotal = inStream.available();
            // 查看一共需要多少个块
            int blockNum = (int)Math.floor(streamTotal/BLOCK_SIZE);
            inStream.close();
            return blockNum + 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static void main(String[] args){
        String str = "test.zip";
        String str2 = "out.zip";
        for(int i = 0; i < getBlockLength(str); i++){

            byte2file(str2, divideToList(str, i));
        }
    }
}