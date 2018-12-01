package service;

import java.io.IOException;
import java.io.ObjectInputStream.GetField;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;

import tools.ByteConverter;
import tools.FileIO;
import tools.Packet;

public class ReceiveThread implements Runnable {
	private final static int BUFSIZE = 1024 * 1024;
	private DatagramSocket socket;				// UDP连接DatagramSocket
	private int serverPort;						// 服务端接收端口
	private int expectedseqnum;					// 期望收到的序列号
	InetAddress clientInetAddress;				// 客户端发送IP地址
	int clientPort;								// 客户端发送端口
	boolean isRandom = false;                   // 是否进入失序模式
	int rwwd = 10;                              // 窗口大小
	List<Packet> randomBuff;                    // 存储失序的包
	
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

	@Override
	public void run() {
		try {
			socket = new DatagramSocket(serverPort);
			byte[] buffer = new byte[BUFSIZE];
			List<byte[]> data = new ArrayList<>();
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
				// 接收到发送完成的信号数据包，跳出循环
				if (packet.isFIN() == true) break;
				// 接收到期望收到的数据包
				if(packet.getSeq() == expectedseqnum) {
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
							data.add(packet.getData());
							// 利用缓存读取数据
							for(int i = 0; i < index; i++){
								System.out.println("Yes!");
								data.add(randomBuff.get(i).getData());
							}
							for(int i = 0; i < index; i++){
								randomBuff.remove(0);
							}
							isRandom = false;
							randomBuff.clear();
							expectedseqnum += (1 + index);
							// 返回一个正确接受的ACK包
							Packet ackPacket = new Packet(expectedseqnum, -1, true, false, rwwd, null);
							byte[] ackBuffer = ByteConverter.objectToBytes(ackPacket);
							DatagramPacket ackdp = new DatagramPacket(ackBuffer, ackBuffer.length, clientInetAddress, clientPort);
							socket.send(ackdp);
							System.out.println("ACK(right): " + expectedseqnum + " expect: " + expectedseqnum + " get: " + packet.getSeq());
							socket.receive(dp);
							continue;
						}
					}
					data.add(packet.getData());
					// 获得新的窗口大小
					rwwd = packet.getRwwd();
					// 返回一个正确接受的ACK包
					Packet ackPacket = new Packet(expectedseqnum, -1, true, false, rwwd, null);
					byte[] ackBuffer = ByteConverter.objectToBytes(ackPacket);
					DatagramPacket ackdp = new DatagramPacket(ackBuffer, ackBuffer.length, clientInetAddress, clientPort);
					socket.send(ackdp);
					System.out.println("ACK(right): " + expectedseqnum + " expect: " + expectedseqnum + " get: " + packet.getSeq());
					// 期待下一个序列号的数据包
					expectedseqnum++;
					// 阻塞等待下一个数据包
					socket.receive(dp);
				}
				// 接受到非期望数据包
				else {
					//如果之前不是失序的
					isRandom = true;
					// 重传数据丢掉
					if(randomBuff.size() <= rwwd && expectedseqnum < packet.getSeq()){
						randomBuff.add(packet);
						//从小到大排序
						Collections.sort(randomBuff, new Comparator<Packet>() {
							public int compare(Packet o1, Packet o2) {
								return o1.getSeq().compareTo(o2.getSeq());
							}
						});
					}

					// 返回一个错误接受的ACK包
					Packet ackPacket = new Packet(expectedseqnum-1, -1, true, false, rwwd, null);
					byte[] ackBuffer = ByteConverter.objectToBytes(ackPacket);
					DatagramPacket ackdp = new DatagramPacket(ackBuffer, ackBuffer.length, clientInetAddress, clientPort);
					socket.send(ackdp);
					System.out.println("ACK(wrong): " + (expectedseqnum-1) + "——————expect: " + expectedseqnum + "——————get: " + packet.getSeq());
					// 阻塞等待下一个数据包
					socket.receive(dp);
				}
			}
			String dirString = "output.flac";
			FileIO.byte2file(dirString, data);
			System.out.println("接收并写入完毕！");
		}
		catch (SocketException e) {
			System.out.println("ReceiveThread: 创建socket出错");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("ReceiveThread: 接收数据包出错");
			e.printStackTrace();
		}
	}
	

}