package main;

import service.ReceiveThread;

import java.io.File;

public class Server {
	public static void main(String[] argv) {
		try{
			int serverPort = 3888;
			Thread receiveThread = new Thread(new ReceiveThread(serverPort));
			receiveThread.start();
			System.out.println("传输开始" + serverPort);
			String dirString = "server";
			File file = new File(dirString);
			if(!file.exists()) {
				file.mkdir();
			}
			receiveThread.join();
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}