package top.saymzx.easycontrol.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import top.saymzx.easycontrol.app.databinding.ActivityFileBinding;
import top.saymzx.easycontrol.app.helper.FileListAdapter;
import top.saymzx.easycontrol.app.helper.PublicTools;
import top.saymzx.easycontrol.app.helper.ViewTools;

public class FileActivity extends Activity {

  private ActivityFileBinding activityFileBinding;
  private FileListAdapter localFileListAdapter;
  private FileListAdapter remoteFileListAdapter;

  private final List<String> localFiles = new ArrayList<>();
  private final List<String> remoteFiles = new ArrayList<>();

  private String localPath = "/sdcard";
  private String remotePath = "/sdcard";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ViewTools.setStatusAndNavBar(this);
    ViewTools.setLocale(this);
    activityFileBinding = ActivityFileBinding.inflate(this.getLayoutInflater());
    setContentView(activityFileBinding.getRoot());
    setButtonListener();
    // 创建列表适配器
    localFileListAdapter = new FileListAdapter(this, localFiles, this::listLocalFile);
    remoteFileListAdapter = new FileListAdapter(this, remoteFiles, this::listRemoteFile);
    activityFileBinding.localFile.setAdapter(localFileListAdapter);
    activityFileBinding.remoteFile.setAdapter(remoteFileListAdapter);
    // 检查权限
    if (!checkStoragePermission()) requestStoragePermission();
    else {
      listLocalFile();
      listRemoteFile();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == 10) {
      if (!checkStoragePermission()) PublicTools.logToast("file", getString(R.string.toast_storage_per), true);
      else {
        listLocalFile();
        listRemoteFile();
      }
    }
  }

  // 检查权限
  private boolean checkStoragePermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
  }

  private void requestStoragePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
      intent.setData(Uri.parse("package:" + this.getPackageName()));
      startActivityForResult(intent, 10);
    }
  }

  // 罗列文件
  private void listLocalFile() {
    // 获取文件路径
    String filePath;
    if (Objects.equals(localFileListAdapter.localSelect, "..")) filePath = localPath.substring(0, localPath.lastIndexOf("/"));
    else filePath = localPath + "/" + localFileListAdapter.localSelect;
    // 打开文件
    File file = new File(filePath);
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        localPath = filePath;
        localFiles.clear();
        localFileListAdapter.localSelect = "";
        for (File f : files) localFiles.add(f.getName());
        localFileListAdapter.update();
      }
    }
  }

  private void listRemoteFile() {
    File file = new File(localPath += localFileListAdapter.localSelect);
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        localPath = localPath + localFileListAdapter.localSelect + "/";
        localFiles.clear();
        for (File f : files) localFiles.add(f.getName());
        localFileListAdapter.update();
      }
    }
  }

  public void pushFile(InputStream inputStream, String fileName) {
//    if (inputStream == null) return;
//    Pair<ItemLoadingBinding, Dialog> loading = ViewTools.createLoading(context);
//    loading.second.show();
//    AdbTools.pushFile(sendFileDevice, inputStream, fileName, process -> AppData.uiHandler.post(() -> {
//      if (process < 0) {
//        loading.second.cancel();
//        Toast.makeText(context, context.getString(R.string.toast_fail), Toast.LENGTH_SHORT).show();
//      } else if (process == 100) {
//        loading.second.cancel();
//        Toast.makeText(context, context.getString(R.string.toast_success), Toast.LENGTH_SHORT).show();
//      } else loading.first.text.setText(process + " %");
//    }));
  }

  // 设置按钮监听
  private void setButtonListener() {
    activityFileBinding.backButton.setOnClickListener(v -> finish());
  }

}