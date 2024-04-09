/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 */
package top.saymzx.easycontrol.server.tools;

import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Pair;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.saymzx.easycontrol.server.Server;
import top.saymzx.easycontrol.server.encode.VideoEncode;
import top.saymzx.easycontrol.server.entity.Options;
import top.saymzx.easycontrol.server.entity.Pointer;
import top.saymzx.easycontrol.server.entity.PointersState;
import top.saymzx.easycontrol.server.wrappers.ClipboardManager;
import top.saymzx.easycontrol.server.wrappers.InputManager;
import top.saymzx.easycontrol.server.wrappers.SurfaceControl;
import top.saymzx.easycontrol.server.wrappers.WindowManager;

public final class DeviceTool {
  private static boolean isClosed = false;
  private final Handler mainHandler;
  private final ControlPacket controlPacket;

  // 参数
  private boolean listenerClip = false;

  public Pair<Integer, Integer> realSize;

  private boolean needReset = false;


  public DeviceTool(Handler mainHandler, ControlPacket controlPacket) {
    this.mainHandler = mainHandler;
    this.controlPacket = controlPacket;
  }

  public void handle(ByteBuffer byteBuffer) {
    if (isClosed) return;
    try {
      int mode = byteBuffer.getInt();
      if (mode == ControlPacket.DEVICE_CONFIG) handleDeviceConfig(byteBuffer);
      else if (mode == ControlPacket.DEVICE_CLIP) handleDeviceClip(byteBuffer);
      else if (mode == ControlPacket.DEVICE_TOUCH) handleDeviceTouch(byteBuffer);
      else if (mode == ControlPacket.DEVICE_KEY) handleDeviceKey(byteBuffer);
      else if (mode == ControlPacket.DEVICE_ROTATE) handleDeviceRotate(byteBuffer);
      else if (mode == ControlPacket.DEVICE_LIGHT) handleDeviceLight(byteBuffer);
      else if (mode == ControlPacket.DEVICE_CHANGE_SIZE) handleDeviceChangeSize(byteBuffer);
    } catch (Exception e) {
      release(e.toString());
    }
  }

  private void handleDeviceConfig(ByteBuffer byteBuffer) {
    boolean listenerClip = byteBuffer.getInt() == 1;
    boolean keepScreenLight = byteBuffer.getInt() == 1;
    if (!this.listenerClip && listenerClip) mainHandler.post(this::checkClip);
    this.listenerClip = listenerClip;
    setKeepScreenLight(keepScreenLight);
  }

  private String nowClipboardText = "";

  private void checkClip() {
    if (isClosed || !listenerClip) return;
    String newClipboardText = ClipboardManager.getText();
    if (newClipboardText == null) return;
    try {
      if (!newClipboardText.equals(nowClipboardText)) {
        nowClipboardText = newClipboardText;
        controlPacket.deviceClip(newClipboardText);
      }
    } catch (Exception e) {
      release(e.toString());
    }
    mainHandler.postDelayed(this::checkClip, 1000);
  }

  private int oldScreenOffTimeout = 60000;

  private void setKeepScreenLight(boolean keepScreenLight) {
    try {
      if (keepScreenLight) {
        String output = execReadOutput("settings get system screen_off_timeout");
        // 使用正则表达式匹配数字
        Matcher matcher = Pattern.compile("\\d+").matcher(output);
        if (matcher.find()) {
          int timeout = Integer.parseInt(matcher.group());
          if (timeout >= 20 && timeout <= 60 * 30) oldScreenOffTimeout = timeout;
        }
        execReadOutput("settings put system screen_off_timeout 600000000");
      } else {
        execReadOutput("settings put system screen_off_timeout " + oldScreenOffTimeout);
      }
    } catch (Exception ignored) {
    }
  }

  private void handleDeviceClip(ByteBuffer byteBuffer) {
    nowClipboardText = byteBuffer.toString();
    ClipboardManager.setText(nowClipboardText);
  }

  private void handleDeviceTouch(ByteBuffer byteBuffer) {
    // todo
  }

  private void handleDeviceKey(ByteBuffer byteBuffer) {
    // todo
  }

  private void handleDeviceRotate(ByteBuffer byteBuffer) {
    // todo
  }

  private void handleDeviceLight(ByteBuffer byteBuffer) {
    // todo
  }

  private void handleDeviceChangeSize(ByteBuffer byteBuffer) {
    // todo
  }


