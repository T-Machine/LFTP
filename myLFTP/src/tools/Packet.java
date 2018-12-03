package tools;

import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Packet implements Serializable {
	private Integer ack;		//确认号
	private Integer seq;		//序列号
	private boolean ACK;	//ACK标志位
	private boolean FIN;	//FIN标志位
	private Integer rwnd;		//接收窗口(流量控制)
	private byte[] data;	//数据
	private String filename;
	
	public Packet(int ack, int seq, boolean ACK, boolean FIN, int rwnd, byte[] data, String fn) {
		this.ack = ack;
		this.seq = seq;
		this.ACK = ACK;
		this.FIN = FIN;
		this.rwnd = rwnd;
		this.data = data;
		this.filename = fn;
	}

	public static void sendStringParketTo(DatagramSocket socket, String msg, InetAddress address, int port) {
		try {
			byte[] tmp = ByteConverter.objectToBytes(new Packet(0, -1, false, false, -1, msg.getBytes(), ""));
			DatagramPacket portPack = new DatagramPacket(tmp, tmp.length, address, port);
			socket.send(portPack);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	public void setAck(int ack) {
		this.ack = ack;
		this.ACK = true;
	}
	public Integer getAck() {
		return ack;
	}
	
	public void setSeq(int seq) {
		this.seq = seq;
	}
	public Integer getSeq() {
		return seq;
	}
	
	public void setACK(boolean aCK) {
		ACK = aCK;
	}
	public boolean isACK() {
		return ACK;
	}
	
	public void setFIN(boolean fIN) {
		FIN = fIN;
	}
	public boolean isFIN() {
		return FIN;
	}
	
	public void setRwnd(int rwnd) {
		this.rwnd = rwnd;
	}
	public Integer getRwnd() {
		return rwnd;
	}
	
	public void setData(byte[] data) {
		this.data = data;
	}
	public byte[] getData() {
		return data;
	}

	public void setAck(Integer ack) {
		this.ack = ack;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public void setRwnd(Integer rwnd) {
		this.rwnd = rwnd;
	}

	public void setSeq(Integer seq) {
		this.seq = seq;
	}
	public String getFilename(){ return  filename; }
}