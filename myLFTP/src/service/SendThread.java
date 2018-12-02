package service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;

import tools.ByteConverter;
import tools.FileIO;
import tools.Packet;

public class SendThread implements Runnable {
    private final static int BUFSIZE = 1024 * 1024;
    private List<Packet> data;						//要发送的数据
    private List<Packet> readingData;               // 用于多线程读取
    InetAddress address;							//目的地址
    int sourcePort;									//源端口
    int destPort;									//目的端口
    private volatile int base = 0;					//基序号
    private volatile int nextSeq = 0;				//下一个待发送分组的序号
    private int rwnd = 8192 * 1024;								//未确认的最大分组数
    private volatile Date date;						//记录启动定时器的时间
    private DatagramSocket socket;					//用于发送数据包
    private volatile boolean retrans= false;		//当前是否在重传
    private volatile int currAck = -1;				//已被确认的最大分组ack
    private String dir;                             // 传输文件的文件名
    private volatile int duplicateAck = 0;
    private boolean isFull = false;                 // 判断接收方的缓存是否已经是满的，以调整速率
    private boolean isConneted;
    private volatile double cwnd = 1;					//拥塞窗口
    private volatile double ssthresh = 60;				//慢启动阈值
    private volatile boolean isQuickRecover = false;	//是否处于快速恢复状态
    private volatile Date startTime;
    private volatile int currentBlock = 0;              // 当前读取的块
    private volatile int blockSum;                          // 块的总数量
    private volatile boolean isReRoad;                          // 判断现在是否是已读


    public SendThread(InetAddress address, int sourcePort, int destPort, String dir) {
        this.address = address;
        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.date = new Date();
        this.dir = dir;
    }

    @Override
    public void run() {
        try {
            this.socket = new DatagramSocket(sourcePort);
        } catch (SocketException e) {
            System.out.println("SendThread: 创建socket出错");
            e.printStackTrace();
        }
        isConneted = true;
        //启动接收ACK包线程
        Thread recv_ack_thread = new Thread(new RecvAck());

        // 启动超时判断处理线程
        Thread time_out_threadThread;
        time_out_threadThread = new Thread(new TimeOut());


//        // 接收文件名
//        data = new ArrayList<>();
//        Packet dataPacket;
//        dataPacket = new Packet(0, 0, false, false, 50, dir.getBytes());
//        data.add(dataPacket);
        // 读取文件
        System.out.println("开始读取文件发送");
        data = new ArrayList<>();
        blockSum = FileIO.getBlockLength(dir);
        for(currentBlock = 0; currentBlock < blockSum; currentBlock++){
            isReRoad = true;
            if(currentBlock != 0){
                data.clear();
            }
            Packet dataPacket;
            List<byte[]> byteData = FileIO.divideToList(dir, currentBlock);
            for(int j = 0; j < byteData.size(); j++) {
                dataPacket= new Packet(0, j + currentBlock * FileIO.MAX_PACK_PER_BLOCK, false, false, 50, byteData.get(j), dir);
                data.add(dataPacket);
            }
            try {
                if(currentBlock == 0){
                    time_out_threadThread.start();
                    recv_ack_thread.start();
                }
                isReRoad = false;
                //启动发送数据包
                startTime = new Date();
                // 对于上次的文件传输，需要减掉差值
                while (nextSeq < data.size() + currentBlock * FileIO.MAX_PACK_PER_BLOCK) {
                    if(isFull == true) {
                        byte[] tmp = ByteConverter.objectToBytes(new Packet(0, -1, false, false, -1, null, ""));
                        DatagramPacket tmpPack = new DatagramPacket(tmp, tmp.length, address, destPort);
                        socket.send(tmpPack);
                        continue;
                    }
                    int threshold = rwnd < (int)cwnd ? rwnd : (int)cwnd;
                    if (nextSeq <= base + threshold && retrans == false) {
                        // 包含了文件名
                        Packet sentPacket;
//                        if(currentBlock == 0){
                            sentPacket = data.get(nextSeq - currentBlock * FileIO.MAX_PACK_PER_BLOCK);
//                        } else{
//                            sentPacket = data.get(nextSeq - 1 - currentBlock * FileIO.MAX_PACK_PER_BLOCK);
//
//                        }
                        byte[] buffer = ByteConverter.objectToBytes(sentPacket);
                        DatagramPacket dp = new DatagramPacket(buffer, buffer.length, address, destPort);
                        ByteConverter.bytesToObject(dp.getData());
                        socket.send(dp);

                        if (base == nextSeq) startTimer();

                        nextSeq++;
                    }
                }
                while(base < data.size() + (currentBlock - 1) * FileIO.MAX_PACK_PER_BLOCK && currentBlock < blockSum - 1) {}
            } catch (IOException e) {
                System.out.println("SendThread: 发送数据包出错");
                e.printStackTrace();
            }
        }
        //while(base < data.size() + currentBlock * FileIO.MAX_PACK_PER_BLOCK) {}
        //传输完成时，发送一个FIN包告知接收方
        while (true) {
            if (currAck == data.size() - 1 + (currentBlock - 1) * FileIO.MAX_PACK_PER_BLOCK) {
                try {
                    System.out.print("发送终止packet");
                    byte[] buffer = ByteConverter.objectToBytes(new Packet(-1, -1, false, true, rwnd, null, ""));
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length, address, destPort);
                    socket.send(dp);
                    Date endTime = new Date();
//					float speed = acurrentSeries / (endTime.getTime() - startTime.getTime());
//					speed *= 1000;
//					System.out.println(speed + "kB/s");
                    System.out.println("发送完毕");
                    recv_ack_thread.join();
                    time_out_threadThread.join();
                    socket.disconnect();
                    socket.close();
                    return;
                } catch (IOException | InterruptedException e) {
                    System.out.println("SendThread: 发送数据包出错");
                    e.printStackTrace();
                }
                break;
            }
        }

    }
