package tools;

import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Packet implements Serializable {
	private final static int BUFLENGTH = 8192;
	private final static int BUFSIZE = BUFLENGTH * 1024;
	private Integer ack; // 确认的分组编号，用于接收端
	private Integer seq; // 该分组的编号，用于发送端
	private boolean ACK; // ACK标志
	private boolean FIN; // FIN标志
	private Integer rwnd; // 接收窗口
	private byte[] pkData; // 数据端
	private String fileName; // 文件名
	private int totalPackage; // 包的总数

	public Packet(int _ack, int _seq, boolean _ACK, boolean _FIN, int _rwnd, byte[] _pkData, String _fn) {
		this.ack = _ack;
		this.seq = _seq;
		this.ACK = _ACK;
		this.FIN = _FIN;
		this.rwnd = _rwnd;
		this.pkData = _pkData;
		this.fileName = _fn;
		this.totalPackage = totalPackage;
	}

	// 确认信息的数据报
	public static void sendStringParketTo(DatagramSocket socket, String msg, InetAddress address, int port) {
		try {
			byte[] tmp = ByteConverter.objectToBytes(new Packet(0, -1, false, false, -1, msg.getBytes(), ""));
			DatagramPacket portPack = new DatagramPacket(tmp, tmp.length, address, port);
			socket.send(portPack);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	// 接收数据包信息
	public static String getStringParketFrom(DatagramSocket socket) {
		try {
			byte[] buffer = new byte[BUFSIZE];
			DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
			socket.receive(dp);
			Packet pkt = ByteConverter.bytesToObject(buffer);
			String info = new String(pkt.getData());
			return info;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	public Integer getAck() {
		return ack;
	}
	public boolean getACKBoolean() {
		return ACK;
	}

	public Integer getSeq() {
		return seq;
	}

	public boolean getFIN() {
		return FIN;
	}

	public Integer getRwnd() {
		return rwnd;
	}

	public void setData(byte[] pkData) {
		this.pkData = pkData;
	}

	public byte[] getData() {
		return pkData;
	}

	public void setFilename(String fileName) {
		this.fileName = fileName;
	}

	public String getFilename() {
		return fileName;
	}

	public void setTotalPackage(int totalPackage){ this.totalPackage = totalPackage;}
	public int getTotalPackage(){ return this.totalPackage; }
}