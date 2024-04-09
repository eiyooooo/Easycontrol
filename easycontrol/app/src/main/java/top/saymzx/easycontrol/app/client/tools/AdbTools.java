package top.saymzx.easycontrol.app.client.tools;

import android.hardware.usb.UsbDevice;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import top.saymzx.easycontrol.app.BuildConfig;
import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.adb.Adb;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.entity.MyInterface;

public class AdbTools {
  public static final ArrayList<Device> devicesList = new ArrayList<>();
  public static final HashMap<String, UsbDevice> usbDevicesList = new HashMap<>();

  private static final String serverName = "/data/local/tmp/easycontrol_server_" + BuildConfig.VERSION_CODE + ".jar";

  public static Adb connectADB(Device device) throws Exception {
    if (device.isLinkDevice()) return new Adb(usbDevicesList.get(device.address), AppData.keyPair);
    else return new Adb(getIp(device.address), device.adbPort, AppData.keyPair);
  }

  public static Stream connectServer(Adb adb, Device device) throws Exception {
    // 发送启动Server
    if (BuildConfig.ENABLE_DEBUG_FEATURE || !adb.runAdbCmd("ls /data/local/tmp/easycontrol_*").contains(serverName)) {
      adb.runAdbCmd("rm /data/local/tmp/easycontrol_* ");
      adb.pushFile(AppData.applicationContext.getResources().openRawResource(R.raw.easycontrol_server), serverName, null);
    }
    adb.getShell().write(ByteBuffer.wrap(("app_process -Djava.class.path=" + serverName + " / top.saymzx.easycontrol.server.Server " + device.serverPort + "\n").getBytes()));
    // 连接Server
    Thread.sleep(50);
    for (int i = 0; i < 40; i++) {
      try {
        if (device.isLinkDevice()) return new AdbStream(adb.tcpForward(device.serverPort));
        else return new TcpStream(new Socket(device.address, device.serverPort));
      } catch (Exception ignored) {
        Thread.sleep(50);
      }
    }
    throw new IOException(AppData.applicationContext.getString(R.string.toast_connect_server));
  }

  public static void runOnceCmd(Device device, String cmd, MyInterface.MyFunctionBoolean handle) {
    new Thread(() -> {
      try {
        Adb adb = connectADB(device);
        adb.runAdbCmd(cmd);
        if (handle != null) handle.run(true);
      } catch (Exception ignored) {
        if (handle != null) handle.run(false);
      }
    }).start();
  }

  public static void restartOnTcpip(Device device, MyInterface.MyFunctionBoolean handle) {
    new Thread(() -> {
      try {
        Adb adb = connectADB(device);
        String output = adb.restartOnTcpip(5555);
        if (handle != null) handle.run(output.contains("restarting"));
      } catch (Exception ignored) {
        if (handle != null) handle.run(false);
      }
    }).start();
  }

  private static String getIp(String address) throws IOException {
    // 特殊格式
    if (address.contains("*")) {
      if (address.equals("*gateway*")) address = getGateway();
      if (address.contains("*netAddress*")) address = address.replace("*netAddress*", getNetAddress());
    } else address = InetAddress.getByName(address).getHostAddress();
    return address;
  }

  // 获取网关地址
  private static String getGateway() {
    int ip = AppData.wifiManager.getDhcpInfo().gateway;
    // 没有wifi时，设置为1.1.1.1
    if (ip == 0) ip = 16843009;
    return decodeIntToIp(ip, 4);
  }

  // 获取子网地址
  private static String getNetAddress() {
    int ip = AppData.wifiManager.getDhcpInfo().gateway;
    // 没有wifi时，设置为1.1.1.1
    if (ip == 0) ip = 16843009;
    // 因为此标识符使用场景有限，为了节省资源，默认地址为24位掩码地址
    return decodeIntToIp(ip, 3);
  }

  // 解析地址
  private static String decodeIntToIp(int ip, int len) {
    if (len < 1 || len > 4) return "";
    StringBuilder builder = new StringBuilder();
    builder.append(ip & 0xff);
    if (len > 1) {
      builder.append(".");
      builder.append((ip >> 8) & 0xff);
      if (len > 2) {
        builder.append(".");
        builder.append((ip >> 16) & 0xff);
        if (len > 3) {
          builder.append(".");
          builder.append((ip >> 24) & 0xff);
        }
      }
    }
    return builder.toString();
  }
}