  public boolean isClosed() {
    return isClosed;
  }

  public void release(String error) {
    if (isClosed()) return;
    isClosed = true;
    try {
      controlPacket.deviceError(error);
    } catch (Exception ignored) {
    }
  }

  // 以下是旧代码，移植完成后删除
  public void init() throws Exception {
    // 若启动单个应用则需创建虚拟Dispaly
    getRealSize();
  }

  // 打开并移动应用
  private void startAndMoveAppToVirtualDisplay(int displayId) throws IOException, InterruptedException {
    int appStackId = getAppStackId();
    if (appStackId == -1) {
      DeviceTool.execReadOutput("monkey -p " + Options.startApp + " -c android.intent.category.LAUNCHER 1");
      appStackId = getAppStackId();
    }
    if (appStackId == -1) throw new IOException("error app");
    DeviceTool.execReadOutput("am display move-stack " + appStackId + " " + displayId);
  }

  private int getAppStackId() throws IOException, InterruptedException {
    String amStackList = DeviceTool.execReadOutput("am stack list");
    Matcher m = Pattern.compile("taskId=([0-9]+): " + Options.startApp).matcher(amStackList);
    if (!m.find()) return -1;
    return Integer.parseInt(Objects.requireNonNull(m.group(1)));
  }

  private void getRealSize() throws IOException, InterruptedException {
    String output = DeviceTool.execReadOutput("wm size");
    String patStr;
    // 查看当前分辨率
    patStr = (output.contains("Override") ? "Override" : "Physical") + " size: (\\d+)x(\\d+)";
    Matcher matcher = Pattern.compile(patStr).matcher(output);
    if (matcher.find()) {
      String width = matcher.group(1);
      String height = matcher.group(2);
      if (width == null || height == null) return;
      realSize = new Pair<>(Integer.parseInt(width), Integer.parseInt(height));
    }
  }

  // 修改分辨率
  public void changeResolution(float targetRatio) {
    try {
      // 安全阈值(长宽比最多三倍)
      if (targetRatio > 3 || targetRatio < 0.34) return;
      // 没有获取到真实分辨率
      if (realSize == null) return;

      float originalRatio = (float) realSize.first / realSize.second;
      // 计算变化比率
      float ratioChange = targetRatio / originalRatio;
      // 根据比率变化确定新的长和宽
      int newWidth, newHeight;
      if (ratioChange > 1) {
        newWidth = realSize.first;
        newHeight = (int) (realSize.second / ratioChange);
      } else {
        newWidth = (int) (realSize.first * ratioChange);
        newHeight = realSize.second;
      }
      changeResolution(newWidth, newHeight);
    } catch (Exception ignored) {
    }
  }

  // 修改分辨率
  public void changeResolution(int width, int height) {
    try {
      float originalRatio = (float) realSize.first / realSize.second;
      // 安全阈值(长宽比最多三倍)
      if (originalRatio > 3 || originalRatio < 0.34) return;

      needReset = true;

      // 缩放至16倍数
      width = width + 8 & ~15;
      height = height + 8 & ~15;
      // 避免分辨率相同，会触发安全机制导致系统崩溃
      if (width == height) width -= 16;

      // 修改分辨率
      if (virtualDisplay != null) virtualDisplay.resize(width, height, displayInfo.density);
      else DeviceTool.execReadOutput("wm size " + width + "x" + height);

      // 更新，需延迟一段时间
      Thread.sleep(200);
      updateSize();
      VideoEncode.isHasChangeConfig = true;
    } catch (Exception ignored) {
    }
  }

  // 恢复分辨率
  public void fallbackResolution() throws IOException, InterruptedException {
    if (DeviceTool.needReset) {
      if (virtualDisplay != null) {
        int appStackId = getAppStackId();
        if (appStackId == -1) DeviceTool.execReadOutput("am display move-stack " + appStackId + " " + Display.DEFAULT_DISPLAY);
        virtualDisplay.release();
      } else {
        if (DeviceTool.realSize != null) DeviceTool.execReadOutput("wm size " + DeviceTool.realSize.first + "x" + DeviceTool.realSize.second);
        else DeviceTool.execReadOutput("wm size reset");
      }
    }
  }


  public void deviceTouch() throws IOException {
    int action = Server.mainInputStream.readByte();
    int pointerId = Server.mainInputStream.readByte();
    float x = Server.mainInputStream.readFloat();
    float y = Server.mainInputStream.readFloat();
    int offsetTime = Server.mainInputStream.readInt();
    DeviceTool.touchEvent(action, x, y, pointerId, offsetTime);
  }

