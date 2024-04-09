package top.saymzx.easycontrol.app.client.tools;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Pair;
import android.view.Display;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.client.Client;
import top.saymzx.easycontrol.app.client.view.FullActivity;
import top.saymzx.easycontrol.app.client.view.MiniView;
import top.saymzx.easycontrol.app.client.view.SmallView;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.entity.MyInterface;
import top.saymzx.easycontrol.app.helper.PublicTools;

public class ClientController implements TextureView.SurfaceTextureListener {
  private final Device device;
  private final ControlPacket controlPacket;
  private final MyInterface.MyFunction handle;


  // 执行线程
  private final HandlerThread mainThread = new HandlerThread("easycontrol_client_main");
  private Handler mainHandler;

  public ClientController(Device device, ControlPacket controlPacket, MyInterface.MyFunction handle) {
    this.device = device;
    this.controlPacket = controlPacket;
    this.handle = handle;
    mainThread.start();
    mainHandler = new Handler(mainThread.getLooper());
    textureView.setSurfaceTextureListener(this);
    setTouchListener();
    // 启动子服务
    mainHandler.post(this::otherService);
  }

  public void handleAction(String action, ByteBuffer byteBuffer, int delay) {
    if (delay == 0) mainHandler.post(() -> handleAction(action, byteBuffer));
    else mainHandler.postDelayed(() -> handleAction(action, byteBuffer), delay);
  }

  private void handleAction(String action, ByteBuffer byteBuffer) {
    try {
      switch (action) {
        case "keepAlive":
          controlPacket.keepAlive();
          break;
        case "changeToSmall":
          changeToSmall();
          break;
        case "changeToFull":
          changeToFull();
          break;
        case "changeToMini":
          changeToMini(byteBuffer);
          break;
        case "changeToApp":
          changeToApp();
          break;
        case "buttonPower":
          clientStream.writeToMain(ControlPacket.createPowerEvent(-1));
          break;
        case "buttonWake":
          clientStream.writeToMain(ControlPacket.createPowerEvent(1));
          break;
        case "buttonLock":
          clientStream.writeToMain(ControlPacket.createPowerEvent(0));
          break;
        case "buttonLight":
          clientStream.writeToMain(ControlPacket.createLightEvent(Display.STATE_ON));
          clientStream.writeToMain(ControlPacket.createLightEvent(Display.STATE_OFF));
          break;
        case "buttonLightOff":
          clientStream.writeToMain(ControlPacket.createLightEvent(Display.STATE_UNKNOWN));
          break;
        case "buttonBack":
          clientStream.writeToMain(ControlPacket.createKeyEvent(4, 0));
          break;
        case "buttonHome":
          clientStream.writeToMain(ControlPacket.createKeyEvent(3, 0));
          break;
        case "buttonSwitch":
          clientStream.writeToMain(ControlPacket.createKeyEvent(187, 0));
          break;
        case "buttonRotate":
          clientStream.writeToMain(ControlPacket.createRotateEvent());
          break;

        case "checkSizeAndSite":
          checkSizeAndSite();
          break;
        case "checkClipBoard":
          checkClipBoard();
          break;
        default:
          if (byteBuffer == null) break;
        case "writeByteBuffer":
          clientStream.writeToMain(byteBuffer);
          break;
        case "updateMaxSize":
          updateMaxSize(byteBuffer);
          break;
        case "updateVideoSize":
          updateVideoSize(byteBuffer);
          break;
        case "runShell":
          runShell(byteBuffer);
          break;
        case "setClipBoard":
          setClipBoard(byteBuffer);
          break;
      }
    } catch (Exception ignored) {
      PublicTools.logToast("controller", AppData.applicationContext.getString(R.string.toast_stream_closed) + action, true);
      Client.sendAction(device.uuid, "close", ByteBuffer.allocate(1), 0);
    }
  }

  private void otherService() {
    handleAction("checkClipBoard", null, 0);
    handleAction("keepAlive", null, 0);
    handleAction("checkSizeAndSite", null, 0);
    mainHandler.postDelayed(this::otherService, 2000);
  }





  private synchronized void changeToApp() throws Exception {
    // 获取当前APP
    String output = clientStream.runShell("dumpsys window | grep mCurrentFocus=Window");
    // 创建匹配器
    Matcher matcher = Pattern.compile(" ([a-zA-Z0-9.]+)/").matcher(output);
    // 进行匹配
    if (matcher.find()) {
      Device tempDevice = device.clone(String.valueOf(UUID.randomUUID()));
      tempDevice.name = "----";
      tempDevice.address = tempDevice.address + "#" + matcher.group(1);
      // 为了错开界面
      tempDevice.smallX += 200;
      tempDevice.smallY += 200;
      tempDevice.smallLength -= 200;
      tempDevice.miniY += 200;
      Client.startDevice(tempDevice);
    }
  }



  public void close() {
    hide();
    mainThread.quitSafely();
    if (surfaceTexture != null) surfaceTexture.release();
  }






}
