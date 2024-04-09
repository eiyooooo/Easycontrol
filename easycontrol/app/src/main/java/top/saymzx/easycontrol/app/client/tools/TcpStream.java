package top.saymzx.easycontrol.app.client.tools;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class TcpStream implements Stream {
  private final Socket socket;
  private final DataInputStream inputStream;
  private final OutputStream outputStream;

  public TcpStream(Socket socket) throws IOException {
    this.socket = socket;
    this.inputStream = new DataInputStream(socket.getInputStream());
    this.outputStream = socket.getOutputStream();
  }

  @Override
  public byte readByte() throws IOException {
    return inputStream.readByte();
  }

  @Override
  public int readInt() throws IOException {
    return inputStream.readInt();
  }

  @Override
  public long readLong() throws IOException {
    return inputStream.readLong();
  }

  @Override
  public ByteBuffer readByteArray(int size) throws IOException {
    byte[] bytes = new byte[size];
    inputStream.readFully(bytes);
    return ByteBuffer.wrap(bytes);
  }

  @Override
  public void write(int mode, ByteBuffer byteBuffer) throws Exception {
    if (byteBuffer != null) {
      ByteBuffer header = ByteBuffer.allocate(8);
      header.putInt(mode);
      header.putInt(byteBuffer.remaining());
      header.flip();
      writeByteBuffer(header);
      writeByteBuffer(byteBuffer);
    } else {
      ByteBuffer header = ByteBuffer.allocate(4);
      header.putInt(mode);
      header.flip();
      writeByteBuffer(header);
    }
  }

  private void writeByteBuffer(ByteBuffer byteBuffer) throws IOException {
    byte[] bytes = new byte[byteBuffer.remaining()];
    byteBuffer.get(bytes);
    outputStream.write(bytes);
  }

  @Override
  public void close() {
    try {
      outputStream.close();
      inputStream.close();
      socket.close();
    } catch (IOException ignored) {
    }
  }
}
