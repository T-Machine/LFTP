package service;

import java.io.IOException;
import java.io.ObjectInputStream.GetField;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import tools.ByteConverter;
import tools.FileIO;
import tools.Packet;

public class ReceiveThread implements Runnable {
	private final static int BUFSIZE = 1024 * 1024;
	private DatagramSocket socket;				// UDP����DatagramSocket
	private int serverPort;						// ����˽��ն˿�
	private int expectedseqnum;					// �����յ������к�
	InetAddress clientInetAddress;				// �ͻ��˷���IP��ַ
	int clientPort;								// �ͻ��˷��Ͷ˿�
	
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
			// �����ȴ���һ�����ݰ�
			socket.receive(dp);
			// ��ȡ�ͻ���IP�ͷ��Ͷ˿�
			setClientInetAddress(dp.getAddress());
			setClientPort(dp.getPort());
			System.out.println("���ڽ����ļ�����\n���ͷ���ַ����" + clientInetAddress.getAddress().toString() + ":" + clientPort + "\n");
			while (true) {
				Packet packet = ByteConverter.bytesToObject(buffer);
				// ���յ�������ɵ��ź����ݰ�������ѭ��
				if (packet.isFIN() == true) break;
				// ���յ������յ������ݰ�
				if(packet.getSeq() == expectedseqnum) {
					// ��ȡ���ݰ����ݽ�����
					data.add(packet.getData());
					//System.out.println("����Ƭ�Σ�" + packet.getSeq());
					// ����һ����ȷ���ܵ�ACK��
					Packet ackPacket = new Packet(expectedseqnum, -1, true, false, 1, null);
					byte[] ackBuffer = ByteConverter.objectToBytes(ackPacket);
					DatagramPacket ackdp = new DatagramPacket(ackBuffer, ackBuffer.length, clientInetAddress, clientPort);
					socket.send(ackdp);
					System.out.println("ACK(right): " + expectedseqnum + "������������expect: " + expectedseqnum + "������������get: " + packet.getSeq());
					// �ڴ���һ�����кŵ����ݰ�
					expectedseqnum++;
					// �����ȴ���һ�����ݰ�
					socket.receive(dp);
				}
				// ���ܵ����������ݰ�
				else {
					// ����һ��������ܵ�ACK��
					Packet ackPacket = new Packet(expectedseqnum-1, -1, true, false, 1, null);
					byte[] ackBuffer = ByteConverter.objectToBytes(ackPacket);
					DatagramPacket ackdp = new DatagramPacket(ackBuffer, ackBuffer.length, clientInetAddress, clientPort);
					socket.send(ackdp);
					System.out.println("ACK(wrong): " + (expectedseqnum-1) + "������������expect: " + expectedseqnum + "������������get: " + packet.getSeq());
					// �����ȴ���һ�����ݰ�
					socket.receive(dp);
				}
			}
			String dirString = "output.mp4";
			FileIO.byte2file(dirString, data);
			System.out.println("���ղ�д����ϣ�");
		}
		catch (SocketException e) {
			System.out.println("ReceiveThread: ����socket����");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("ReceiveThread: �������ݰ�����");
			e.printStackTrace();
		}
	}
	

}
