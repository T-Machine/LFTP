package service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.text.DecimalFormat;
import java.util.*;

import tools.ByteConverter;
import tools.FileIO;
import tools.Packet;
import tools.ProgressBar;

import static java.lang.Thread.sleep;

public class SendThread implements Runnable {
    private final static int BUFSIZE = 1024 * 1024;// 数据报的大小最大1024 * 1024
    private volatile int rwnd = 8192; // 未确认最大分组的编号
    private volatile int base = 1; // 基序号
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
    private volatile boolean isMainSent = false; //主线程是否送完所有数据
    private List<Packet> sentDataList; // 要发送的数据
    private InetAddress receiveAddr; // 目的地址
    private int sentPort; // 源端口
    private int receivePort; // 目的端口
    private DatagramSocket socket; // 用于发送数据包
    private String fileName; // 传输文件的文件名
    private volatile boolean isFull = false; // 判断接收方的缓存是否已经是满的，以调整速率
    private volatile boolean isConneted;
    private final static int SAMPLE = 1; // 速度采样时间
    private int totalPackageSum;
    private CallbackEnd callback;


    public interface CallbackEnd {
        void finish();
    }

    // 进行进度条的显示
    class ShowProgressBar implements Runnable{
        @Override
        public void run(){
            ProgressBar pg = new ProgressBar(0, 100, 60, '#');
            // 之前的时间 现在的时间 之前的ack idnex  现在的ack index
            Date before, current;
            int ackAfter, ackBefore;
            System.out.println("\n\n\n");
            try {
                while(isConneted){
                    ackBefore = currAck;
                    before = new Date();

                    // 睡眠
                    sleep(SAMPLE);
                    ackAfter = currAck;
                    current = new Date();
                    // 显示百分比
                    float haveGot = (float)ackAfter / (float)totalPackageSum;
                    float rate = (float)(ackAfter - ackBefore) / (float)(current.getTime() - before.getTime());
                    int val = Math.round(haveGot * 100);
                    rate = (rate / (float)SAMPLE) * 1000;
                    if(!isConneted) break;
                    if(Math.abs(rate - 0) < 1) continue;
                    pg.show(val, rate);
                }
                System.out.println("\n\n\n");
                System.out.println("Sending Success!");
            }catch (InterruptedException e){
                System.out.println("Interrupt!");
            }
        }
    }

    // 回调函数
    public SendThread(String _fileName, InetAddress _receiveAddr, int _sentPort, int _receivePort, CallbackEnd _callback) {
        this.fileName = _fileName;
        this.receiveAddr = _receiveAddr;
        this.sentPort = _sentPort;
        this.receivePort = _receivePort;
        this.startTime = new Date();
        this.callback = _callback;
    }

