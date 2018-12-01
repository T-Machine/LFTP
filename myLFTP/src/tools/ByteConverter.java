package tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;


public class ByteConverter {
	// 将object转换为byte
	public static<T> byte[] toByte(T obj) {
		//byte 数据类型是8位、有符号的，以二进制补码表示的整数
		byte[] bytes = null;
		//字节数组输出流在内存中创建一个字节数组缓冲区，所有发送到输出流的数据保存在该字节数组缓冲区中。
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try(ObjectOutputStream out = new ObjectOutputStream(bos)){
			out.writeObject(obj);
			out.flush();
			return bos.toByteArray();
		} catch(IOException e){
			e.printStackTrace();
			return null;
		}
	}

	// 将读取的二进制流转换为obj
	public static<T> T toObject(byte[] bytes) {
		T t = null;
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		ObjectInputStream ois;
		try {
			ois = new ObjectInputStream(bis);
			t = (T)ois.readObject();
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
			return null;
		}
		return t;
	}
}