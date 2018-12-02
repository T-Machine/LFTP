package service;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream.GetField;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import tools.ByteConverter;
import tools.FileIO;
import tools.Packet;

import static java.lang.Thread.sleep;

public class ReceiveThread implements Runnable {
	private final static int BUFLENGTH = 8192;
	private final static int BUFSIZE = BUFLENGTH * 1024;
	private DatagramSocket socket;				// UDP连接DatagramSocket
	private int serverPort;						// 服务端接收端口
	private int expectedseqnum;					// 期望收到的序列号
	InetAddress clientInetAddress;				// 客户端发送IP地址
	int clientPort;								// 客户端发送端口
	boolean isRandom = false;                   // 是否进入失序模式
	private volatile int rwnd = BUFLENGTH;                              // 窗口大小
	private volatile int writeCount = 0;        // 写入的数量
	private  volatile boolean isConneted;
	private List<Packet> randomBuff;            // 存储失序的包
	private String fileDic = "data/";           // 存储的文件目录
	private String fileName;                    // 存储的文件名
	private String totalFileName;               // 完整文件名
	private volatile Lock fileLock = new ReentrantLock();        //文件读写的锁
	private volatile List<byte[]> data = new ArrayList<>();       // 存储文件的list
	private volatile boolean okToWrite = false;           // 可以读写文件
	public ReceiveThread(int port) {
		this.serverPort = port;
		expectedseqnum = 0;
	}
	
	public InetAddress getClientInetAddress() {
		return clientInetAddress;
	}
	public void setClientInetAddress(InetAddress ia) {
		this.clientInetAddress = ia;
	}
	public int getClientPort() {
		return clientPort;
	}
	public void setClientPort(int port) {
		this.clientPort = port;
	}

	//判断是否超时的线程
	class WriteFile implements Runnable {
		@Override
		synchronized public void run() {
			while(isConneted){
				while(okToWrite){
					try{
						sleep(10);
						if(data.isEmpty()) continue;
						fileLock.lock();
						writeCount += data.size();
						FileIO.byte2file(totalFileName, data);
						data.clear();
						rwnd = BUFLENGTH - (expectedseqnum - 1 - writeCount);
						if(rwnd < 0) rwnd = 0;
						fileLock.unlock();
					}catch (InterruptedException e){
						System.out.println(Thread.currentThread().getName());
						Thread.currentThread().interrupt();
						System.out.println("after interrupt");
					}
				}
			}
		}
	}

