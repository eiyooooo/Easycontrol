package top.saymzx.easycontrol.app.client.tools;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

import android.content.ClipData;
import android.os.Handler;
import android.view.Display;

import java.nio.ByteBuffer;
import java.util.Objects;

import top.saymzx.easycontrol.app.entity.AppData;

public class DeviceTool {
  private boolean isClosed = false;
  private final Handler mainHandler;
  private final ControlPacket controlPacket;

  public DeviceTool(Handler mainHandler, ControlPacket controlPacket) throws Exception {
    this.mainHandler = mainHandler;
    this.controlPacket = controlPacket;
  }

  public void handle(ByteBuffer byteBuffer) {
    if (isClosed) return;
    try {
      int mode = byteBuffer.getInt();
      if (mode == ControlPacket.DEVICE_CLIP) handleDeviceClip(byteBuffer);
    } catch (Exception e) {
      release(e.toString());
    }
  }

  private String nowClipboardText = "";
  private void handleDeviceClip(ByteBuffer byteBuffer) {
    nowClipboardText = byteBuffer.toString();
    AppData.clipBoard.setPrimaryClip(ClipData.newPlainText(MIMETYPE_TEXT_PLAIN, nowClipboardText));
  }

  private void checkClipBoard() {
    ClipData clipBoard = AppData.clipBoard.getPrimaryClip();
    if (clipBoard != null && clipBoard.getItemCount() > 0) {
      String newClipBoardText = String.valueOf(clipBoard.getItemAt(0).getText());
      if (!Objects.equals(nowClipboardText, newClipBoardText)) {
        nowClipboardText = newClipBoardText;
        handleAction("writeByteBuffer", ControlPacket.createClipboardEvent(nowClipboardText), 0);
      }
    }
  }

  public void deviceTouch(int displayId, int action, int pointerId, int x, int y, int offsetTime) {
    mainHandler.post(() -> {
      try {
        controlPacket.deviceTouch(displayId, action, pointerId, x, y, offsetTime);
      } catch (Exception e) {
        release(e.toString());
      }
    });
  }

  public void deviceKey(int displayId, int key, int meta) {
    mainHandler.post(() -> {
      try {
        controlPacket.deviceKey(displayId, key, meta);
      } catch (Exception e) {
        release(e.toString());
      }
    });
  }

  public void deviceClip(String newClipboardText) {
    mainHandler.post(() -> {
      try {
        controlPacket.deviceClip(newClipboardText);
      } catch (Exception e) {
        release(e.toString());
      }
    });
  }

  public void deviceRotate(int displayId) {
    mainHandler.post(() -> {
      try {
        controlPacket.deviceRotate(displayId);
      } catch (Exception e) {
        release(e.toString());
      }
    });
  }

  public void deviceLight(int displayId) {
    deviceLight(displayId, Display.STATE_ON);
    deviceLight(displayId, Display.STATE_OFF);
  }

  public void deviceLightOff(int displayId) {
    deviceLight(displayId, Display.STATE_UNKNOWN);
  }

  private void deviceLight(int displayId, int mode) {
    mainHandler.post(() -> {
      try {
        controlPacket.deviceLight(displayId, mode);
      } catch (Exception e) {
        release(e.toString());
      }
    });
  }

  private void runShell(ByteBuffer byteBuffer) throws Exception {
    String cmd = new String(byteBuffer.array());
    clientStream.runShell(cmd);
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
}
