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
        System.out.println("[Info] Send " + serverAddress + " Filename: " + filename);
        try {
            //将控制信息（请求）发送给服务端（LSEND + 文件名 + 文件长度）
            DatagramSocket socket = new DatagramSocket(controlPort);
            String controlInfo = "LSEND#" + filename + "#" + FileIO.getByteLength(filename);
            Packet.sendStringParketTo(socket, controlInfo, InetAddress.getByName(serverAddress), 5500);
            //从服务端获取可用端口
            String serverInfo = Packet.getStringParketFrom(socket);
            String [] info = serverInfo.split("#");
            System.out.println("[Info] Server response: " + serverInfo);

            if(info[0].equals("NOPORT")) {
                System.out.println("[Fail] The server has no free port");
            } else if(info[0].equals("EXIST")) {
                System.out.println("[Fail] The file is existed in the server");
            } else {
                System.out.println("[Info] Read file and send to " + info[1]);
                Thread send_thread = new Thread(new SendThread(filename, InetAddress.getByName(serverAddress), dataPort, Integer.parseInt(info[1]), null));
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
