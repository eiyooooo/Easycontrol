/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 */
package top.saymzx.easycontrol.server.encode;

import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Pair;
import android.view.Display;
import android.view.Surface;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;

import top.saymzx.easycontrol.server.entity.DisplayInfo;
import top.saymzx.easycontrol.server.tools.ControlPacket;
import top.saymzx.easycontrol.server.wrappers.DeviceManager;
import top.saymzx.easycontrol.server.wrappers.DisplayManager;
import top.saymzx.easycontrol.server.wrappers.SurfaceControl;

public final class VideoEncode {
  private final int id;
  private static boolean isClosed = false;
  private final Handler mainHandler;
  private final ControlPacket controlPacket;
  private IBinder display;
  private VirtualDisplay virtualDisplay;
  private int displayId = Display.DEFAULT_DISPLAY;

  private MediaCodec encedec;
  private Surface surface;
  private final MediaFormat encodecFormat = new MediaFormat();
  private DisplayInfo displayInfo;
  private int maxSize;
  private int maxVideoBit;
  private int maxFps;
  private boolean supportHevc = EncodecTools.isSupportH265();

  private final ArrayList<Thread> workThreads = new ArrayList<>();


  public VideoEncode(int id, Handler mainHandler, ControlPacket controlPacket) {
    this.id = id;
    this.mainHandler = mainHandler;
    this.controlPacket = controlPacket;
    try {
      // 创建显示器
      display = SurfaceControl.createDisplay("easycontrol", Build.VERSION.SDK_INT < Build.VERSION_CODES.R || (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !"S".equals(Build.VERSION.CODENAME)));
      // 参数初始化
      encodecFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    } catch (Exception ignored) {
      release(null);
    }
  }

  public void handle(ByteBuffer byteBuffer) {
    if (isClosed) return;
    try {
      int mode = byteBuffer.getInt();
      if (mode == ControlPacket.AUDIO_ERROR) handleVideoError(byteBuffer);
      else if (mode == ControlPacket.VIDEO_INIT) handleVideoInit(byteBuffer);
      else if (mode == ControlPacket.VIDEO_CONFIG) handleVideoConfig(byteBuffer);
    } catch (Exception e) {
      release(e.toString());
    }
  }

  public void handleVideoError(ByteBuffer byteBuffer) throws IOException {
    release(byteBuffer.toString());
  }

  public void handleVideoInit(ByteBuffer byteBuffer) throws Exception {
    // 已经初始化过则忽略
    if (encedec != null) return;
    // 读取初始化参数
    supportHevc = byteBuffer.getInt() == 1 && supportHevc;
    String startApp = byteBuffer.toString();
    // 是否单应用投屏
    if (!Objects.equals(startApp, "")) {
      virtualDisplay = DisplayManager.createVirtualDisplay();
      displayId = virtualDisplay.getDisplay().getDisplayId();
      DeviceManager.startAndMoveAppToVirtualDisplay(startApp, displayId);
    }
    // 完成初始化
    String codecMime = supportHevc ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
    encedec = MediaCodec.createEncoderByType(codecMime);
    encodecFormat.setString(MediaFormat.KEY_MIME, codecMime);
    surface = encedec.createInputSurface();
    displayInfo = DisplayManager.getDisplayInfo(displayId);
    setDisplaySurface(display, surface);
    mainHandler.postDelayed(this::checkDisplayInfo, 1000);
  }

  private void checkDisplayInfo() {
    if (isClosed) return;
    DisplayInfo newDisplayInfo = DisplayManager.getDisplayInfo(displayId);
    if (displayInfo.width != newDisplayInfo.width || displayInfo.height != newDisplayInfo.height) {
      displayInfo = newDisplayInfo;
      ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 4);
      byteBuffer.putInt(ControlPacket.VIDEO_CONFIG);
      byteBuffer.putInt(maxSize);
      byteBuffer.putInt(maxVideoBit);
      byteBuffer.putInt(maxFps);
      byteBuffer.flip();
      handle(byteBuffer);
    }
    mainHandler.postDelayed(this::checkDisplayInfo, 1000);
  }

  private void handleVideoConfig(ByteBuffer byteBuffer) throws Exception {
    if (encedec == null) return;
    // 停止编码器
    stopEncode();
    // 读取新参数
    maxSize = byteBuffer.getInt();
    maxVideoBit = byteBuffer.getInt();
    maxFps = byteBuffer.getInt();
    // 更新参数
    encodecFormat.setInteger(MediaFormat.KEY_BIT_RATE, maxVideoBit);
    encodecFormat.setInteger(MediaFormat.KEY_FRAME_RATE, maxFps);
    encodecFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
    encodecFormat.setFloat("max-fps-to-encoder", maxFps);
    Pair<Integer, Integer> videoSize = calculateVideoSize(maxSize);
    encodecFormat.setInteger(MediaFormat.KEY_WIDTH, videoSize.first);
    encodecFormat.setInteger(MediaFormat.KEY_HEIGHT, videoSize.second);
    SurfaceControl.setDisplaySize(display, videoSize.first, videoSize.second);
    encedec.configure(encodecFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    // 发送新的视频参数
    controlPacket.videoInfo(id, supportHevc, displayInfo.width, displayInfo.height, videoSize.first, videoSize.second);
    // 启动编码
    encedec.start();
    workThreads.clear();
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

  private Pair<Integer, Integer> calculateVideoSize(int maxSize) {
    DisplayInfo displayInfo = DisplayManager.getDisplayInfo(displayId);
    boolean isPortrait = displayInfo.width < displayInfo.height;
    int major = isPortrait ? displayInfo.height : displayInfo.width;
    int minor = isPortrait ? displayInfo.width : displayInfo.height;
    if (major > maxSize) {
      minor = minor * maxSize / major;
      major = maxSize;
    }
    // 某些厂商实现的解码器只接受16的倍数，所以需要缩放至最近数值
    minor = minor + 8 & ~15;
    major = major + 8 & ~15;
    return isPortrait ? new Pair<>(minor, major) : new Pair<>(major, minor);
  }

  private void setDisplaySurface(IBinder display, Surface surface) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    SurfaceControl.openTransaction();
    try {
      SurfaceControl.setDisplaySurface(display, surface);
      SurfaceControl.setDisplayLayerStack(display, DisplayManager.getDisplayInfo(displayId).layerStack);
    } finally {
      SurfaceControl.closeTransaction();
    }
  }

  private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

  private void executeOut() {
    while (!Thread.interrupted()) {
      try {
        int outIndex;
        do outIndex = encedec.dequeueOutputBuffer(bufferInfo, -1); while (outIndex < 0);
        ByteBuffer buffer = encedec.getOutputBuffer(outIndex);
        if (buffer == null) continue;
        controlPacket.videoFrame(id, bufferInfo.presentationTimeUs, buffer);
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
      SurfaceControl.destroyDisplay(display);
      surface.release();
      encedec.release();
      controlPacket.videoError(id, error);
    } catch (Exception ignored) {
    }
  }

}
