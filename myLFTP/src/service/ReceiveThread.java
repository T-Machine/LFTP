package service;

import java.io.IOException;
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
import tools.ProgressBar;

import static java.lang.Thread.sleep;

public class ReceiveThread implements Runnable {
	private final static int BUFLENGTH = 8 * 1024; // BUFF大小（单位KB）
	private final static int BUFSIZE = BUFLENGTH * 1024; // BUFF大小（单位byte）
	private final static int SAMPLE = 1; // 速度采样时间
	private DatagramSocket socket; // UDP连接DatagramSocket
	private int receivePort; // 服务端接收端口
	private List<Packet> randomBuff; // 选择重传的缓存
	private String fileName; // 存储的文件名
	private InetAddress senderAddr; // 发送方IP地址
	private int totalPackageSum; // 数据包的总数
	private int senderPort; // 发送方的发送端口
	private volatile int ackIndex; // 确认收到的分组编号
	private volatile boolean isRandom = false; // 判断接收的分组顺序是否是正常的
	private volatile int rwnd = BUFLENGTH; // 接收窗口大小
	private volatile int writeCount = 0; // 写入数据的分组数量
	private volatile boolean isConneted; // 判断当前是否仍在连接
	private volatile Lock fileLock = new ReentrantLock(); // 文件读写的锁
	private volatile List<byte[]> receiveDataList = new ArrayList<>();// 存储接收到分组数据的list
	private CallbackEnd callback;

	public interface CallbackEnd {
		void finish();
	}

	public ReceiveThread(int _receivePort, CallbackEnd _callback) {
		this.receivePort = _receivePort;
		this.callback = _callback;
		ackIndex = 0;
	}

	// 设置发送方的ip地址
	public void setSenderAddr(InetAddress _senderAddr) {
		this.senderAddr = _senderAddr;
	}

	public void setSenderPort(int _receivePort) {
		this.senderPort = _receivePort;
	}

	// 进行文件写入的线程
	class WriteFile implements Runnable {
		@Override
		synchronized public void run() {
			// 判断是否仍然在连接
			while (isConneted) {
				// 接收数据列表不能为空
				if (receiveDataList.isEmpty())
					continue;
				writeFile();
			}
		}
	}

	// 进行进度条的显示
	class ShowProgressBar implements Runnable{
		@Override
		public void run(){
			ProgressBar pg = new ProgressBar(0, 100, 60, '#');
			// 之前的时间 现在的时间 之前的ack idnex  现在的ack index
			Date before;
			Date current;
			int ackBefore;
			int ackAfter;
			System.out.println("\n\n\n");
			try {
				while(isConneted){
					before = new Date();
					ackBefore = ackIndex;
					// 睡眠
					sleep(SAMPLE);
					current = new Date();
					ackAfter = ackIndex;
					// 显示百分比
					float haveGot = (float)ackAfter / (float)totalPackageSum;
					int val = Math.round(haveGot * 100);
					float rate = (float)(ackAfter - ackBefore) / (float)(current.getTime() - before.getTime());
					rate /= (float)SAMPLE;
					rate *= 1000;
					if(!isConneted) break;
					if(Math.abs(rate - 0) < 1) continue;
					pg.show(val, rate);

				}
				System.out.println("\n\n\n");
				System.out.println("Writing Success!");
			}catch (InterruptedException e){
				System.out.println("Interrupt!");
			}
		}
	}

