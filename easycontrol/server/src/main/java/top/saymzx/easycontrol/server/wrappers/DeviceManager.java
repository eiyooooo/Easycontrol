package top.saymzx.easycontrol.server.wrappers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceManager {
  // 打开并移动应用
  public static void startAndMoveAppToVirtualDisplay(String app, int displayId) throws IOException, InterruptedException {
    int appStackId = getAppStackId(app);
    if (appStackId == -1) {
      execReadOutput("monkey -p " + app + " -c android.intent.category.LAUNCHER 1");
      appStackId = getAppStackId(app);
    }
    if (appStackId == -1) throw new IOException("error app");
    execReadOutput("am display move-stack " + appStackId + " " + displayId);
  }

  private static int getAppStackId(String app) throws IOException, InterruptedException {
    String amStackList = execReadOutput("am stack list");
    Matcher m = Pattern.compile("taskId=([0-9]+): " + app).matcher(amStackList);
    if (!m.find()) return -1;
    return Integer.parseInt(Objects.requireNonNull(m.group(1)));
  }

  public static String execReadOutput(String cmd) throws IOException, InterruptedException {
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
}
