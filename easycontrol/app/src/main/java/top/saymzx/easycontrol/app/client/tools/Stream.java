package top.saymzx.easycontrol.app.client.tools;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Stream {
  public byte readByte() throws IOException, InterruptedException;

  public int readInt() throws IOException, InterruptedException;

  public long readLong() throws IOException, InterruptedException;

  public ByteBuffer readByteArray(int size) throws IOException, InterruptedException;

  public void write(int mode, ByteBuffer byteBuffer) throws Exception;

  public void close();
}
