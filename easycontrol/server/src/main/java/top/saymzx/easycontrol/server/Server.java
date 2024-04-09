/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 */
package top.saymzx.easycontrol.server;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IInterface;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.HashMap;

import top.saymzx.easycontrol.server.encode.AudioEncode;
import top.saymzx.easycontrol.server.encode.VideoEncode;
import top.saymzx.easycontrol.server.tools.ControlPacket;
import top.saymzx.easycontrol.server.tools.DeviceTool;
import top.saymzx.easycontrol.server.tools.FileTool;
import top.saymzx.easycontrol.server.tools.Stream;
import top.saymzx.easycontrol.server.tools.TcpStream;
import top.saymzx.easycontrol.server.wrappers.ClipboardManager;
import top.saymzx.easycontrol.server.wrappers.DisplayManager;
import top.saymzx.easycontrol.server.wrappers.InputManager;
import top.saymzx.easycontrol.server.wrappers.SurfaceControl;
import top.saymzx.easycontrol.server.wrappers.WindowManager;

// 此部分代码摘抄借鉴了著名投屏软件Scrcpy的开源代码(https://github.com/Genymobile/scrcpy/tree/master/server)
public final class Server {
  private static boolean isClosed = false;
  // 组件
  private static Stream stream;
  private static ControlPacket controlPacket;
  private static final HashMap<Integer, VideoEncode> videoEncodes = new HashMap<>();
  private static final HashMap<Integer, AudioEncode> audioEncodes = new HashMap<>();
  private static DeviceTool deviceTool;
  private static FileTool fileTool;

  private static final HandlerThread mainHandlerThread = new HandlerThread("easycontrol_main");
  public static Handler mainHandler;

  public static void main(String... args) {
    mainHandlerThread.start();
    mainHandler = new Handler(mainHandlerThread.getLooper());
    int serverPort = Integer.parseInt(args[0]);
    try {
      // 初始化
      setManagers();
      // 连接
      stream = connectClient(serverPort);
      controlPacket = new ControlPacket(stream);
      // 连接检测
      lastKeepAliveTime = System.currentTimeMillis();
      mainHandler.postDelayed(Server::checkKeepAlive, 2000);
      // 主程序
      mainService();
    } catch (Exception e) {
      release();
    }
  }

  private static Method GET_SERVICE_METHOD;

  @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
  private static void setManagers() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    GET_SERVICE_METHOD = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
    // 1
    WindowManager.init(getService("window", "android.view.IWindowManager"));
    // 2
    DisplayManager.init(Class.forName("android.hardware.display.DisplayManagerGlobal").getDeclaredMethod("getInstance").invoke(null));
    // 3
    Class<?> inputManagerClass;
    try {
      inputManagerClass = Class.forName("android.hardware.input.InputManagerGlobal");
    } catch (ClassNotFoundException e) {
      inputManagerClass = android.hardware.input.InputManager.class;
    }
    InputManager.init(inputManagerClass.getDeclaredMethod("getInstance").invoke(null));
    // 4
    ClipboardManager.init(getService("clipboard", "android.content.IClipboard"));
    // 5
    SurfaceControl.init();
  }

  private static IInterface getService(String service, String type) {
    try {
      IBinder binder = (IBinder) GET_SERVICE_METHOD.invoke(null, service);
      Method asInterfaceMethod = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
      return (IInterface) asInterfaceMethod.invoke(null, binder);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static Stream connectClient(int serverPort) throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
      serverSocket.setSoTimeout(1000 * 5);
      return new TcpStream(serverSocket.accept());
    }
  }

  private static long lastKeepAliveTime;

  private static void checkKeepAlive() {
    if (System.currentTimeMillis() - lastKeepAliveTime > 6000) release();
  }

  private static void mainService() throws IOException, InterruptedException {
    while (!Thread.interrupted()) {
      int mode = stream.readInt();
      ByteBuffer data = stream.readByteArray(stream.readInt());
      if (mode == ControlPacket.KEEPALIVE_EVENT) lastKeepAliveTime = System.currentTimeMillis();
      else if (mode == ControlPacket.VIDEO_EVENT) {
        int videoEncodeId = stream.readInt();
        VideoEncode videoEncode = videoEncodes.get(videoEncodeId);
        if (videoEncode == null || videoEncode.isClosed()) {
          videoEncode = new VideoEncode(mainHandler, controlPacket);
          videoEncodes.put(videoEncodeId, videoEncode);
        }
        videoEncode.handle(data);
      } else if (mode == ControlPacket.AUDIO_EVENT) {
        int auidoEncodeId = stream.readInt();
        AudioEncode audioEncode = audioEncodes.get(auidoEncodeId);
        if (audioEncode == null || audioEncode.isClosed()) {
          audioEncode = new AudioEncode(mainHandler, controlPacket);
          audioEncodes.put(auidoEncodeId, audioEncode);
        }
        audioEncode.handle(data);
      } else if (mode == ControlPacket.FILE_EVENT) {
        if (fileTool == null || fileTool.isClosed()) fileTool = new FileTool(controlPacket);
        fileTool.handle(data);
      } else if (mode == ControlPacket.DEVICE_EVENT) {
        if (deviceTool == null || deviceTool.isClosed()) deviceTool = new DeviceTool(controlPacket);
        deviceTool.handle(data);
      }
    }
  }

  private static void release() {
    if (isClosed) return;
    isClosed = true;
    // 关闭组件
    mainHandlerThread.quit();
    for (VideoEncode videoEncode : videoEncodes.values()) videoEncode.release(null);
    for (AudioEncode audioEncode : audioEncodes.values()) audioEncode.release(null);
    if (fileTool != null) fileTool.release(null);
    if (deviceTool != null) deviceTool.release(null);
    if (stream != null) stream.close();
    // 更新数据库
    Runtime.getRuntime().exit(0);
  }

}
