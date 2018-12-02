package command;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import service.SendThread;
import tools.FileIO;
import tools.Packet;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "lsend", mixinStandardHelpOptions = true, description = "Send files to the specified IP address")
public class Lsend implements Runnable  {

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

        List<byte[]> byteList = FileIO.file2byte(filename);
        List<Packet> packageList = new ArrayList<>();
        Packet data;
        data = new Packet(0, 0, false, false, 50, filename.getBytes());
        packageList.add(data);
        for(int i = 0; i < byteList.size(); i++) {
            data = new Packet(0, i + 1, false, false, 50, byteList.get(i));
            packageList.add(data);
        }

        try {
            System.out.println("Read to send：");
            InetAddress ia = InetAddress.getByName(serverAddress);
            Thread send_thread = new Thread(new SendThread(packageList, ia, dataPort, 3888, filename));
            send_thread.start();
            send_thread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
