/// 因为JVM堆内存的限制，大概跑了一下只能读写300-MB的文件

package tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class FileIO {
	public static List<byte[]> file2byte(String path) {
        try {
            FileInputStream inStream =new FileInputStream(new File(path));
            List<byte[]> datas = new ArrayList<>();
            final int MAX_BYTE = 1024;	//每个byte[]的容量,当前1KB
            long streamTotal = 0;  //接受流的容量
            int streamNum = 0;  //流需要分开的数量
            int leave = 0;  //文件剩下的字符数
            // 获得文件输入流的总量
            streamTotal = inStream.available();
            // 获得流文件需要分开的满1kb的流的数量
            streamNum = (int)Math.floor(streamTotal/MAX_BYTE);
            // 获得分开成多个登长流文件后，最后剩余的流大小
            leave = (int)streamTotal%MAX_BYTE;
            if(streamNum > 0) {
            	for(int i = 0; i < streamNum; i++) {
            		byte[] data;
            		data = new byte[MAX_BYTE];
            		inStream.read(data, 0, MAX_BYTE);
            		datas.add(data);
            	}
            }
            // 处理最后剩余的部分字符
            byte[] data = new byte[leave];
            inStream.read(data, 0, leave);
            datas.add(data);
            inStream.close();
            System.out.println("读取文件完毕,共 " + streamNum + "段");
            return datas;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void byte2file(String path,List<byte[]> datas) {
        try {
            //  if true, then bytes will be written to the end of the file rather than the beginning
            System.out.println(path);
            FileOutputStream outputStream  =new FileOutputStream(new File(path), true);
            for(int i = 0; i < datas.size(); i++) {
            	outputStream.write(datas.get(i));
            	outputStream.flush();
        		//System.out.println("写入文件片段" + i);
            }
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static int getBufferLength(String path) {
    	try{
	    	FileInputStream inStream =new FileInputStream(new File(path));
	        List<byte[]> datas = new ArrayList<>();
	        final int MAX_BYTE = 1024;	//每个byte[]的容量,当前1Kb
	        long streamTotal = 0;  //接受流的容量
	        int streamNum = 0;  //流需要分开的数量
	        int leave = 0;  //文件剩下的字符数
	        // 获得文件输入流的总量
	        streamTotal = inStream.available();
	        // 获得流文件需要分开的满1kb的流的数量
	        streamNum = (int)Math.floor(streamTotal/MAX_BYTE);
	        inStream.close();
	        return streamNum+1;
    	} catch (Exception e) {
    		e.printStackTrace();
    		return 0;
    	}
    }
    

    public static void main(String[] args) {
        List<byte[]> datas=file2byte("test.rmvb");   
        System.out.println("readFile succeed!");
        System.out.println("Total: " + getBufferLength("test.mp3") + "kb.");
        byte2file("output.rmvb",datas);
        System.out.println("saveFile succeed!");
        System.out.println("Total: " + getBufferLength("output.mp3") + "kb.");
        
        
    }
}