//
//    // 新建List表，可以一边读取以便发送
//    class getList implements  Runnable{
//        @Override
//        public void run(){
//            while(isConneted){
//                if(okToRead){
//                    List<byte[]> byteData = FileIO.divideToList(dir, currentBlock);
//                    for(int j = 0; j < byteData.size(); j++) {
//                        dataPacket= new Packet(0, j + currentBlock * FileIO.MAX_PACK_PER_BLOCK, false, false, 50, byteData.get(j), dir);
//                        data.add(dataPacket);
//                    }
//                }
//            }
//        }
//    }

    //接收ACK包的线程
    class RecvAck implements Runnable {
        @Override
        public void run() {
            try {
                while (isConneted) {
                    //System.out.println(cwnd);//for debug
                    byte[] buffer = new byte[BUFSIZE];
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    socket.receive(dp);
                    Packet packet = ByteConverter.bytesToObject(buffer);
                    base = packet.getAck() + 1;
                    rwnd = packet.getRwnd();
                    // 如果已经满
                    if (rwnd == 0) {
                        isFull = true;
                    } else isFull = false;
//                    if(base == )
                    //检测冗余Ack
                    if(currAck == packet.getAck()) {
                        if(isQuickRecover == true) {
                            cwnd++;
                        } else {
                            duplicateAck ++;
                        }

                        //3个冗余Ack，快速重传
                        if(duplicateAck == 3) {
                            //更新拥塞窗口
                            ssthresh = cwnd / 2;
                            cwnd = ssthresh + 3;
                            isQuickRecover = true;
//							duplicateAck = 0;
//                            System.out.println("启动快速重传");
                            SendPacket(base);
                        }
                    } else {
                        duplicateAck = 0;

                        //拥塞控制
                        if(isQuickRecover == true) {	//快速恢复时，收到新的Ack，转到拥塞避免状态
                            isQuickRecover = false;
                            cwnd = ssthresh;
                        } else {
                            if(cwnd >= ssthresh) {
                                cwnd += 1 / cwnd;	//拥塞避免
                            } else {
                                cwnd ++;	//慢启动
                            }
                        }
                    }

                    currAck = packet.getAck();
                    if (base != nextSeq) startTimer();
//
//					//确认接收最后一个分组
//					if (packet.getAck() - totalFileSize == data.size() - 1) break;
                }
            } catch (IOException e) {
                System.out.println("ReceiveThread: 接收数据包出错");
            }
        }
    }

    //判断是否超时的线程
    class TimeOut implements Runnable {
        @Override
        public void run() {
            while (isConneted) {
                while(isReRoad){ startTimer();}
                long start_time = date.getTime();
                long curr_time = new Date().getTime();
                //超过0.3秒时触发超时
                if (curr_time - start_time > 300) {
                    startTimer();
                    timeOut();
                }
            }
        }
    }


    private void SendPacket(int seq) {
        try {
            byte[] buffer = ByteConverter.objectToBytes(data.get(seq - currentBlock * FileIO.MAX_PACK_PER_BLOCK));
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length, address, destPort);
            Packet packet = ByteConverter.bytesToObject(dp.getData());
//            System.out.println("发送的分组序号: " + packet.getSeq());
            socket.send(dp);
        } catch (IOException e) {
            System.out.println("SendThread: 发送数据包出错");
            e.printStackTrace();
        }
    }

    //超时引发重传事件
    private void timeOut() {
        try {
            //更新拥塞窗口
            isQuickRecover = false;
            duplicateAck = 0;
            ssthresh = cwnd / 2;
            cwnd = 1;

            //记录base值和nextSeq值，防止接收线程对其造成改变
            retrans = true;
            // 只发送一个包
            for (int i = base; i < nextSeq; ++i) {
                //if(nextSeq == base) break;
                while (rwnd <= 0) System.out.println("接收方缓存不够，暂停重传");
                byte[] buffer = ByteConverter.objectToBytes(data.get(i - currentBlock * FileIO.MAX_PACK_PER_BLOCK));
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length, address, destPort);
                System.out.println("重新发送片段：" + i);
                socket.send(dp);
            }
            retrans = false;
        } catch (IOException e) {
            System.out.println("SendThread: 发送数据包出错");
            e.printStackTrace();
        }
    }

    //启动定时器
    private void startTimer() {
        date = new Date();
    }
}