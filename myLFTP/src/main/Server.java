package main;

import service.ReceiveThread;
import tools.ByteConverter;
import tools.Packet;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
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
        try {
            socket = new DatagramSocket(4001);  //Listening Port: 4001
        } catch (SocketException e) {
            e.printStackTrace();
        }
        while(true){
			try{
                System.out.println("[Server] Listening at 4001...");
                byte[] buffer = new byte[BUFSIZE];
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                socket.receive(dp);
                InetAddress clientAddress = dp.getAddress();
                int clientPort = dp.getPort();
                Packet packet = ByteConverter.bytesToObject(buffer);
                String controlInfo = new String(packet.getData());  //控制信息
                System.out.println("[Server] Get request from " + clientAddress.toString() + ":" + clientPort);

                //仍可分配端口时
                if(!PortPool.isEmpty()) {
                    int serverPort = PortPool.remove(0);
                    //将可用端口发送给客户端
                    byte[] s_port = String.valueOf(serverPort).getBytes();
                    byte[] tmp = ByteConverter.objectToBytes(new Packet(0, -1, false, false, -1, s_port));
                    DatagramPacket portPack = new DatagramPacket(tmp, tmp.length, clientAddress, clientPort);
                    socket.send(portPack);
                    System.out.println("[Server] Assign port " + serverPort + " to " + clientAddress.toString());

                    String [] info = controlInfo.split("#");
                    if(info[0].equals("LSEND")) {
                        try {
                            System.out.println("[Server] [lsend] Receive file in " + serverPort);
                            File file = new File("data");
                            if(!file.exists()) {
                                file.mkdir();
                            }
                            Thread receiveThread = new Thread(new ReceiveThread(serverPort));
                            receiveThread.start();
                            //receiveThread.join();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    else if(info[0].equals("LGET")) {

                    }

                } else {
                    //端口爆满时，告诉客户端
                    String msg = "NOPORT";
                    byte[] tmp = ByteConverter.objectToBytes(new Packet(0, -1, false, false, -1, msg.getBytes()));
                    DatagramPacket portPack = new DatagramPacket(tmp, tmp.length, clientAddress, clientPort);
                    socket.send(portPack);
                    System.out.println("[Server] No more port can assigned to " + clientAddress.toString());
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