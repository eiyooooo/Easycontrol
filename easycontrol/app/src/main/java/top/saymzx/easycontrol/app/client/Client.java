package top.saymzx.easycontrol.app.client;

import android.os.Handler;
import android.os.HandlerThread;

import java.nio.ByteBuffer;
import java.util.HashMap;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.client.decode.AudioDecode;
import top.saymzx.easycontrol.app.client.decode.VideoDecode;
import top.saymzx.easycontrol.app.client.tools.AdbTools;
import top.saymzx.easycontrol.app.client.tools.ControlPacket;
import top.saymzx.easycontrol.app.client.tools.DeviceTool;
import top.saymzx.easycontrol.app.client.tools.FileTool;
import top.saymzx.easycontrol.app.client.tools.Stream;
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
  private static final HashMap<Integer, VideoDecode> videoDecodes = new HashMap<>();
  private static final HashMap<Integer, AudioDecode> audioDecodes = new HashMap<>();
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

  private void mainService() throws Exception {
    while (!Thread.interrupted()) {
      int mode = stream.readInt();
      ByteBuffer data = stream.readByteArray(stream.readInt());
      if (mode == ControlPacket.VIDEO_EVENT) {
        int videoId = stream.readInt();
        VideoDecode videoDecode = videoDecodes.get(videoId);
        if (videoDecode == null || videoDecode.isClosed()) controlPacket.videoError(videoId, "videoId is close");
        else videoDecode.handle(data);
      } else if (mode == ControlPacket.AUDIO_EVENT) {
        int auidoId = stream.readInt();
        AudioDecode audioDecode = audioDecodes.get(auidoId);
        if (audioDecode == null || audioDecode.isClosed()) controlPacket.audioError(auidoId, "audioId is close");
        else audioDecode.handle(data);
      } else if (mode == ControlPacket.FILE_EVENT) {
        if (fileTool == null || fileTool.isClosed()) controlPacket.fileError("file is close");
        fileTool.handle(data);
      } else if (mode == ControlPacket.DEVICE_EVENT) {
        if (deviceTool == null || deviceTool.isClosed()) controlPacket.deviceError("device is close");
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
    for (VideoDecode videoDecode : videoDecodes.values()) videoDecode.release(null);
    for (AudioDecode audioDecode : audioDecodes.values()) audioDecode.release(null);
    if (fileTool != null) fileTool.release(null);
    if (deviceTool != null) deviceTool.release(null);
    if (stream != null) stream.close();
    // 更新数据库
    AppData.dbHelper.update(device);
    allClient.remove(device.uuid);
  }

}
