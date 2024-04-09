package top.saymzx.easycontrol.app.client.tools;

import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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

  public void keepAlive() throws Exception {
    stream.write(KEEPALIVE_EVENT, null);
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

  public void videoInit(int id, boolean supportHevc, String startApp) throws Exception {
    byte[] tmpTextByte = startApp.getBytes(StandardCharsets.UTF_8);
    if (tmpTextByte.length == 0 || tmpTextByte.length > 5000) return;
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + 4 + tmpTextByte.length);
    byteBuffer.putInt(id);
    byteBuffer.putInt(VIDEO_CONFIG);
    byteBuffer.putInt(supportHevc ? 1 : 0);
    byteBuffer.put(tmpTextByte);
    byteBuffer.flip();
    stream.write(VIDEO_EVENT, byteBuffer);
  }

  public void videoConfig(int id, int maxSize, int maxVideoBit, int maxFps) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + 4 * 3);
    byteBuffer.putInt(id);
    byteBuffer.putInt(VIDEO_CONFIG);
    byteBuffer.putInt(maxSize);
    byteBuffer.putInt(maxVideoBit);
    byteBuffer.putInt(maxFps);
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

  public void audioInit(int id, boolean supportOpus, int audioSource, int sampleRate) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + 4 * 3);
    byteBuffer.putInt(id);
    byteBuffer.putInt(AUDIO_INIT);
    byteBuffer.putInt(supportOpus ? 1 : 0);
    byteBuffer.putInt(audioSource);
    byteBuffer.putInt(sampleRate);
    byteBuffer.flip();
    stream.write(AUDIO_EVENT, byteBuffer);
  }

  public void audioConfig(int id, int maxAudioBit) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + 4 * 2);
    byteBuffer.putInt(id);
    byteBuffer.putInt(AUDIO_CONFIG);
    byteBuffer.putInt(maxAudioBit);
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

  public void deviceConfig(boolean listenerClip, boolean keepScreenOn) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4 * 2);
    byteBuffer.putInt(DEVICE_CONFIG);
    byteBuffer.putInt(listenerClip ? 1 : 0);
    byteBuffer.putInt(keepScreenOn ? 1 : 0);
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

  public void deviceTouch(int displayId, int action, int pointerId, int x, int y, int offsetTime) throws Exception {
    if (x < 0 || x > 1 || y < 0 || y > 1) {
      // 超出范围则改为抬起事件
      if (x < 0) x = 0;
      if (x > 1) x = 1;
      if (y < 0) y = 0;
      if (y > 1) y = 1;
      action = MotionEvent.ACTION_UP;
    }
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + 4 * 5);
    byteBuffer.putInt(DEVICE_TOUCH);
    byteBuffer.putInt(displayId);
    byteBuffer.putInt(action);
    byteBuffer.putInt(pointerId);
    // 坐标位置
    byteBuffer.putInt(x);
    byteBuffer.putInt(y);
    // 时间偏移
    byteBuffer.putInt(offsetTime);
    byteBuffer.flip();
    stream.write(DEVICE_EVENT, byteBuffer);
  }

  public void deviceKey(int displayId, int key, int meta) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + 4 * 2);
    byteBuffer.putInt(DEVICE_KEY);
    byteBuffer.putInt(displayId);
    byteBuffer.putInt(key);
    byteBuffer.putInt(meta);
    byteBuffer.flip();
    stream.write(DEVICE_EVENT, byteBuffer);
  }

  public void deviceRotate(int displayId) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4);
    byteBuffer.putInt(DEVICE_ROTATE);
    byteBuffer.putInt(displayId);
    byteBuffer.flip();
    stream.write(DEVICE_EVENT, byteBuffer);
  }

  public void deviceLight(int displayId, int mode) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 2 + 4);
    byteBuffer.putInt(DEVICE_LIGHT);
    byteBuffer.putInt(displayId);
    byteBuffer.putInt(mode);
    byteBuffer.flip();
    stream.write(DEVICE_EVENT, byteBuffer);
  }

  public void deviceScreen() throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4);
    byteBuffer.putInt(DEVICE_SCREEN);
    byteBuffer.flip();
    stream.write(DEVICE_EVENT, byteBuffer);
  }

  public void deviceChangeSize(float newSize) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4 * 2);
    byteBuffer.putInt(DEVICE_CHANGE_SIZE);
    byteBuffer.putInt(1);
    byteBuffer.putFloat(newSize);
    byteBuffer.flip();
    stream.write(DEVICE_EVENT, byteBuffer);
  }

  public void deviceChangeSize(int width, int height) throws Exception {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4 * 3);
    byteBuffer.putInt(DEVICE_CHANGE_SIZE);
    byteBuffer.putInt(2);
    byteBuffer.putInt(width);
    byteBuffer.putInt(height);
    byteBuffer.flip();
    stream.write(DEVICE_EVENT, byteBuffer);
  }

}
