/// 因为JVM堆内存的限制，大概跑了一下只能读写300-MB的文件

package tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class FileIO {
    // byte的容量为1kB
    private static final int MAX_BYTE = 1024;
    public static List<byte[]> toByte(String path) {
        try {
            // 输入流
            FileInputStream inStream =new FileInputStream(new File(path));
            List<byte[]> dataByteList = new ArrayList<>();

            // 流的总量
            long totalStream = 0;

            // 获得文件输入流的总量
            totalStream = inStream.available();

            // 分割后流的数量
            int streamNum = (int)Math.floor(totalStream / MAX_BYTE);

            // 剩余流的数量
            int residue = (int)totalStream % MAX_BYTE;
            // 如果为0说明不用分割
            if(streamNum > 0) {
                for(int i = 0; i < streamNum; i++) {
                    byte[] tmp = new byte[MAX_BYTE];
                    inStream.read(tmp, 0, MAX_BYTE);
                    dataByteList.add(tmp);
                }
            }

            // 处理最后剩余的部分字符
            byte[] tmp = new byte[residue];
            inStream.read(tmp, 0, residue);
            dataByteList.add(tmp);
            
            inStream.close();
            System.out.println("read success");
            return dataByteList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 写入文件
    public static void toFile(String path,List<byte[]> dataByteList) {
        try {
            FileOutputStream outputStream  =new FileOutputStream(new File(path));
            for(int i = 0; i < dataByteList.size(); i++) {
                outputStream.write(dataByteList.get(i));
                outputStream.flush();
                System.out.println("write success");
            }
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // public static int getBufferLength(String path) {
    //     try{
    //         FileInputStream allStream =new FileInputStream(new File(path));
    //         List<byte[]> dataByteList = new ArrayList<>();
    //         long totalStream = 0;  //接受流的容量
    //         int streamNum = 0;  //流需要分开的数量
    //         int residue = 0;  //文件剩下的字符数
    //         // 获得文件输入流的总量
    //         totalStream = allStream.available();
    //         // 获得流文件需要分开的满1kb的流的数量
    //         streamNum = (int)Math.floor(totalStream/MAX_BYTE);
    //         allStream.close();
    //         return streamNum+1;
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //         return 0;
    //     }
    // }


//    public static void main(String[] args) {
//        List<byte[]> dataByteList=toByte("test.rmvb");
//        System.out.println("readFile succeed!");
////        System.out.println("Total: " + getBufferLength("test.mp3") + "kb.");
//        toFile("output.rmvb",dataByteList);
//        System.out.println("saveFile succeed!");
//    }
}