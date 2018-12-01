package tools;

import java.io.Serializable;

public class Packet implements Serializable {
	private int ack;		//ȷ�Ϻ�
	private int seq;		//���к�
	private boolean ACK;	//ACK��־λ
	private boolean FIN;	//FIN��־λ
	private int rwwd;		//���մ���(��������)
	private byte[] data;	//����
	
	public Packet(int ack, int seq, boolean ACK, boolean FIN, int rwwd, byte[] data) {
		this.ack = ack;
		this.seq = seq;
		this.ACK = ACK;
		this.FIN = FIN;
		this.rwwd = rwwd;
		this.data = data;
	}
	
	public void setAck(int ack) {
		this.ack = ack;
		this.ACK = true;
	}
	public int getAck() {
		return ack;
	}
	
	public void setSeq(int seq) {
		this.seq = seq;
	}
	public int getSeq() {
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
	
	public void setRwwd(int rwwd) {
		this.rwwd = rwwd;
	}
	public int getRwwd() {
		return rwwd;
	}
	
	public void setData(byte[] data) {
		this.data = data;
	}
	public byte[] getData() {
		return data;
	}
}
