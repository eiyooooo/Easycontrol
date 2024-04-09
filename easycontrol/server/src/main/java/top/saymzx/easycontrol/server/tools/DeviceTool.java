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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.saymzx.easycontrol.server.entity.Pointer;
import top.saymzx.easycontrol.server.entity.PointersState;
import top.saymzx.easycontrol.server.wrappers.ClipboardManager;
import top.saymzx.easycontrol.server.wrappers.DeviceManager;
import top.saymzx.easycontrol.server.wrappers.InputManager;
import top.saymzx.easycontrol.server.wrappers.SurfaceControl;

public final class DeviceTool {
  private static boolean isClosed = false;
  private final Handler mainHandler;
  private final ControlPacket controlPacket;

  // 参数
  private boolean listenerClip = false;

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
      else if (mode == ControlPacket.DEVICE_SCREEN) handleDeviceScreen(byteBuffer);
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

  private Integer oldScreenOffTimeout;

  private void setKeepScreenLight(boolean keepScreenLight) {
    try {
      if (keepScreenLight) {
        String output = DeviceManager.execReadOutput("settings get system screen_off_timeout");
        // 使用正则表达式匹配数字
        Matcher matcher = Pattern.compile("\\d+").matcher(output);
        if (matcher.find()) {
          int timeout = Integer.parseInt(matcher.group());
          if (timeout >= 20 && timeout <= 60 * 30) oldScreenOffTimeout = timeout;
        }
        DeviceManager.execReadOutput("settings put system screen_off_timeout 600000000");
      } else DeviceManager.execReadOutput("settings put system screen_off_timeout " + (oldScreenOffTimeout == null ? 60000 : oldScreenOffTimeout));
    } catch (Exception ignored) {
    }
  }

  private void handleDeviceClip(ByteBuffer byteBuffer) {
    nowClipboardText = byteBuffer.toString();
    ClipboardManager.setText(nowClipboardText);
  }

  private final PointersState pointersState = new PointersState();

