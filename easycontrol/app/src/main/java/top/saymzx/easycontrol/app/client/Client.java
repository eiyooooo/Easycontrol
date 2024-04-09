package top.saymzx.easycontrol.app.client;

import android.os.Handler;
import android.os.HandlerThread;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.client.tools.AdbTools;
import top.saymzx.easycontrol.app.client.tools.AudioPlayer;
import top.saymzx.easycontrol.app.client.tools.ControlPacket;
import top.saymzx.easycontrol.app.client.tools.DeviceTool;
import top.saymzx.easycontrol.app.client.tools.FileTool;
import top.saymzx.easycontrol.app.client.tools.Stream;
import top.saymzx.easycontrol.app.client.tools.VideoPlayer;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.helper.PublicTools;

public class Client {
  private static final HashMap<String, Client> allClient = new HashMap<>();

  private boolean isClosed = false;
  private final Device device;

  // 组件
  private Stream stream;
  private ControlPacket controlPacket;
  private VideoPlayer videoPlayer;
  private AudioPlayer audioPlayer;
  private DeviceTool deviceTool;
  private FileTool fileTool;

  private static final HandlerThread mainHandlerThread = new HandlerThread("easycontrol_main");
  private static Handler mainHandler;

  public Client(Device device) throws Exception {
    this.device = device;
    if (allClient.containsKey(device.uuid)) return;
    mainHandlerThread.start();
    mainHandler = new Handler(mainHandlerThread.getLooper());
    try {
      // 连接
      stream = AdbTools.connectServer(AdbTools.connectADB(device), device);
      controlPacket = new ControlPacket(stream);
      // 连接检测
      mainHandler.postDelayed(this::sendKeepAlive, 2000);
      // 主程序
      mainService();
    } catch (Exception e) {
      PublicTools.logToast("client", e.toString(), true);
      release();
    }
  }

  private void sendKeepAlive() {
    try {
      controlPacket.keepAlive();
    } catch (Exception ignored) {
      PublicTools.logToast("client", AppData.applicationContext.getString(R.string.toast_stream_closed), true);
      release();
    }
  }

  private void mainService() throws IOException, InterruptedException {
    while (!Thread.interrupted()) {
      int mode = stream.readInt();
      ByteBuffer data = stream.readByteArray(stream.readInt());
      if (mode == ControlPacket.VIDEO_EVENT) {
        if (videoPlayer == null || videoPlayer.isClosed()) videoPlayer = new VideoPlayer(device, controlPacket);
        videoPlayer.handle(data);
      } else if (mode == ControlPacket.AUDIO_EVENT) {
        if (audioPlayer == null || audioPlayer.isClosed()) audioPlayer = new AudioPlayer(device, controlPacket);
        audioPlayer.handle(data);
      } else if (mode == ControlPacket.FILE_EVENT) {
        if (fileTool == null || fileTool.isClosed()) fileTool = new FileTool(device, controlPacket);
        fileTool.handle(data);
      } else if (mode == ControlPacket.DEVICE_EVENT) {
        if (deviceTool == null || deviceTool.isClosed()) deviceTool = new DeviceTool(device, controlPacket);
        deviceTool.handle(data);
      }
    }
  }

  public boolean isClosed() {
    return isClosed;
  }

  public void release() {
    if (isClosed) return;
    isClosed = true;
    // 关闭组件
    mainHandlerThread.quit();
    if (videoPlayer != null) videoPlayer.release();
    if (audioPlayer != null) audioPlayer.release();
    if (fileTool != null) fileTool.release();
    if (deviceTool != null) deviceTool.release();
    if (stream != null) stream.close();
    // 更新数据库
    AppData.dbHelper.update(device);
    allClient.remove(device.uuid);
  }

}
