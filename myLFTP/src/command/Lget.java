package command;

import picocli.CommandLine;
import service.ReceiveThread;
import service.SendThread;
import tools.ByteConverter;
import tools.Packet;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@CommandLine.Command(name = "lget", mixinStandardHelpOptions = true, description = "Get files from the specified IP address")
public class Lget implements Runnable  {
    private final static int BUFLENGTH = 8192;
    private final static int BUFSIZE = BUFLENGTH * 1024;

    @CommandLine.Option(names = {"-s", "--myserver"}, description = "Server IP address", defaultValue = "localhost")
    private String serverAddress;

    @CommandLine.Parameters(description = "file name", defaultValue = "")
    private String filename;

    @CommandLine.Option(names = {"-cp", "--controlport"}, description = "The control PORT", defaultValue = "4000")
    private int controlPort;

    @CommandLine.Option(names = {"-dp", "--dataport"}, description = "The data PORT", defaultValue = "3777")
    private int dataPort;

    @Override
    public void run() {
        System.out.println("[Info] Get " + filename + " from " + serverAddress);
        try {
            //将控制信息发送给服务端（LSEND + 文件名 + 端口）
            DatagramSocket socket = new DatagramSocket(controlPort);
            String controlInfo = "LGET#" + filename + "#" + dataPort;   //[LGET]#[FileName]#[Port]
            Packet.sendStringParketTo(socket, controlInfo, InetAddress.getByName(serverAddress), 5500);
            //从服务端获取可用端口
            String serverInfo = Packet.getStringParketFrom(socket);
            String [] info = serverInfo.split("#");

            if(info[0].equals("NOPORT")) {
                System.out.println("[Fail] The server has no free port");
            } else if(info[0].equals("NOFILE")) {
                System.out.println("[Fail] The file is not existed in server");
            } else {
                System.out.println("[Info] Get file from " + info[1]);
                //TODO:获取文件长度 info[2]
                /*缺省目录*/Thread receiveThread = new Thread(new ReceiveThread(dataPort, "data/", null));
                receiveThread.start();
                receiveThread.join();
            }

            socket.disconnect();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}