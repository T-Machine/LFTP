/// ��ΪJVM���ڴ�����ƣ��������һ��ֻ�ܶ�д300-MB���ļ�

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
            final int MAX_BYTE = 1024;	//ÿ��byte[]������,��ǰ1Kb
            long streamTotal = 0;  //������������
            int streamNum = 0;  //����Ҫ�ֿ�������
            int leave = 0;  //�ļ�ʣ�µ��ַ���
            // ����ļ�������������
            streamTotal = inStream.available();
            // ������ļ���Ҫ�ֿ�����1kb����������
            streamNum = (int)Math.floor(streamTotal/MAX_BYTE);
            // ��÷ֿ��ɶ���ǳ����ļ������ʣ�������С
            leave = (int)streamTotal%MAX_BYTE;
            if(streamNum > 0) {
            	for(int i = 0; i < streamNum; i++) {
            		byte[] data;
            		data = new byte[MAX_BYTE];
            		inStream.read(data, 0, MAX_BYTE);
            		datas.add(data);
            	}
            }
            // �������ʣ��Ĳ����ַ�
            byte[] data = new byte[leave];
            inStream.read(data, 0, leave);
            datas.add(data);
            inStream.close();
            System.out.println("��ȡ�ļ����,�� " + streamNum + "��");
            return datas;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void byte2file(String path,List<byte[]> datas) {
        try {
            FileOutputStream outputStream  =new FileOutputStream(new File(path));
            for(int i = 0; i < datas.size(); i++) {
            	outputStream.write(datas.get(i));
            	outputStream.flush();
        		//System.out.println("д���ļ�Ƭ��" + i);
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
	        final int MAX_BYTE = 1024;	//ÿ��byte[]������,��ǰ1Kb
	        long streamTotal = 0;  //������������
	        int streamNum = 0;  //����Ҫ�ֿ�������
	        int leave = 0;  //�ļ�ʣ�µ��ַ���
	        // ����ļ�������������
	        streamTotal = inStream.available();
	        // ������ļ���Ҫ�ֿ�����1kb����������
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