  private void handleDeviceTouch(ByteBuffer byteBuffer) {
    // 解析数据
    int displayId = byteBuffer.getInt();
    int action = byteBuffer.getInt();
    int pointerId = byteBuffer.getInt();
    int x = byteBuffer.getInt();
    int y = byteBuffer.getInt();
    int offsetTime = byteBuffer.getInt();
    // 模拟点击
    Pointer pointer = pointersState.get(pointerId);

    if (pointer == null) {
      if (action != MotionEvent.ACTION_DOWN) return;
      pointer = pointersState.newPointer(pointerId, SystemClock.uptimeMillis() - 50);
    }

    pointer.x = x;
    pointer.y = y;
    int pointerCount = pointersState.update();

    if (action == MotionEvent.ACTION_UP) {
      pointersState.remove(pointerId);
      if (pointerCount > 1) action = MotionEvent.ACTION_POINTER_UP | (pointer.id << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    } else if (action == MotionEvent.ACTION_DOWN) {
      if (pointerCount > 1) action = MotionEvent.ACTION_POINTER_DOWN | (pointer.id << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    }
    MotionEvent event = MotionEvent.obtain(pointer.downTime, pointer.downTime + offsetTime, action, pointerCount, pointersState.pointerProperties, pointersState.pointerCoords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    injectEvent(displayId, event);
  }

  private void handleDeviceKey(ByteBuffer byteBuffer) {
    int displayId = byteBuffer.getInt();
    int keyCode = byteBuffer.getInt();
    int meta = byteBuffer.getInt();
    long now = SystemClock.uptimeMillis();
    KeyEvent event1 = new KeyEvent(now, now, MotionEvent.ACTION_DOWN, keyCode, 0, meta, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
    KeyEvent event2 = new KeyEvent(now, now, MotionEvent.ACTION_UP, keyCode, 0, meta, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
    injectEvent(displayId, event1);
    injectEvent(displayId, event2);
  }

  private void injectEvent(int displayId, InputEvent inputEvent) {
    try {
      if (displayId != Display.DEFAULT_DISPLAY) InputManager.setDisplayId(inputEvent, displayId);
      InputManager.injectInputEvent(inputEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    } catch (Exception e) {
      System.out.println(e);
    }
  }

  private void handleDeviceRotate(ByteBuffer byteBuffer) {
    // 因旋转操作普适性较差，暂停止使用
//    boolean accelerometerRotation = !WindowManager.isRotationFrozen(displayId);
//    WindowManager.freezeRotation((displayInfo.rotation == 0 || displayInfo.rotation == 3) ? 1 : 0, displayId);
//    if (accelerometerRotation) WindowManager.thawRotation(displayId);
  }

  private void handleDeviceLight(ByteBuffer byteBuffer) {
    int mode = byteBuffer.getInt();
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

  public void handleDeviceScreen(ByteBuffer byteBuffer) {
    int mode = byteBuffer.getInt();
    try {
      String output = DeviceManager.execReadOutput("dumpsys deviceidle | grep mScreenOn");
      Boolean isScreenOn = null;
      if (output.contains("mScreenOn=true")) isScreenOn = true;
      else if (output.contains("mScreenOn=false")) isScreenOn = false;
      if (isScreenOn != null && isScreenOn ^ (mode == 1)) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(26);
        buffer.putInt(0);
        buffer.flip();
        handleDeviceKey(buffer);
      }
    } catch (Exception ignored) {
    }
  }

  private void handleDeviceChangeSize(ByteBuffer byteBuffer) {
    if (realSize == null) {
      getRealSize();
      if (realSize == null) return;
    }
    // 读取参数
    int mode = byteBuffer.getInt();
    int width = realSize.first;
    int height = realSize.second;
    if (mode == 1) {
      float newSize = byteBuffer.getFloat();
      // 安全阈值(长宽比最多三倍)
      if (newSize > 3 || newSize < 0.34) return;
      float originalRatio = (float) realSize.first / realSize.second;
      // 计算变化比率
      float ratioChange = newSize / originalRatio;
      // 根据比率变化确定新的长和宽
      if (ratioChange > 1) {
        width = realSize.first;
        height = (int) (realSize.second / ratioChange);
      } else {
        width = (int) (realSize.first * ratioChange);
        height = realSize.second;
      }
    } else if (mode == 2) {
      width = byteBuffer.getInt();
      height = byteBuffer.getInt();
    }
    try {
      // 缩放至16倍数
      width = width + 8 & ~15;
      height = height + 8 & ~15;
      // 避免分辨率相同，会触发安全机制导致系统崩溃
      if (width == height) width -= 16;
      // 修改分辨率
//      if (virtualDisplay != null) virtualDisplay.resize(width, height, displayInfo.density);
//      else DeviceManager.execReadOutput("wm size " + width + "x" + height);
    } catch (Exception ignored) {
    }
  }

  private Pair<Integer, Integer> realSize;

  private void getRealSize() {
    try {
      String output = DeviceManager.execReadOutput("wm size");
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
    } catch (Exception ignored) {
    }
  }

  // 恢复分辨率
  public void fallbackResolution() throws IOException, InterruptedException {
//    if (virtualDisplay != null) {
//      int appStackId = getAppStackId();
//      if (appStackId == -1) DeviceManager.execReadOutput("am display move-stack " + appStackId + " " + Display.DEFAULT_DISPLAY);
//      virtualDisplay.release();
//    } else {
//      if (realSize != null) DeviceManager.execReadOutput("wm size " + realSize.first + "x" + realSize.second);
//      else DeviceManager.execReadOutput("wm size reset");
//    }
  }

  public boolean isClosed() {
    return isClosed;
  }

  public void release(String error) {
    if (isClosed()) return;
    isClosed = true;
    try {
      if (oldScreenOffTimeout != null) setKeepScreenLight(false);
      if (realSize != null) fallbackResolution();
      controlPacket.deviceError(error);
    } catch (Exception ignored) {
    }
  }

}
