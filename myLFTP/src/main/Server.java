package main;

import service.ReceiveThread;
import service.SendThread;
import tools.ByteConverter;
import tools.FileIO;
import tools.Packet;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class Server {

    private final static int BUFLENGTH = 8192;
    private final static int BUFSIZE = BUFLENGTH * 1024;
    private static volatile List<Integer> PortPool = new ArrayList<>();
    private static DatagramSocket socket;				// UDP连接DatagramSocket

	public static void main(String[] argv) {
        //预先在端口池中存放10个可分配端口
	    for(int i = 7000; i <= 7100; i ++) {
	        PortPool.add(i);
        }
        try {
            socket = new DatagramSocket(5500);  //Listening Port: 5500
        } catch (SocketException e) {
            e.printStackTrace();
        }
        while(true){
			try{
                System.out.println("[Server] Listening at 5500...");
                byte[] buffer = new byte[BUFSIZE];
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                socket.receive(dp);
                Packet packet = ByteConverter.bytesToObject(buffer);
                String controlInfo = new String(packet.getData());  //控制信息
                InetAddress clientAddress = dp.getAddress();
                int clientPort = dp.getPort();
                System.out.println("[Server] Get request from " + clientAddress.toString() + ":" + clientPort + " request: " + controlInfo);

                //Server返回的信息：[Msg]#[Port]#[FileLength]
                //Msg包括：[OK]可行, [NOPORT]无端口可用, [NOFILE]文件不存在, [EXIST]文件已存在
                //仍可分配端口时
                if(!PortPool.isEmpty()) {
                    int serverPort = PortPool.remove(0);    //可用端口
                    //将可用端口发送给客户端
                    //Packet.sendStringParketTo(socket, String.valueOf(serverPort), clientAddress, clientPort);

                    String [] info = controlInfo.split("#");
                    String filename = info[1];
                    if(info[0].equals("LSEND")) {
                        File file = new File("data");
                        if(!file.exists()) {
                            file.mkdir();
                        }

                        File findFile = new File("data/" + filename);
                        if(findFile.exists()) {
                            Packet.sendStringParketTo(socket, "EXIST#", clientAddress, clientPort);
                            System.out.println("[Server] [error] The file is existed");
                        } else {
                            Packet.sendStringParketTo(socket, "OK#" + serverPort, clientAddress, clientPort);
                            System.out.println("[Server] [lsend] Assign port " + serverPort + " to " + clientAddress.toString());
                            /*缺省目录*/Thread receiveThread = new Thread(new ReceiveThread(serverPort, "data/", () -> PortPool.add(serverPort)));
                            receiveThread.start();
                        }
                    }
                    else if(info[0].equals("LGET")) {
                        int targetPort = Integer.parseInt(info[2]);
                        File file = new File(filename);
                        if(!file.exists()) {
                            //告诉客户端文件不存在
                            Packet.sendStringParketTo(socket, "NOFILE#", clientAddress, clientPort);
                            System.out.println("[Server] [error] The requested file is not existed");
                        } else {
                            String fileLength = String.valueOf(FileIO.getByteLength(filename));
                            Packet.sendStringParketTo(socket, "OK#" + serverPort + "#" + fileLength, clientAddress, clientPort);
                            System.out.println("[Server] [lget] Assign port " + serverPort + " to " + clientAddress.toString());
                            //TODO: 握手?
                            Thread send_thread = new Thread(new SendThread(filename, clientAddress, serverPort, targetPort, () -> PortPool.add(serverPort)));
                            send_thread.start();
                        }
                    }
                    else {
                        System.out.println("[Server] [error] Get invalid request");
                    }

                } else {
                    //端口爆满时，告诉客户端
                    Packet.sendStringParketTo(socket, "NOPORT#", clientAddress, clientPort);
                    System.out.println("[Server] [error] No more port can assigned to " + clientAddress.toString());
                }
                
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}