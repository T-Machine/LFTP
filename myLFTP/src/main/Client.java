package main;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


import service.SendThread;
import tools.*;



public class Client {
	
	public static void main(String[] args) {
		//创建Scanner对象
        //System.in表示标准化输出，也就是键盘输出
		System.out.println("请输入文件名");
        Scanner scanner = new Scanner(System.in);
        //利用hasNextXXX()判断是否还有下一输入项
    	String dir = scanner.next();
    	String address = "localhost";
    	int sourcePort = 3777;
    	int dstPort = 3888;
    	// 第一个分组传输文件名
    	System.out.println("Send " + address + ":" + dstPort + " Filename: " + dir);
    	List<byte[]> byteList = FileIO.file2byte(dir);
    	List<Packet> packageList = new ArrayList<>();
    	Packet data;
    	data = new Packet(0, 0, false, false, 50, dir.getBytes());
    	packageList.add(data);
    	for(int i = 0; i < byteList.size(); i++) {
    		data = new Packet(0, i + 1, false, false, 50, byteList.get(i));
    		packageList.add(data);
    	}
    	try {
    		System.out.println("Read to send：");
        	InetAddress ia = InetAddress.getByName(address);
        	Thread send_thread = new Thread(new SendThread(packageList, ia, sourcePort, dstPort, dir));
        	send_thread.start();
			send_thread.join();
        	System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
    	
	}
}