	@Override
	synchronized public void run() {
		try {
			// 启动超时判断处理线程
			socket = new DatagramSocket(serverPort);
			isConneted = true;
			Thread file_threadThread;
			file_threadThread = new Thread(new WriteFile());
			file_threadThread.start();
			// 一次接收BUFLENGTH个分组
			byte[] buffer = new byte[BUFSIZE];
			//数据报
			DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
			List<Packet> randomBuff = new ArrayList<>();
			// 阻塞等待第一个数据包
			socket.receive(dp);
			// 获取客户端IP和发送端口
			setClientInetAddress(dp.getAddress());
			setClientPort(dp.getPort());
			System.out.println("正在接受文件传输\n发送方地址——" + clientInetAddress.getAddress().toString() + ":" + clientPort + "\n");
			while (true) {
				Packet packet = ByteConverter.bytesToObject(buffer);
				// 第0个
				if(packet.getSeq() == 0){
					// 通过新接收的文件名新建文件
							fileName = new String(packet.getData());
					totalFileName = fileDic + fileName;
					File file=new File(fileDic + fileName);
					if(file.exists()&&file.isFile()) {
						file.delete();
					}
					sendSuccessACK(packet);
					// 期待下一个序列号的数据包
					expectedseqnum++;
					// 阻塞等待下一个数据包
					socket.receive(dp);
					okToWrite = true;
					continue;
					// 等待第0个
				} else if(packet.getSeq() != 0 && expectedseqnum == 0) {
					sendFailACK(packet);
					System.out.println("ACK(wrong): " + (expectedseqnum-1) + "——————expect: " + expectedseqnum + "——————get: " + packet.getSeq());
					// 阻塞等待下一个数据包
					socket.receive(dp);
					continue;
				}
				// 接收到发送完成的信号数据包，跳出循环
				if (packet.isFIN() == true) break;
				// 缓存空间不足
				if(rwnd == 0) {

					System.out.println("RWND Overflow");
					// 清空List，重置接收窗口空闲空间
					fileLock.lock();
					writeCount += data.size();
					FileIO.byte2file(totalFileName, data);
					data.clear();
					rwnd = BUFLENGTH - (expectedseqnum - 1 - writeCount);
					if(rwnd < 0) rwnd = 0;
					fileLock.unlock();
					sendFailACK(packet);
					System.out.println("ACK(rwnd): " + (expectedseqnum-1) + " expect: " + expectedseqnum);
				}
				else if(packet.getSeq() == expectedseqnum) {
					// 提取数据包，递交数据
					// 判断收到的包是不是重发送的包
					if(isRandom){
						int index = 0;
						//遍历缓存区，查看是否出现了符合缓存的分组
						while(index < randomBuff.size()){
							if(randomBuff.get(index).getSeq() != packet.getSeq() + index + 1){
								break;
							}
							index++;
						}
						// 存在与否
						if(index > 0){
							System.out.println("Yes!");
							fileLock.lock();
							data.add(packet.getData());
							rwnd--;
							// 利用缓存读取数据
							for(int i = 0; i < index; i++){
								System.out.println("Yes!");
								data.add(randomBuff.get(i).getData());
								rwnd--;
							}
							fileLock.unlock();
							for(int i = 0; i < index; i++){
								randomBuff.remove(0);
							}
							isRandom = false;
							randomBuff.clear();
							expectedseqnum += (1 + index);
							// 返回一个正确接受的ACK包
							socket.receive(dp);
							continue;
						}
					}
					fileLock.lock();
					data.add(packet.getData());
					rwnd--;
					fileLock.unlock();
					sendSuccessACK(packet);
					// 期待下一个序列号的数据包
					expectedseqnum++;
					// 阻塞等待下一个数据包
					socket.receive(dp);
				}
				// 接受到非期望数据包
				else {
					// 不能存文件名！
					if(expectedseqnum != 0){
						//如果之前不是失序的
						isRandom = true;
						// 重传数据丢掉
						if(randomBuff.size() <= rwnd && expectedseqnum < packet.getSeq()){
							randomBuff.add(packet);
							//从小到大排序
							Collections.sort(randomBuff, new Comparator<Packet>() {
								public int compare(Packet o1, Packet o2) {
									return o1.getSeq().compareTo(o2.getSeq());
								}
							});
						}
					}
					sendFailACK(packet);
					System.out.println("ACK(wrong): " + (expectedseqnum-1) + "——————expect: " + expectedseqnum + "——————get: " + packet.getSeq());
					// 阻塞等待下一个数据包
					socket.receive(dp);
				}
			}
			fileLock.lock();
			FileIO.byte2file(totalFileName, data);
			data.clear();
			fileLock.unlock();
			System.out.println("接收并写入完毕！");
			socket.disconnect();
			socket.close();
			okToWrite = false;
			isConneted = false;
			file_threadThread.join();
			return;
		}
		catch (SocketException e) {
			System.out.println("ReceiveThread: 创建socket出错");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("ReceiveThread: 接收数据包出错");
			e.printStackTrace();
		} catch (InterruptedException e){
			System.out.println("Join err");
		}
	}

		private void sendSuccessACK(Packet packet){
		try {
			// 返回一个正确接受的ACK包
			Packet ackPacket = new Packet(expectedseqnum, -1, true, false, rwnd, null);
			byte[] ackBuffer = ByteConverter.objectToBytes(ackPacket);
			DatagramPacket ackdp = new DatagramPacket(ackBuffer, ackBuffer.length, clientInetAddress, clientPort);
			socket.send(ackdp);
			System.out.println("ACK(right): " + expectedseqnum + " expect: " + expectedseqnum + " get: " + packet.getSeq());
		} catch (SocketException e) {
			System.out.println("ReceiveThread: 创建socket出错");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("ReceiveThread: 接收数据包出错");
			e.printStackTrace();
		}
	}

	private void sendFailACK(Packet packet){
		try {
			// 返回一个错误接受的ACK包
			Packet ackPacket = new Packet(expectedseqnum-1, -1, true, false, rwnd, null);
			byte[] ackBuffer = ByteConverter.objectToBytes(ackPacket);
			DatagramPacket ackdp = new DatagramPacket(ackBuffer, ackBuffer.length, clientInetAddress, clientPort);
			socket.send(ackdp);
		} catch (SocketException e) {
			System.out.println("ReceiveThread: 创建socket出错");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("ReceiveThread: 接收数据包出错");
			e.printStackTrace();
		}
	}



}