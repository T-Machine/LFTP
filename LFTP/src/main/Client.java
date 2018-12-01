package main;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;




import service.SendThread;
import tools.*;



public class Client {
	
	public static void main(String[] args) {
    	String dir = "test.mp4";
    	String address = "localhost";
    	int sourcePort = 3777;
    	int dstPort = 3888;
    	System.out.println("������ " + address + ":" + dstPort + " �����ļ�: " + dir);
    	List<byte[]> byteList = FileIO.file2byte(dir);
    	List<Packet> packageList = new ArrayList<>();
    	Packet data;
    	for(int i = 0; i < byteList.size(); i++) {
    		data = new Packet(0, i, false, false, 50, byteList.get(i));
    		packageList.add(data);
    	}
    	try {
    		System.out.println("׼�����䣺");
        	InetAddress ia = InetAddress.getByName(address);
        	Thread send_thread = new Thread(new SendThread(packageList, ia, sourcePort, dstPort));
        	send_thread.start();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
    	
	}
    
    public void run() {
    	
    }
}