	// 接收线程打开
	@Override
	synchronized public void run() {
		try {
			// 新建套接字
			socket = new DatagramSocket(receivePort);
			isConneted = true;
			// 调用多线程在等待接收的时候写入数据，避免缓存过满
			Thread fileWriter = new Thread(new WriteFile());
			Thread showProgressBar = new Thread(new ShowProgressBar());
			fileWriter.start();
			// 一次性接收分组的数量
			byte[] receiverBuff = new byte[BUFSIZE];
			// 选择重传的缓存
			randomBuff = new ArrayList<>();
			// 获得Packet
			DatagramPacket dp = new DatagramPacket(receiverBuff, receiverBuff.length);
			// 阻塞等待第一个数据包
			socket.receive(dp);
			// 获取客户端IP和发送端口
			setSenderAddr(dp.getAddress());
			setSenderPort(dp.getPort());
			System.out.println("开始接收数据" + dp.getAddress().toString() + " port: " + dp.getPort());
			// 直到接收到最后一个FIN的数据包，一直处于接收状态
			while (true) {
				Packet packet = ByteConverter.bytesToObject(receiverBuff);
				// 接收完成
				if (packet.getFIN())
					break;
				// 第一次接收加上文件名
				if (ackIndex == 0) {
					fileName = packet.getFilename();
					totalPackageSum = packet.getTotalPackage();
					// 加上完整的文件名
					showProgressBar.start();
				}
				// 缓存空间不足
				if (rwnd == 0) {
					// 清空List，重置接收窗口空闲空间
					writeFile();
					// 因为RWND Overflow导致错误
					sendFailACK();

				} else if (packet.getSeq() == ackIndex) {
					// 如果符合选择重传，从BUFF中读取
					if (isRandom) {
						int index = 0;
						// 遍历缓存区，查看是否出现了符合缓存的分组
						while (index < randomBuff.size()) {
							if (randomBuff.get(index).getSeq() != packet.getSeq() + index + 1) {
								break;
							}
							index++;
						}
						// 存在与否
						if (index > 0) {
							// 读取DataList加锁
							fileLock.lock();
							receiveDataList.add(packet.getData());
							rwnd--;
							// 利用缓存读取数据
							for (int i = 0; i < index; i++) {
								receiveDataList.add(randomBuff.get(i).getData());
								rwnd--;
							}
							fileLock.unlock();
							// 清除BUFF
							for (int i = 0; i < index; i++) {
								randomBuff.remove(0);
							}
							// 顺序正常
							isRandom = false;
							randomBuff.clear();
							ackIndex += index;
							sendSuccessACK();
							ackIndex++;
							// 继续阻塞
							socket.receive(dp);
							continue;
						}
					}
					// 正常顺序的读取
					fileLock.lock();
					receiveDataList.add(packet.getData());
					rwnd--;
					fileLock.unlock();
					sendSuccessACK();
					ackIndex++;
					// 阻塞等待下一个数据包
					socket.receive(dp);
				}
				// 接受到非期望数据包
				else {
					// 选择重传的分组编号不能是之前重传的
					if (randomBuff.size() <= rwnd && ackIndex < packet.getSeq()) {
						isRandom = true;
						randomBuff.add(packet);
						// 从小到大排序
						Collections.sort(randomBuff, new Comparator<Packet>() {
							public int compare(Packet o1, Packet o2) {
								return o1.getSeq().compareTo(o2.getSeq());
							}
						});
					}
					// 因为非期望数据包导致错误
					sendFailACK();
					// 阻塞等待下一个数据包
					socket.receive(dp);
				}
			}
			isConneted = false;
			// 加锁
			fileLock.lock();
			FileIO.byte2file(fileName, receiveDataList);
			receiveDataList.clear();
			fileLock.unlock();
			socket.close();
		} catch (SocketException e) {
			System.out.println("Fail to create socket!");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Fail to receive packets");
			e.printStackTrace();
		}

		//回调
		if(this.callback != null) {
			callback.finish();
		}
	}

	private void sendSuccessACK() {
		try {
			// 返回一个正确接受ACK包
			Packet successAckPacket = new Packet(ackIndex, -1, true, false, rwnd, null, "");
			byte[] ackBuffer = ByteConverter.objectToBytes(successAckPacket);
			DatagramPacket ackdp = new DatagramPacket(ackBuffer, ackBuffer.length, senderAddr, senderPort);
			socket.send(ackdp);
		} catch (SocketException e) {
			System.out.println("Fail to create socket!");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Fail to receive packets");
			e.printStackTrace();
		}
	}

	private void sendFailACK() {
		try {
			// 返回一个错误接受ACK包
			Packet errorAckPacket = new Packet(ackIndex - 1, -1, false, false, rwnd, null, "");
			byte[] ackBuffer = ByteConverter.objectToBytes(errorAckPacket);
			DatagramPacket ackdp = new DatagramPacket(ackBuffer, ackBuffer.length, senderAddr, senderPort);
			socket.send(ackdp);
			// 因为RWND导致
		} catch (SocketException e) {
			System.out.println("Fail to create socket!");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Fail to receive packets");
			e.printStackTrace();
		}
	}

	// 进行文件的写入，需要加锁
	private void writeFile() {
		fileLock.lock();
		// 写入文件对应的分组编号
		writeCount += receiveDataList.size();
		FileIO.byte2file(fileName, receiveDataList);
		receiveDataList.clear();
		// 更新rwnd
		rwnd = BUFLENGTH - (ackIndex - writeCount);
		if (rwnd < 1)
			rwnd = 1;
		fileLock.unlock();
	}

}