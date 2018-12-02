package service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;

import tools.ByteConverter;
import tools.Packet;

public class SendThread implements Runnable {
	private final static int BUFSIZE = 1024 * 1024;
	private List<Packet> data;						//要发送的数据
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
	private volatile long EstimatedRTT  = 0;		//估计往返时间
	private volatile long DevRTT  = 0;				//RTT偏差
	private volatile long TimeoutInterval  = 300;	//超时时间
	private boolean isFull = false;                 // 判断接收方的缓存是否已经是满的，以调整速率
	private Map<Integer, Long> SendTimeMap = new HashMap<Integer, Long>(); //发送时间表
	private boolean isConneted;
	private volatile double cwnd = 1;					//拥塞窗口
	private volatile double ssthresh = 60;				//慢启动阈值
	private volatile boolean isQuickRecover = false;	//是否处于快速恢复状态
	private volatile Date startTime;


	public SendThread(List<Packet> data, InetAddress address, int sourcePort, int destPort, String dir) {
		this.data = data;
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
		recv_ack_thread.start();

		// 启动超时判断处理线程
		Thread time_out_threadThread;
		time_out_threadThread = new Thread(new TimeOut());
		time_out_threadThread.start();

		//启动发送数据包
		try {
			startTime = new Date();
			while (nextSeq < data.size()) {
				if(isFull == true) {
					 byte[] tmp = ByteConverter.objectToBytes(new Packet(0, -1, false, false, -1, null));
					 DatagramPacket tmpPack = new DatagramPacket(tmp, tmp.length, address, destPort);
						 socket.send(tmpPack);
					continue;
				}
				int threshold = rwnd < (int)cwnd ? rwnd : (int)cwnd;
				if (nextSeq <= base + threshold && retrans == false) {
					//if (nextSeq % N != 0) {
					Packet sentPacket = data.get(nextSeq);
					byte[] buffer = ByteConverter.objectToBytes(sentPacket);
					DatagramPacket dp = new DatagramPacket(buffer, buffer.length, address, destPort);
					Packet packet = ByteConverter.bytesToObject(dp.getData());

						socket.send(dp);

//					System.out.println("发送的分组序号: " + packet.getSeq());

					//更新时间表
					SendTimeMap.put(nextSeq, System.nanoTime());

					if (base == nextSeq) startTimer();
					//}
					nextSeq++;
				}
			}
		} catch (IOException e) {
			System.out.println("SendThread: 发送数据包出错");
			e.printStackTrace();
		}

		//传输完成时，发送一个FIN包告知接收方
		while (true) {
			if (currAck == data.size() - 1) {
				try {
					System.out.print("发送终止packet");
					byte[] buffer = ByteConverter.objectToBytes(new Packet(-1, -1, false, true, rwnd, null));
					DatagramPacket dp = new DatagramPacket(buffer, buffer.length, address, destPort);
					socket.send(dp);
					Date endTime = new Date();
					float speed = data.size() / (endTime.getTime() - startTime.getTime());
					speed *= 1000;
					System.out.println(speed + "kB/s");
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
//					System.out.println("确认分组: " + packet.getAck());
					base = packet.getAck() + 1;
					rwnd = packet.getRwnd();
					// 如果已经满
					if (rwnd == 0) {
						isFull = true;
					} else isFull = false;

					//获取该包发送的时间
					Long sendTime = SendTimeMap.remove(packet.getAck());
					if(sendTime != null) {
						UpdateTimeout(System.nanoTime() - sendTime);
					}

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
							System.out.println("启动快速重传");
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

					//确认接收最后一个分组
					if (packet.getAck() == data.size() - 1) break;
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
				long start_time = date.getTime();
				long curr_time = new Date().getTime();
				//超过0.3秒时触发超时
				if (curr_time - start_time > 300) {
					startTimer();
					TimeoutInterval *= 2;
					timeOut();
				}

				//确认接收最后一个分组时停止计时
				if (currAck == data.size() - 1) break;
			}
		}
	}


	private void SendPacket(int seq) {
		try {
			byte[] buffer = ByteConverter.objectToBytes(data.get(seq));
			DatagramPacket dp = new DatagramPacket(buffer, buffer.length, address, destPort);
			Packet packet = ByteConverter.bytesToObject(dp.getData());
			System.out.println("发送的分组序号: " + packet.getSeq());
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
				byte[] buffer = ByteConverter.objectToBytes(data.get(i));
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

	private void UpdateTimeout(long SampleRTT) {

		EstimatedRTT = (long)(0.875 * EstimatedRTT + 0.125 * SampleRTT);
		DevRTT = (long)(0.75 * DevRTT + 0.25 * Math.abs(SampleRTT - EstimatedRTT));
		TimeoutInterval = EstimatedRTT + 4 * DevRTT;
	}

	//启动定时器
	private void startTimer() {
		date = new Date();
	}
}