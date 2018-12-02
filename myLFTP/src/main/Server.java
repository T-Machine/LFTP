package main;

import service.ReceiveThread;

import java.io.File;

import static java.lang.Thread.sleep;

public class Server {
	public static void main(String[] argv) {
		while(true){
			try{
				int serverPort = 3888;
				Thread receiveThread = new Thread(new ReceiveThread(serverPort));
				receiveThread.start();
				System.out.println("传输开始" + serverPort);
				String dirString = "data";
				File file = new File(dirString);
				if(!file.exists()) {
					file.mkdir();
				}
				receiveThread.join();
				sleep(500);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}