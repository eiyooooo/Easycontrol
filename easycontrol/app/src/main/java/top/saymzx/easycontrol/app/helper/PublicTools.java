package top.saymzx.easycontrol.app.helper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.adb.AdbBase64;
import top.saymzx.easycontrol.app.adb.AdbKeyPair;
import top.saymzx.easycontrol.app.entity.AppData;

public class PublicTools {

  // DP转PX
  public static int dp2px(Float dp) {
    return (int) (dp * getScreenSize().density);
  }

  // 获取IP地址
  public static Pair<ArrayList<String>, ArrayList<String>> getIp() {
    ArrayList<String> ipv4Addresses = new ArrayList<>();
    ArrayList<String> ipv6Addresses = new ArrayList<>();
    try {
      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
      while (networkInterfaces.hasMoreElements()) {
        NetworkInterface networkInterface = networkInterfaces.nextElement();
        Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
        while (inetAddresses.hasMoreElements()) {
          InetAddress inetAddress = inetAddresses.nextElement();
          if (!inetAddress.isLoopbackAddress()) {
            if (inetAddress instanceof Inet4Address) ipv4Addresses.add(inetAddress.getHostAddress());
            else if (inetAddress instanceof Inet6Address && !inetAddress.isLinkLocalAddress()) ipv6Addresses.add("[" + inetAddress.getHostAddress() + "]");
          }
        }
      }
    } catch (Exception ignored) {
    }
    return new Pair<>(ipv4Addresses, ipv6Addresses);
  }

  // 浏览器打开
  public static void startUrl(Context context, String url) {
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.addCategory(Intent.CATEGORY_BROWSABLE);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setData(Uri.parse(url));
      context.startActivity(intent);
    } catch (Exception ignored) {
      Toast.makeText(context, context.getString(R.string.toast_no_browser), Toast.LENGTH_SHORT).show();
    }
  }

  // 日志
  public static void logToast(String type, String msg, boolean showToast) {
    Log.e("Easycontrol_" + type, msg);
    if (showToast) AppData.uiHandler.post(() -> Toast.makeText(AppData.applicationContext, type + ":" + msg, Toast.LENGTH_SHORT).show());
  }

  // 获取密钥文件
  public static Pair<File, File> getAdbKeyFile(Context context) {
    return new Pair<>(new File(context.getApplicationContext().getFilesDir(), "public.key"), new File(context.getApplicationContext().getFilesDir(), "private.key"));
  }

  // 读取密钥
  public static AdbKeyPair readAdbKeyPair() {
    try {
      AdbKeyPair.setAdbBase64(new AdbBase64() {
        @Override
        public String encodeToString(byte[] data) {
          return Base64.encodeToString(data, Base64.DEFAULT);
        }

        @Override
        public byte[] decode(byte[] data) {
          return Base64.decode(data, Base64.DEFAULT);
        }
      });
      Pair<File, File> adbKeyFile = PublicTools.getAdbKeyFile(AppData.applicationContext);
      if (!adbKeyFile.first.isFile() || !adbKeyFile.second.isFile()) AdbKeyPair.generate(adbKeyFile.first, adbKeyFile.second);
      return AdbKeyPair.read(adbKeyFile.first, adbKeyFile.second);
    } catch (Exception ignored) {
      return reGenerateAdbKeyPair();
    }
  }

  // 生成密钥
  public static AdbKeyPair reGenerateAdbKeyPair() {
    try {
      Pair<File, File> adbKeyFile = PublicTools.getAdbKeyFile(AppData.applicationContext);
      AdbKeyPair.generate(adbKeyFile.first, adbKeyFile.second);
      return AdbKeyPair.read(adbKeyFile.first, adbKeyFile.second);
    } catch (Exception ignored) {
      return null;
    }
  }

  // 获取设备当前分辨率
  public static DisplayMetrics getScreenSize() {
    DisplayMetrics screenSize = new DisplayMetrics();
    Display display = AppData.windowManager.getDefaultDisplay();
    display.getRealMetrics(screenSize);
    return screenSize;
  }

  // 扫描局域网设备
  public static ArrayList<String> scanAddress() {
    ArrayList<String> scannedAddresses = new ArrayList<>();
    ExecutorService executor = Executors.newFixedThreadPool(256);
    ArrayList<String> ipv4List = getIp().first;
    for (String ipv4 : ipv4List) {
      Matcher matcher = Pattern.compile("(\\d+\\.\\d+\\.\\d+)").matcher(ipv4);
      if (matcher.find()) {
        String subnet = matcher.group(1);
        for (int i = 1; i <= 255; i++) {
          String host = subnet + "." + i;
          executor.execute(() -> {
            try {
              Socket socket = new Socket();
              socket.connect(new InetSocketAddress(host, 5555), 800);
              socket.close();
              // 标注本机
              scannedAddresses.add(host + ":5555" + (host.equals(ipv4) ? " (" + AppData.applicationContext.getString(R.string.main_scan_device_local) + ")" : ""));
            } catch (Exception ignored) {
            }
          });
        }
      }
    }
    executor.shutdown();
    try {
      while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
      }
    } catch (InterruptedException ignored) {
    }
    return scannedAddresses;
  }

}