package tools;

import java.io.Serializable;

public class Packet implements Serializable {
	private Integer ack;		//确认号
	private Integer seq;		//序列号
	private boolean ACK;	//ACK标志位
	private boolean FIN;	//FIN标志位
	private Integer rwnd;		//接收窗口(流量控制)
	private byte[] data;	//数据
	
	public Packet(int ack, int seq, boolean ACK, boolean FIN, int rwnd, byte[] data) {
		this.ack = ack;
		this.seq = seq;
		this.ACK = ACK;
		this.FIN = FIN;
		this.rwnd = rwnd;
		this.data = data;
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
}