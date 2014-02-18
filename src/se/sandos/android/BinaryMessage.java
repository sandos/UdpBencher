package se.sandos.android;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.util.Log;

public class BinaryMessage {
	private byte[] writeBuf;
	private ByteBuffer writeBuffer;
	private byte[] readBuf;
	private ByteBuffer readBuffer;
	
	private static final int DEFAULT_BUFFER_SIZE = 1024;
	
	public BinaryMessage()
	{
		writeBuf = new byte[DEFAULT_BUFFER_SIZE];
		writeBuffer = ByteBuffer.wrap(writeBuf);
		readBuf = new byte[DEFAULT_BUFFER_SIZE];
		readBuffer = ByteBuffer.wrap(readBuf);
	}
	
	public byte[] getInternalBuffer()
	{
		return writeBuffer.array();
	}
	
	public int writtenLength()
	{
		return writeBuffer.position();
	}
	
	public void reset()
	{
		writeBuffer.position(0);
		readBuffer.position(0);
	}
	
	public void parseFrom(byte[] data)
	{
		System.arraycopy(data, 0, readBuf, 0, Math.min(data.length, readBuf.length));
		readBuffer.position(0);
	}
	
	public int readInt() throws IOException {
		int r = readBuffer.getInt();
		return r;
	}

	public long readLong() throws IOException {
		long r = readBuffer.getLong();
		return r;
	}
	
	public BinaryMessage writeInt(int v) throws IOException
	{
		writeBuffer.putInt(v);
		return this;
	}
	
	public BinaryMessage writeLong(long v) throws IOException
	{
		writeBuffer.putLong(v);
		return this;
	}
	
	public int getOffset()
	{
		return writeBuffer.arrayOffset();
	}
	
	public int limit()
	{
		return writeBuffer.limit();
	}
	
	public int capa()
	{
		return writeBuffer.capacity();
	}
	
	public byte[] getWrittenCopy()
	{
		return Arrays.copyOf(writeBuf, writtenLength());
	}
	
	public byte[] getWritten()
	{
//		return Arrays.copyOf(writeBuffer.array(), writeBuffer.position());
		return writeBuf;
//		byte[] d = new byte[writeBuffer.position()];
//		Log.v("majs", "Size " + d.length);
//		writeBuffer.flip();
//		writeBuffer.get(d);
//		return d;
	}
	
}
