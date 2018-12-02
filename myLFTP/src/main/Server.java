package main;

import service.ReceiveThread;
import service.ServerControlThread;
import tools.ByteConverter;
import tools.Packet;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.sleep;

public class Server {

    private final static int BUFLENGTH = 8192;
    private final static int BUFSIZE = BUFLENGTH * 1024;
    private static volatile List<Integer> PortPool = new ArrayList<>();
    private static DatagramSocket socket;				// UDP连接DatagramSocket

	public static void main(String[] argv) {
        //预先在端口池中存放10个可分配端口
	    for(int i = 3888; i <= 3898; i ++) {
	        PortPool.add(i);
        }

		while(true){
			try{
                System.out.println("[Server]  Listening at 4001...");
                socket = new DatagramSocket(4001);  //Listening Port: 4001
                byte[] buffer = new byte[BUFSIZE];
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                socket.receive(dp);
                InetAddress clientAddress = dp.getAddress();
                int clientPort = dp.getPort();

                //仍可分配端口时
                if(!PortPool.isEmpty()) {
                    Packet packet = ByteConverter.bytesToObject(buffer);
                    int serverPort = PortPool.remove(0);
                    String controlInfo = new String(packet.getData());  //控制信息
                    //将可用端口发送给客户端
                    byte[] s_port = String.valueOf(serverPort).getBytes();
                    byte[] tmp = ByteConverter.objectToBytes(new Packet(0, -1, false, false, -1, s_port));
                    DatagramPacket portPack = new DatagramPacket(tmp, tmp.length, clientAddress, clientPort);
                    socket.send(portPack);

                    Thread serverControlThread = new Thread(new ServerControlThread(serverPort, controlInfo));
                    serverControlThread.start();
                }

//				int serverPort = 3888;
//				Thread receiveThread = new Thread(new ReceiveThread(serverPort));
//				receiveThread.start();
//				System.out.println("传输开始" + serverPort);
//				String dirString = "data";
//				File file = new File(dirString);
//				if(!file.exists()) {
//					file.mkdir();
//				}
//				receiveThread.join();
//				sleep(500);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}