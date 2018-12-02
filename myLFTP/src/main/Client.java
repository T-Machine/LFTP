package main;

import service.SendThread;
import tools.FileIO;
import tools.Packet;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static java.lang.Thread.sleep;


public class Client {
	
	public static void main(String[] args) {


//		Options options = new Options();
//		options.addOption("h", "help", false, "Helping information." );
//		options.addOption("lsend", "lsend", false, "Send file to server" );
//		CommandLine comm = null;
//		try {
//			comm = new DefaultParser().parse(options, args);
//		} catch (ParseException e) {
//			e.printStackTrace();
//			System.out.println("解析参数失败，参数：[" + Arrays.asList(args).toString() + "]");	//log.error
//			throw new IllegalArgumentException();
//		}
//		if (comm.getOptions().length == 0) {
//			System.out.println("No any param to specify.");
//			return;	//return?
//		}
//		if (comm.hasOption("h")) {// help
//			HelpFormatter formatter = new HelpFormatter();
//			formatter.printHelp("LFTP", options);
//			//return;
//		}
//		if (comm.hasOption("lsend")) {//
//			System.out.println("Fucking send.");
//			return;
//		}


		//创建Scanner对象
		System.out.println("请输入文件名");
		Scanner scanner = new Scanner(System.in);
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
			sleep(10000);
		} catch (Exception e) {
			e.printStackTrace();
		}
    	
	}
}