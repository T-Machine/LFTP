package main;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


import service.SendThread;
import tools.*;

import static java.lang.Thread.sleep;


public class Client {

	public static void main(String[] args) {
		System.out.println("请输入文件名");
		Scanner scanner = new Scanner(System.in);
		//利用hasNextXXX()判断是否还有下一输入项
		String dir = scanner.next();
		try {
			while(true) {
				String address = "localhost";
				int sourcePort = 3777;
				int dstPort = 3888;
				InetAddress ia = InetAddress.getByName(address);
				Thread send_thread = new Thread(new SendThread(dir, ia, sourcePort, dstPort));
				send_thread.start();
				send_thread.join();
				sleep(3000);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}