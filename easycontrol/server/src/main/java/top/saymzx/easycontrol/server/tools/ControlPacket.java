/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 */
package top.saymzx.easycontrol.server.tools;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ControlPacket {
  private final Stream stream;

  // mode
  public static final int KEEPALIVE_EVENT = 0;
  public static final int VIDEO_EVENT = 1;
  public static final int AUDIO_EVENT = 2;
  public static final int FILE_EVENT = 3;
  public static final int DEVICE_EVENT = 4;

  // VIDEO_EVENT
  public static final int VIDEO_ERROR = 100;
  public static final int VIDEO_INIT = 101;
  public static final int VIDEO_CONFIG = 102;
  public static final int VIDEO_INFO = 103;
  public static final int VIDEO_FRAME = 104;

  // AUDIO_EVENT
  public static final int AUDIO_ERROR = 200;
  public static final int AUDIO_INIT = 201;
  public static final int AUDIO_CONFIG = 202;
  public static final int AUDIO_INFO = 203;
  public static final int AUDIO_FRAME = 204;

  // FILE_EVENT
  public static final int FILE_ERROR = 300;
  public static final int FILE_INIT = 301;
  public static final int FILE_GET_LIST = 302;
  public static final int FILE_LIST = 303;
  public static final int FILE_GET = 304; // 用于主动请求文件
  public static final int FILE_SEND = 305; // 用于文件发送，可以主动发送，也可以被请求后发送

  // DEVICE_EVENT
  public static final int DEVICE_ERROR = 400;
  public static final int DEVICE_INIT = 401;
  public static final int DEVICE_CONFIG = 402;
  public static final int DEVICE_CLIP = 403;
  public static final int DEVICE_TOUCH = 404;
  public static final int DEVICE_KEY = 405;
  public static final int DEVICE_ROTATE = 406;
  public static final int DEVICE_LIGHT = 407;
  public static final int DEVICE_SCREEN = 408;
  public static final int DEVICE_CHANGE_SIZE = 409;

  public ControlPacket(Stream stream) {
    this.stream = stream;
  }

  public void videoError(int id, String error) throws Exception {
    byte[] tmpTextByte = null;
    if (error != null) {
      tmpTextByte = error.getBytes(StandardCharsets.UTF_8);
      if (tmpTextByte.length == 0 || tmpTextByte.length > 5000) {
        tmpTextByte = null;
      }
    }
    int tmpTextByteSize = tmpTextByte == null ? 0 : tmpTextByte.length;
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + tmpTextByteSize);
    byteBuffer.putInt(id);
    byteBuffer.putInt(VIDEO_ERROR);
    if (tmpTextByte != null) byteBuffer.put(tmpTextByte);
    byteBuffer.flip();
    stream.write(VIDEO_EVENT, byteBuffer);
  }

  public void videoInfo(int id, boolean supportHevc, int screenWidth, int screenHeight, int videoWidth, int videoHeight) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + 4 * 5);
    byteBuffer.putInt(id);
    byteBuffer.putInt(VIDEO_INFO);
    byteBuffer.putInt(supportHevc ? 1 : 0);
    byteBuffer.putInt(screenWidth);
    byteBuffer.putInt(screenHeight);
    byteBuffer.putInt(videoWidth);
    byteBuffer.putInt(videoHeight);
    byteBuffer.flip();
    stream.write(VIDEO_EVENT, byteBuffer);
  }

  public void videoFrame(int id, long pts, ByteBuffer data) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + 8 + data.remaining());
    byteBuffer.putInt(id);
    byteBuffer.putInt(VIDEO_FRAME);
    byteBuffer.putLong(pts);
    byteBuffer.put(data);
    byteBuffer.flip();
    stream.write(VIDEO_EVENT, byteBuffer);
  }

  public void audioError(int id, String error) throws Exception {
    byte[] tmpTextByte = null;
    if (error != null) {
      tmpTextByte = error.getBytes(StandardCharsets.UTF_8);
      if (tmpTextByte.length == 0 || tmpTextByte.length > 5000) {
        tmpTextByte = null;
      }
    }
    int tmpTextByteSize = tmpTextByte == null ? 0 : tmpTextByte.length;
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + tmpTextByteSize);
    byteBuffer.putInt(id);
    byteBuffer.putInt(AUDIO_ERROR);
    if (tmpTextByte != null) byteBuffer.put(tmpTextByte);
    byteBuffer.flip();
    stream.write(AUDIO_EVENT, byteBuffer);
  }

  public void audioInfo(int id, boolean isOpus) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + 4);
    byteBuffer.putInt(id);
    byteBuffer.putInt(AUDIO_INFO);
    byteBuffer.putInt(isOpus ? 1 : 0);
    byteBuffer.flip();
    stream.write(AUDIO_EVENT, byteBuffer);
  }

  public void audioFrame(int id, ByteBuffer data) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + data.remaining());
    byteBuffer.putInt(id);
    byteBuffer.putInt(AUDIO_FRAME);
    byteBuffer.put(data);
    byteBuffer.flip();
    stream.write(AUDIO_EVENT, byteBuffer);
  }

  public void fileError(String error) throws Exception {
    byte[] tmpTextByte = null;
    if (error != null) {
      tmpTextByte = error.getBytes(StandardCharsets.UTF_8);
      if (tmpTextByte.length == 0 || tmpTextByte.length > 5000) {
        tmpTextByte = null;
      }
    }
    int tmpTextByteSize = tmpTextByte == null ? 0 : tmpTextByte.length;
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + tmpTextByteSize);
    byteBuffer.putInt(FILE_ERROR);
    if (tmpTextByte != null) byteBuffer.put(tmpTextByte);
    byteBuffer.flip();
    stream.write(FILE_EVENT, byteBuffer);
  }

  public void fileList(byte[] path, List<byte[]> fileNames) throws Exception {
    int size = 0;
    for (byte[] bytes : fileNames) size += bytes.length;
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4 + path.length + fileNames.size() * 4 + size);
    byteBuffer.putInt(FILE_LIST);
    byteBuffer.putInt(path.length);
    byteBuffer.put(path);
    for (byte[] bytes : fileNames) {
      byteBuffer.putInt(bytes.length);
      byteBuffer.put(bytes);
    }
    byteBuffer.flip();
    stream.write(FILE_EVENT, byteBuffer);
  }

  public void deviceError(String error) throws Exception {
    byte[] tmpTextByte = null;
    if (error != null) {
      tmpTextByte = error.getBytes(StandardCharsets.UTF_8);
      if (tmpTextByte.length == 0 || tmpTextByte.length > 5000) {
        tmpTextByte = null;
      }
    }
    int tmpTextByteSize = tmpTextByte == null ? 0 : tmpTextByte.length;
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + tmpTextByteSize);
    byteBuffer.putInt(DEVICE_ERROR);
    if (tmpTextByte != null) byteBuffer.put(tmpTextByte);
    byteBuffer.flip();
    stream.write(DEVICE_EVENT, byteBuffer);
  }

  public void deviceClip(String newClipboardText) throws Exception {
    byte[] tmpTextByte = newClipboardText.getBytes(StandardCharsets.UTF_8);
    if (tmpTextByte.length == 0 || tmpTextByte.length > 5000) return;
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + tmpTextByte.length);
    byteBuffer.putInt(DEVICE_CLIP);
    byteBuffer.put(tmpTextByte);
    byteBuffer.flip();
    stream.write(DEVICE_EVENT, byteBuffer);
  }

}

