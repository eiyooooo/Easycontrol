package top.saymzx.easycontrol.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import top.saymzx.easycontrol.app.client.Client;
import top.saymzx.easycontrol.app.client.tools.AdbTools;
import top.saymzx.easycontrol.app.databinding.ActivityMainBinding;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.helper.DeviceListAdapter;
import top.saymzx.easycontrol.app.helper.MyBroadcastReceiver;
import top.saymzx.easycontrol.app.helper.ViewTools;

public class MainActivity extends Activity {

  private ActivityMainBinding activityMainBinding;
  public DeviceListAdapter deviceListAdapter;

  // 广播
  private final MyBroadcastReceiver myBroadcastReceiver = new MyBroadcastReceiver();

  @SuppressLint("SourceLockedOrientationActivity")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AppData.init(this);
    ViewTools.setStatusAndNavBar(this);
    ViewTools.setLocale(this);
    activityMainBinding = ActivityMainBinding.inflate(this.getLayoutInflater());
    setContentView(activityMainBinding.getRoot());
    // 检测激活
    checkActive();
    // 设置设备列表适配器
    deviceListAdapter = new DeviceListAdapter(this);
    activityMainBinding.devicesList.setAdapter(deviceListAdapter);
    myBroadcastReceiver.setDeviceListAdapter(deviceListAdapter);
    // 设置按钮监听
    setButtonListener();
    // 注册广播监听
    myBroadcastReceiver.register(this);
    // 重置已连接设备
    myBroadcastReceiver.resetUSB();
    // 自启动设备
    AppData.uiHandler.postDelayed(() -> {
      for (Device device : AdbTools.devicesList) if (device.connectOnStart) Client.startDevice(device);
    }, 2000);
  }

  @Override
  protected void onDestroy() {
    myBroadcastReceiver.unRegister(this);
    super.onDestroy();
  }

  // 检测激活
  private void checkActive() {
    if (!AppData.setting.getIsActive()) startActivity(new Intent(this, ActiveActivity.class));
  }

  // 设置按钮监听
  private void setButtonListener() {
    activityMainBinding.buttonAdd.setOnClickListener(v -> startActivity(new Intent(this, DeviceDetailActivity.class)));
    activityMainBinding.buttonSet.setOnClickListener(v -> startActivity(new Intent(this, SetActivity.class)));
  }

}