package top.saymzx.easycontrol.server.tools;

import android.os.Handler;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileTool {
  private static boolean isClosed = false;
  private final Handler mainHandler;
  private final ControlPacket controlPacket;

  public FileTool(Handler mainHandler, ControlPacket controlPacket) {
    this.mainHandler = mainHandler;
    this.controlPacket = controlPacket;
  }

  public void handle(ByteBuffer byteBuffer) {
    if (isClosed) return;
    try {
      int mode = byteBuffer.getInt();
      if (mode == ControlPacket.FILE_GET_LIST) handleFileGetList(byteBuffer);
      else if (mode == ControlPacket.FILE_GET) handleFileGet(byteBuffer);
    } catch (Exception e) {
      release(e.toString());
    }
  }

  private void handleFileGetList(ByteBuffer byteBuffer) throws Exception {
    // 获取文件路径
    String filePath = byteBuffer.toString();
    // 打开文件
    File file = new File(filePath);
    if (file.isDirectory()) {
      List<byte[]> fileNames = new ArrayList<>();
      File[] files = file.listFiles();
      if (files != null) {
        for (File f : files) {
          byte[] tmpTextByte = f.getName().getBytes(StandardCharsets.UTF_8);
          if (tmpTextByte.length == 0 || tmpTextByte.length > 5000) continue;
          fileNames.add(tmpTextByte);
        }
      }
      // 发送
      controlPacket.fileList(filePath.getBytes(StandardCharsets.UTF_8), fileNames);
    }
  }

  private void handleFileGet(ByteBuffer byteBuffer) throws Exception {
    // 获取文件路径
    String filePath = byteBuffer.toString();
    // 读取文件
    File file = new File(filePath);
    if (!file.isDirectory()) {
      // 分段读取，分成4096
    }
  }

  public boolean isClosed() {
    return isClosed;
  }

  public void release(String error) {
    if (isClosed()) return;
    isClosed = true;
    try {
      controlPacket.fileError(error);
    } catch (Exception ignored) {
    }
  }

}