    @Override
    public void run() {
        // 创建socket
        try {
            this.socket = new DatagramSocket(sentPort);
            isConneted = true;
            // 新建ACK接收线程
            Thread ACKreceiver = new Thread(new RecvAck());
            Thread showProgessBar = new Thread(new ShowProgressBar());
            // 超时判断线程
            Thread timeCounter;
            timeCounter = new Thread(new TimeCounter());

            sentDataList = new ArrayList<>();
            // 记录一共读取的块的数量
            blockSum = FileIO.getBlockLength(fileName);
            // 记录数据包的总量
            totalPackageSum = FileIO.getByteLength(fileName);
            showProgessBar.start();
            // 读取-发送循环
            System.out.println("文件读取成功！");
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
                System.out.println("begin to divide");
                List<byte[]> byteData = FileIO.divideToList(fileName, currentBlock);
                if(byteData == null){
                    isConneted = false;
                    socket.close();
                    //回调
                    if(this.callback != null) {
                        callback.finish();
                    }
                    return;
                }
                System.out.println("create package");
                for (int j = 0; j < byteData.size(); j++) {
                    int packetIndex = j + currentBlock * FileIO.MAX_PACK_PER_BLOCK;
                    dataPacket = new Packet(0, packetIndex, false, false, 0, byteData.get(j), fileName);
                    if(packetIndex == 0){
                        dataPacket.setTotalPackage(totalPackageSum);
                    }
                    sentDataList.add(dataPacket);
                }
                isReRoad = false;
                // 开始计时
                startTime = new Date();
                System.out.println("Begin to send");
                // 启动发送数据包
                while (nextSeq < sentDataList.size() + currentBlock * FileIO.MAX_PACK_PER_BLOCK) {
                    System.out.println("before isNull" + base);
                    // 如果接收方的BUFF满发送空的数据报
                    if (isFull) {
                        byte[] tmp = ByteConverter.objectToBytes(new Packet(0, -1, false, false, -1, null, ""));
                        DatagramPacket tmpPack = new DatagramPacket(tmp, tmp.length, receiveAddr, receivePort);
                        socket.send(tmpPack);
                        continue;
                    }
                    System.out.println("before send" + base);
                    System.out.println(isFull);
                    // 拥塞控制和流量控制
                    int threshold = rwnd < (int) cwnd ? rwnd : (int) cwnd;
                    if(threshold <= 0) threshold = 1;
                    System.out.println(threshold + " | " + cwnd + " | " + rwnd + " + " + base);
                    System.out.println(reSending + " | " + nextSeq);
                    if (nextSeq <= base + threshold && reSending == false) {
                        System.out.println("send" + currAck);
                        Packet sentPacket = sentDataList.get(nextSeq - currentBlock * FileIO.MAX_PACK_PER_BLOCK);
                        byte[] buff = ByteConverter.objectToBytes(sentPacket);
                        DatagramPacket dp = new DatagramPacket(buff, buff.length, receiveAddr, receivePort);
                        ByteConverter.bytesToObject(dp.getData());
                        socket.send(dp);
                        System.out.println("endSend" + currAck);
                        if (base == nextSeq) startTime = new Date();
                        nextSeq++;
                    }
                }
                System.out.println("Begin to switch" + currentBlock);
                // 如果不是最后一块准备文件切换，在ACK最后一个数据报后切换
                while (base < sentDataList.size() + (currentBlock - 1) * FileIO.MAX_PACK_PER_BLOCK
                        && currentBlock < blockSum - 1) {}
            }

            isMainSent = true;
            // 发送完成，需要确定ACK最后一个数据包后才能断开
            while (true) {
                // 注意：此时currentBlock已经再加上1
                if (currAck == sentDataList.size() - 1 + (currentBlock - 1) * FileIO.MAX_PACK_PER_BLOCK) {
                    byte[] buff = ByteConverter.objectToBytes(new Packet(-1, -1, false, true, rwnd, null, ""));
                    DatagramPacket dp = new DatagramPacket(buff, buff.length, receiveAddr, receivePort);
                    socket.send(dp);
                    isConneted = false;
                    socket.close();
                    //回调
                    if(this.callback != null) {
                        callback.finish();
                    }
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
                            if(ssthresh <= 0) {
                                ssthresh = 1;
                            }
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
                    if(!isConneted) break;
                    if(packet.getAckBoolean() && currAck == totalPackageSum - 1 && isMainSent) break;
                }
            } catch (IOException e) {
                System.out.println("Fail to receive packets");
                e.printStackTrace();
            }
        }
    }

    private void SendPacket(int seq) {
        try {
            // jiance
            int index = isMainSent ? seq - (currentBlock - 1) * FileIO.MAX_PACK_PER_BLOCK : seq - currentBlock * FileIO.MAX_PACK_PER_BLOCK;
            byte[] buff = ByteConverter.objectToBytes(sentDataList.get(index));
            DatagramPacket dp = new DatagramPacket(buff, buff.length, receiveAddr, receivePort);
            Packet packet = ByteConverter.bytesToObject(dp.getData());
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
                // 如果在读取数据，不进行计时，更新时间rt
                while (isReRoad) {
                    startTime = new Date();
                }
                long curr_time = new Date().getTime();
                // 因为有快速重传，所以阈值设为500
                if (curr_time - startTime.getTime() > 500) {
                    startTime = new Date();
                    timeOut();
                }
                if(isMainSent && currAck == totalPackageSum - 1) break;
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
        int myBase = base;
        if(myBase >= 1) myBase--;
        // 只发送一个报可能出现卡死，故使用GBN的机制
        for (int i = myBase; isConneted && i >= 0 && i < nextSeq; ++i) {
            while (rwnd <= 0) continue;
            SendPacket(i);
        }
        reSending = false;
    }
}