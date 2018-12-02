package command;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import service.SendThread;
import tools.ByteConverter;
import tools.FileIO;
import tools.Packet;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "lsend", mixinStandardHelpOptions = true, description = "Send files to the specified IP address")
public class Lsend implements Runnable  {
    private final static int BUFLENGTH = 8192;
    private final static int BUFSIZE = BUFLENGTH * 1024;

    @Option(names = {"-s", "--myserver"}, description = "Server IP address", defaultValue = "localhost")
    private String serverAddress;

    @Parameters(description = "file name", defaultValue = "")
    private String filename;

    @Option(names = {"-cp", "--controlport"}, description = "The control PORT", defaultValue = "4000")
    private int controlPort;

    @Option(names = {"-dp", "--dataport"}, description = "The data PORT", defaultValue = "3777")
    private int dataPort;

    @Override
    public void run() {
        System.out.println("Send " + serverAddress + ":" + dataPort + " Filename: " + filename);
        try {
            int destPort;
            //将控制信息发送给服务端（LSEND + 文件名）
            DatagramSocket socket = new DatagramSocket(controlPort);
            String controlInfo = "LSEND#" + filename;
            byte[] tmp = ByteConverter.objectToBytes(new Packet(0, -1, false, false, -1, controlInfo.getBytes()));
            DatagramPacket controlPkt = new DatagramPacket(tmp, tmp.length, InetAddress.getByName(serverAddress), 4001);
            socket.send(controlPkt);
            //从服务端获取可用端口
            byte[] buffer = new byte[BUFSIZE];
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            socket.receive(dp);
            Packet returnPkt = ByteConverter.bytesToObject(buffer);
            String serverInfo = new String(returnPkt.getData());
            if(serverInfo.equals("NOPORT")) {
                System.out.println("[Fail] The server has no free port");
            } else {
                System.out.println("[Info] Read file and send to " + Integer.parseInt(serverInfo));
                Thread send_thread = new Thread(new SendThread(InetAddress.getByName(serverAddress), dataPort, Integer.parseInt(serverInfo), filename));
                send_thread.start();
                send_thread.join();
            }

            socket.disconnect();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
