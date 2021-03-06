package tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class FileIO {
    public static final int BLOCK_SIZE = 64 * 1024 * 1024;
    public static final int MAX_BYTE = 1024;
    public static final int MAX_PACK_PER_BLOCK = BLOCK_SIZE / MAX_BYTE;

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

    // 按照块进行读取，每个块大小64MB
    public static List<byte[]> divideToList(String path, int blockNum) {
        if(blockNum < 0) return null;
        try {
            FileInputStream inStream =new FileInputStream(new File(path));
            List<byte[]> datas = new ArrayList<>();
            // 获得文件输入流的总量
            File file = new File(path);
            long streamTotal = file.length();
            // 查看块的数量
            int blockTotal = (int)Math.floor(streamTotal/BLOCK_SIZE);
            if(blockNum > blockTotal) return null;
            for(int i = 0; i < blockTotal; i++){
                byte[] blockData = new byte[BLOCK_SIZE];
                inStream.read(blockData, 0, BLOCK_SIZE);
                if(i == blockNum){
                    datas = blockToByteList(blockData, BLOCK_SIZE);
                }
            }
            if(blockNum == blockTotal) {
                int leaveBlock =(int)(streamTotal - (long)(blockTotal * BLOCK_SIZE));
                byte[] blockData = new byte[leaveBlock];
                inStream.read(blockData, 0, leaveBlock);
                datas = blockToByteList(blockData, leaveBlock);
            }
            inStream.close();
            return datas;
        } catch (Exception e) {
            System.out.println("文件名不存在，程序强制退出");
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
        return getLength(path, false);
    }

    public static int getByteLength(String path) {
        return getLength(path, true);
    }

    // 0 -> blockSum, 1 -> byteSum
    private static int getLength(String path, boolean choice){
        try{
            // 获取文件
            File file = new File(path);
            long streamTotal = file.length();
            if(choice){
                // 查看一共需要多少个块
                int byteSum = (int)Math.floor(streamTotal/MAX_BYTE);
                return byteSum + 1;
            } else{
                // 查看一共需要多少个块
                int blockSum = (int)Math.floor(streamTotal/BLOCK_SIZE);
                return blockSum + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}