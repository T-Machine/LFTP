package service;

import java.io.File;
import java.net.InetAddress;

public class ServerControlThread implements Runnable {
    private int serverPort;				//服务端接收端口
    private String controlInfo;         //控制包的内容
//    private int clientPort;             //客户端端口
//    private InetAddress clientAddress;  //客户端地址

    public ServerControlThread(int s_port, String control) {
        this.serverPort = s_port;
        this.controlInfo = control;
        //this.clientPort = c_port;
        //this.clientAddress = address;
    }

    @Override
    public void run() {
//        String [] info = controlInfo.split("#");
//        if(info[0] == "LSEND") {
//            try {
//                Thread receiveThread = new Thread(new ReceiveThread(serverPort));
//                receiveThread.start();
//                System.out.println("[Server] [lsend] Receive file in " + serverPort);
//                String dirString = "data";
//                File file = new File(dirString);
//                if(!file.exists()) {
//                    file.mkdir();
//                }
//                receiveThread.join();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//        else if(info[0] == "LGET") {
//
//        }
    }
}