  public void handleKeyEvent() throws IOException {
    int keyCode = Server.mainInputStream.readInt();
    int meta = Server.mainInputStream.readInt();
    DeviceTool.keyEvent(keyCode, meta);
  }


  private final PointersState pointersState = new PointersState();

  public void touchEvent(int action, Float x, Float y, int pointerId, int offsetTime) {
    Pointer pointer = pointersState.get(pointerId);

    if (pointer == null) {
      if (action != MotionEvent.ACTION_DOWN) return;
      pointer = pointersState.newPointer(pointerId, SystemClock.uptimeMillis() - 50);
    }

    pointer.x = x * displayInfo.width;
    pointer.y = y * displayInfo.height;
    int pointerCount = pointersState.update();

    if (action == MotionEvent.ACTION_UP) {
      pointersState.remove(pointerId);
      if (pointerCount > 1) action = MotionEvent.ACTION_POINTER_UP | (pointer.id << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    } else if (action == MotionEvent.ACTION_DOWN) {
      if (pointerCount > 1) action = MotionEvent.ACTION_POINTER_DOWN | (pointer.id << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    }
    MotionEvent event = MotionEvent.obtain(pointer.downTime, pointer.downTime + offsetTime, action, pointerCount, pointersState.pointerProperties, pointersState.pointerCoords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    injectEvent(event);
  }

  public void keyEvent(int keyCode, int meta) {
    long now = SystemClock.uptimeMillis();
    KeyEvent event1 = new KeyEvent(now, now, MotionEvent.ACTION_DOWN, keyCode, 0, meta, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
    KeyEvent event2 = new KeyEvent(now, now, MotionEvent.ACTION_UP, keyCode, 0, meta, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
    injectEvent(event1);
    injectEvent(event2);
  }

  private void injectEvent(InputEvent inputEvent) {
    try {
      if (displayId != Display.DEFAULT_DISPLAY) InputManager.setDisplayId(inputEvent, displayId);
      InputManager.injectInputEvent(inputEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    } catch (Exception e) {
      System.out.println(e.toString());
    }
  }

  public void changeScreenPowerMode(int mode) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      long[] physicalDisplayIds = SurfaceControl.getPhysicalDisplayIds();
      if (physicalDisplayIds == null) return;
      for (long physicalDisplayId : physicalDisplayIds) {
        IBinder token = SurfaceControl.getPhysicalDisplayToken(physicalDisplayId);
        if (token != null) SurfaceControl.setDisplayPowerMode(token, mode);
      }
    } else {
      IBinder d = SurfaceControl.getBuiltInDisplay();
      if (d != null) SurfaceControl.setDisplayPowerMode(d, mode);
    }
  }

  public void changePower(int mode) {
    if (mode == -1) keyEvent(26, 0);
    else {
      try {
        String output = execReadOutput("dumpsys deviceidle | grep mScreenOn");
        Boolean isScreenOn = null;
        if (output.contains("mScreenOn=true")) isScreenOn = true;
        else if (output.contains("mScreenOn=false")) isScreenOn = false;
        if (isScreenOn != null && isScreenOn ^ (mode == 1)) DeviceTool.keyEvent(26, 0);
      } catch (Exception ignored) {
      }
    }
  }

  public void rotateDevice() {
    boolean accelerometerRotation = !WindowManager.isRotationFrozen(displayId);
    WindowManager.freezeRotation((displayInfo.rotation == 0 || displayInfo.rotation == 3) ? 1 : 0, displayId);
    if (accelerometerRotation) WindowManager.thawRotation(displayId);
  }

  public String execReadOutput(String cmd) throws IOException, InterruptedException {
    Process process = new ProcessBuilder().command("sh", "-c", cmd).start();
    StringBuilder builder = new StringBuilder();
    String line;
    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      while ((line = bufferedReader.readLine()) != null) builder.append(line).append("\n");
    }
    int exitCode = process.waitFor();
    if (exitCode != 0) throw new IOException("命令执行错误" + cmd);
    return builder.toString();
  }


  // 恢复自动锁定时间
  public void fallbackScreenLightTimeout() throws IOException, InterruptedException {
    if (Options.keepAwake) DeviceTool.execReadOutput("settings put system screen_off_timeout " + DeviceTool.oldScreenOffTimeout);
  }

}
