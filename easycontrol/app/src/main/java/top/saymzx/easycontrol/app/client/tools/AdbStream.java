package top.saymzx.easycontrol.app.client.tools;

import java.io.IOException;
import java.nio.ByteBuffer;

import top.saymzx.easycontrol.app.buffer.BufferStream;

public class AdbStream implements Stream {
  private final BufferStream bufferStream;

  public AdbStream(BufferStream bufferStream) {
    this.bufferStream = bufferStream;
  }

  @Override
  public byte readByte() throws IOException, InterruptedException {
    return bufferStream.readByte();
  }

  @Override
  public int readInt() throws IOException, InterruptedException {
    return bufferStream.readInt();
  }

  @Override
  public long readLong() throws IOException, InterruptedException {
    return bufferStream.readLong();
  }

  @Override
  public ByteBuffer readByteArray(int size) throws IOException, InterruptedException {
    return bufferStream.readByteArray(size);
  }

  @Override
  public void write(int mode, ByteBuffer byteBuffer) throws Exception {
    if (byteBuffer != null) {
      ByteBuffer header = ByteBuffer.allocate(8);
      header.putInt(mode);
      header.putInt(byteBuffer.remaining());
      header.flip();
      bufferStream.write(header);
      bufferStream.write(byteBuffer);
    } else {
      ByteBuffer header = ByteBuffer.allocate(4);
      header.putInt(mode);
      header.flip();
      bufferStream.write(header);
    }
  }

  @Override
  public void close()  {
    bufferStream.close();
  }
}
