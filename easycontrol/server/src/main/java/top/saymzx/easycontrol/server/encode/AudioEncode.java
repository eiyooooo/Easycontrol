/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 */
package top.saymzx.easycontrol.server.encode;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import top.saymzx.easycontrol.server.tools.ControlPacket;

public final class AudioEncode {
  private final int id;
  private boolean isClosed = false;
  private final Handler mainHandler;
  private final ControlPacket controlPacket;
  private MediaCodec encedec;
  private final MediaFormat encodecFormat = new MediaFormat();
  private AudioCapture audioCapture;
  private boolean supportOpus = EncodecTools.isSupportOpus();
  private final ArrayList<Thread> workThreads = new ArrayList<>();

  public AudioEncode(int id, Handler mainHandler, ControlPacket controlPacket) {
    this.id = id;
    this.mainHandler = mainHandler;
    this.controlPacket = controlPacket;
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) release("Need android 12+");
  }

  public void handle(ByteBuffer byteBuffer) {
    if (isClosed) return;
    try {
      int mode = byteBuffer.getInt();
      if (mode == ControlPacket.AUDIO_ERROR) handleAudioError(byteBuffer);
      else if (mode == ControlPacket.AUDIO_INIT) handleAudioInit(byteBuffer);
      else if (mode == ControlPacket.AUDIO_CONFIG) handleAudioConfig(byteBuffer);
    } catch (Exception e) {
      release(e.toString());
    }
  }

  public void handleAudioError(ByteBuffer byteBuffer) throws IOException {
    release(byteBuffer.toString());
  }

  public void handleAudioInit(ByteBuffer byteBuffer) throws IOException {
    // 已经初始化过则忽略
    if (encedec != null) return;
    // 读取初始化参数
    supportOpus = byteBuffer.getInt() == 1 && supportOpus;
    int audioSource = byteBuffer.getInt();
    int sampleRate = byteBuffer.getInt();
    // 完成初始化
    String codecMime = supportOpus ? MediaFormat.MIMETYPE_AUDIO_OPUS : MediaFormat.MIMETYPE_AUDIO_AAC;
    encedec = MediaCodec.createEncoderByType(codecMime);
    encodecFormat.setString(MediaFormat.KEY_MIME, codecMime);
    audioCapture = new AudioCapture(audioSource, sampleRate);
    audioCapture.startRecord();
  }

  private void handleAudioConfig(ByteBuffer byteBuffer) throws Exception {
    if (encedec == null) return;
    // 停止编码器
    stopEncode();
    // 读取新参数
    int maxAudioBit = byteBuffer.getInt();
    // 更新参数
    encodecFormat.setInteger(MediaFormat.KEY_BIT_RATE, maxAudioBit);
    encodecFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, audioCapture.SAMPLE_RATE);
    encodecFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, audioCapture.CHANNELS);
    encodecFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioCapture.AUDIO_PACKET_SIZE);
    encedec.configure(encodecFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    // 发送新的视频参数
    controlPacket.audioInfo(id, supportOpus);
    // 启动编码
    encedec.start();
    workThreads.clear();
    workThreads.add(new Thread(this::executeIn));
    workThreads.add(new Thread(this::executeOut));
    for (Thread thread : workThreads) thread.start();
  }

  private void stopEncode() {
    try {
      if (encedec == null) return;
      for (Thread thread : workThreads) thread.interrupt();
      encedec.stop();
      encedec.reset();
    } catch (IllegalStateException ignored) {
    }
  }

  private void executeIn() {
    while (!Thread.interrupted()) {
      try {
        int inIndex;
        do inIndex = encedec.dequeueInputBuffer(-1); while (inIndex < 0);
        ByteBuffer buffer = encedec.getInputBuffer(inIndex);
        if (buffer == null) return;
        int size = audioCapture.read(buffer);
        encedec.queueInputBuffer(inIndex, 0, size, 0, 0);
      } catch (IllegalStateException ignored) {
      } catch (Exception e) {
        release(e.toString());
      }
    }
  }

  private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

  private void executeOut() {
    while (!Thread.interrupted()) {
      try {
        int outIndex;
        do outIndex = encedec.dequeueOutputBuffer(bufferInfo, -1); while (outIndex < 0);
        ByteBuffer buffer = encedec.getOutputBuffer(outIndex);
        if (buffer == null) return;
        if (supportOpus) {
          if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            buffer.getLong();
            int size = (int) buffer.getLong();
            buffer.limit(buffer.position() + size);
          }
          // 当无声音时不发送
          if (buffer.remaining() < 5) {
            encedec.releaseOutputBuffer(outIndex, false);
            return;
          }
        }
        controlPacket.audioFrame(id, buffer);
        encedec.releaseOutputBuffer(outIndex, false);
      } catch (IllegalStateException ignored) {
      } catch (Exception e) {
        release(e.toString());
      }
    }
  }

  public boolean isClosed() {
    return isClosed;
  }

  public void release(String error) {
    if (isClosed()) return;
    isClosed = true;
    try {
      stopEncode();
      audioCapture.release();
      encedec.release();
      controlPacket.audioError(id, error);
    } catch (Exception ignored) {
    }
  }

}

