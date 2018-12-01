package tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

//���ڴ���������ֽ�֮�以��ת���Ĺ�����
public class ByteConverter {
	public static<T> byte[] objectToBytes(T obj) {
		byte[] bytes = null;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream sout;
		try {
			sout = new ObjectOutputStream(out);
			sout.writeObject(obj);
			sout.flush();
			bytes = out.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bytes;
	}
	
	public static<T> T bytesToObject(byte[] bytes) {
		T t = null;
		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		ObjectInputStream sin;
		try {
			sin = new ObjectInputStream(in);
			t = (T)sin.readObject();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return t;
	}
}
