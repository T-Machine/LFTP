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
    private final static int BUFSIZE = 1024 * 1024;// 数据报的大小最大1024 * 1024
    private volatile int rwnd; // 未确认最大分组的编号
    private volatile int base = 0; // 基序号
    private volatile int nextSeq = 0; // 下一个待发送分组的序号
    private volatile Date startTime; // 记录时间
    private volatile boolean reSending = false; // 重传模式是否打开
    private volatile int currAck = -1; // 已被确认的最大ACK的编号
    private volatile int duplicateAck = 0; // 是否快速重传a
    private volatile double cwnd = 1; // 拥塞窗口
    private volatile double ssthresh = 60; // 慢启动阈值
    private volatile boolean isQuickRecover = false; // 是否处于快速恢复状态
    private volatile int currentBlock = 0; // 当前读取的块
    private volatile int blockSum; // 块的总数量
    private volatile boolean isReRoad; // 判断现在是否是已读
    private List<Packet> sentDataList; // 要发送的数据
    private InetAddress receiveAddr; // 目的地址
    private int sentPort; // 源端口
    private int receivePort; // 目的端口
    private DatagramSocket socket; // 用于发送数据包
    private String fileName; // 传输文件的文件名
    private boolean isFull = false; // 判断接收方的缓存是否已经是满的，以调整速率
    private boolean isConneted;


    public SendThread(String _fileName, InetAddress _receiveAddr, int _sentPort, int _receivePort) {
        this.fileName = _fileName;
        this.receiveAddr = _receiveAddr;
        this.sentPort = _sentPort;
        this.receivePort = _receivePort;
        this.startTime = new Date();
    }

    @Override
    public void run() {
        // 创建socket
        try {
            this.socket = new DatagramSocket(sentPort);
            isConneted = true;
            // 新建ACK接收线程
            Thread ACKreceiver = new Thread(new RecvAck());

            // 超时判断线程
            Thread timeCounter;
            timeCounter = new Thread(new TimeCounter());

            sentDataList = new ArrayList<>();
            // 记录一共读取的块的数量
            blockSum = FileIO.getBlockLength(fileName);
            // 读取-发送循环
            for (currentBlock = 0; currentBlock < blockSum; currentBlock++) {
                // 开始读取文件的标志
                isReRoad = true;
                // 如果是新的块则对之前的内容清空操作，否则启动超时和ACK接收线程
                if (currentBlock != 0) {
                    sentDataList.clear();
                } else{
                    timeCounter.start();
                    ACKreceiver.start();
                }
                Packet dataPacket;
                List<byte[]> byteData = FileIO.divideToList(fileName, currentBlock);
                for (int j = 0; j < byteData.size(); j++) {
                    dataPacket = new Packet(0, j + currentBlock * FileIO.MAX_PACK_PER_BLOCK, false, false, 0, byteData.get(j), fileName);
                    sentDataList.add(dataPacket);
                }
                isReRoad = false;
                // 开始计时
                startTime = new Date();

                // 启动发送数据包
                while (nextSeq < sentDataList.size() + currentBlock * FileIO.MAX_PACK_PER_BLOCK) {

                    // 如果接收方的BUFF满发送空的数据报
                    if (isFull) {
                        byte[] tmp = ByteConverter.objectToBytes(new Packet(0, -1, false, false, -1, null, ""));
                        DatagramPacket tmpPack = new DatagramPacket(tmp, tmp.length, receiveAddr, receivePort);
                        socket.send(tmpPack);
                        continue;
                    }
                    // 拥塞控制和流量控制
                    int threshold = rwnd < (int) cwnd ? rwnd : (int) cwnd;
                    if (nextSeq <= base + threshold && reSending == false) {
                        Packet sentPacket = sentDataList.get(nextSeq - currentBlock * FileIO.MAX_PACK_PER_BLOCK);
                        byte[] buff = ByteConverter.objectToBytes(sentPacket);
                        DatagramPacket dp = new DatagramPacket(buff, buff.length, receiveAddr, receivePort);
                        ByteConverter.bytesToObject(dp.getData());
                        socket.send(dp);
                        if (base == nextSeq) startTime = new Date();
                        nextSeq++;
                    }
                }
                // 如果不是最后一块准备文件切换，在ACK最后一个数据报后切换
                while (base < sentDataList.size() + (currentBlock - 1) * FileIO.MAX_PACK_PER_BLOCK
                        && currentBlock < blockSum - 1) {}
            }

            // 发送完成，需要确定ACK最后一个数据包后才能断开
            while (true) {
                // 注意：此时currentBlock已经再加上1
                if (currAck == sentDataList.size() - 1 + (currentBlock - 1) * FileIO.MAX_PACK_PER_BLOCK) {
                    byte[] buff = ByteConverter.objectToBytes(new Packet(-1, -1, false, true, rwnd, null, ""));
                    DatagramPacket dp = new DatagramPacket(buff, buff.length, receiveAddr, receivePort);
                    socket.send(dp);
                    System.out.println("Sending Success!");
                    socket.disconnect();
                    socket.close();
                    return;
                }
            }
        } catch (SocketException e) {
            System.out.println("Fail to create socket!");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Fail to send packets");
            e.printStackTrace();
        }
    }

    // 接收ACK包的线程
    class RecvAck implements Runnable {
        @Override
        public void run() {
            try {
                while (isConneted) {
                    byte[] buff = new byte[BUFSIZE];
                    DatagramPacket dp = new DatagramPacket(buff, buff.length);
                    socket.receive(dp);
                    Packet packet = ByteConverter.bytesToObject(buff);
                    base = packet.getAck() + 1;
                    rwnd = packet.getRwnd();
                    // 如果接收方的BUFF已经满
                    if (rwnd == 0) {
                        isFull = true;
                    } else
                        isFull = false;

                    // 检测冗余Ack
                    if (currAck == packet.getAck()) {
                        if (isQuickRecover) {
                            cwnd++;
                        } else {
                            duplicateAck++;
                        }

                        // 3个冗余Ack，快速重传
                        if (duplicateAck == 3) {
                            // 更新拥塞窗口
                            ssthresh = cwnd / 2;
                            cwnd = ssthresh + 3;
                            isQuickRecover = true;
                            SendPacket(base);
                        }
                    } else {
                        duplicateAck = 0;

                        // 拥塞控制
                        if (isQuickRecover) {
                            // 快速恢复时，收到新的Ack，转到拥塞避免状态
                            isQuickRecover = false;
                            cwnd = ssthresh;
                        } else {
                            if (cwnd >= ssthresh) {
                                cwnd += 1 / cwnd; // 拥塞避免
                            } else {
                                cwnd++; // 慢启动
                            }
                        }
                    }

                    currAck = packet.getAck();
                    if (base != nextSeq)
                        startTime = new Date();
                }
            } catch (IOException e) {
                System.out.println("Fail to receive packets");
                e.printStackTrace();
            }
        }
    }

    private void SendPacket(int seq) {
        try {
            byte[] buff = ByteConverter.objectToBytes(sentDataList.get(seq - currentBlock * FileIO.MAX_PACK_PER_BLOCK));
            DatagramPacket dp = new DatagramPacket(buff, buff.length, receiveAddr, receivePort);
            Packet packet = ByteConverter.bytesToObject(dp.getData());
            System.out.println("Send: packet index->" + packet.getSeq());
            socket.send(dp);
        } catch (IOException e) {
            System.out.println("Fail to send packets");
            e.printStackTrace();
        }
    }

    // 判断是否超时的线程
    class TimeCounter implements Runnable {
        @Override
        public void run() {
            while (isConneted) {
                // 如果在读取数据，不进行计时，更新时间
                while (isReRoad) {
                    startTime = new Date();
                }
                long curr_time = new Date().getTime();
                // 因为有快速重传，所以阈值设为500
                if (curr_time - startTime.getTime() > 500) {
                    startTime = new Date();
                    timeOut();
                }
            }
        }
    }

    // 超时引发重传事件
    private void timeOut() {
        // 更新拥塞窗口
        isQuickRecover = false;
        duplicateAck = 0;
        ssthresh = cwnd / 2;
        cwnd = 1;

        // 记录base值和nextSeq值，防止接收线程对其造成改变
        reSending = true;
        // 只发送一个报可能出现卡死，故使用GBN的机制
        for (int i = base; i < nextSeq; ++i) {
            while (rwnd <= 0) System.out.println("Reciever has no enough buffer!");
            SendPacket(i);
        }
        reSending = false;
    }
}