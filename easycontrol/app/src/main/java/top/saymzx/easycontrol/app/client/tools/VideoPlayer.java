package top.saymzx.easycontrol.app.client.tools;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.util.Pair;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.client.decode.AudioDecode;
import top.saymzx.easycontrol.app.client.decode.VideoDecode;
import top.saymzx.easycontrol.app.client.view.FullActivity;
import top.saymzx.easycontrol.app.client.view.MiniView;
import top.saymzx.easycontrol.app.client.view.SmallView;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.helper.PublicTools;

public class VideoPlayer {
  private boolean isClose = false;

  private final Device device;
  private final ControlPacket controlPacket;

  private final TextureView textureView = new TextureView(AppData.applicationContext);
  private SurfaceTexture surfaceTexture;

  private SmallView smallView;
  private MiniView miniView;
  private FullActivity fullView;

  private Pair<Integer, Integer> videoSize;
  private Pair<Integer, Integer> maxSize;
  private Pair<Integer, Integer> surfaceSize;


  public VideoPlayer(Device device, ControlPacket controlPacket) {
    this.device = device;
    this.controlPacket = controlPacket;
  }

  public void handle(ByteBuffer byteBuffer) {
    int mode = byteBuffer.getInt();
    if (mode==ControlPacket.VIDEO_CONFIG)handleVideoInfo(byteBuffer);
  }

  private void handleVideoInfo(ByteBuffer byteBuffer){

  }

  private void sendVideoConfig(ByteBuffer byteBuffer){

  }

  public void setFullView(FullActivity fullView) {
    this.fullView = fullView;
  }

  public TextureView getTextureView() {
    return textureView;
  }

  private synchronized void changeToFull() {
    hide();
    Intent intent = new Intent(AppData.mainActivity, FullActivity.class);
    intent.putExtra("uuid", device.uuid);
    AppData.mainActivity.startActivity(intent);
  }

  private synchronized void changeToSmall() {
    hide();
    if (!checkFloatPermission()) {
      PublicTools.logToast("controller", AppData.applicationContext.getString(R.string.toast_float_per), true);
      changeToFull();
    } else {
      if (smallView == null) smallView = new SmallView(device.uuid);
      AppData.uiHandler.post(smallView::show);
    }
  }

  private synchronized void changeToMini(ByteBuffer byteBuffer) {
    hide();
    if (!checkFloatPermission()) {
      PublicTools.logToast("controller", AppData.applicationContext.getString(R.string.toast_float_per), true);
      changeToFull();
    } else {
      if (miniView == null) miniView = new MiniView(device.uuid);
      AppData.uiHandler.post(() -> miniView.show(byteBuffer));
    }
  }

  private synchronized void hide() {
    if (fullView != null) AppData.uiHandler.post(fullView::hide);
    fullView = null;
    if (smallView != null) AppData.uiHandler.post(smallView::hide);
    if (miniView != null) AppData.uiHandler.post(miniView::hide);
  }

  private void mainStreamIn() {
    AudioDecode audioDecode = null;
    boolean useOpus = true;
    try {
      if (clientStream.readByteFromMain() == 1) useOpus = clientStream.readByteFromMain() == 1;
      // 循环处理报文
      while (!Thread.interrupted()) {
        switch (clientStream.readByteFromMain()) {
          case AUDIO_EVENT:
            ByteBuffer audioFrame = clientStream.readFrameFromMain();
            if (audioDecode != null) audioDecode.decodeIn(audioFrame);
            else audioDecode = new AudioDecode(useOpus, audioFrame, playHandler);
            break;
          case CLIPBOARD_EVENT:
            clientController.handleAction("setClipBoard", clientStream.readByteArrayFromMain(clientStream.readIntFromMain()), 0);
            break;
          case CHANGE_SIZE_EVENT:
            clientController.handleAction("updateVideoSize", clientStream.readByteArrayFromMain(8), 0);
            break;
        }
      }
    } catch (InterruptedException ignored) {
    } catch (Exception e) {
      PublicTools.logToast("player", e.toString(), false);
    } finally {
      if (audioDecode != null) audioDecode.release();
    }
  }

  private void videoStreamIn() {
    VideoDecode videoDecode = null;
    try {
      boolean useH265 = clientStream.readByteFromVideo() == 1;
      Pair<Integer, Integer> videoSize = new Pair<>(clientStream.readIntFromVideo(), clientStream.readIntFromVideo());
      Surface surface = new Surface(clientController.getTextureView().getSurfaceTexture());
      ByteBuffer csd0 = clientStream.readFrameFromVideo();
      ByteBuffer csd1 = useH265 ? null : clientStream.readFrameFromVideo();
      videoDecode = new VideoDecode(videoSize, surface, csd0, csd1, playHandler);
      while (!Thread.interrupted()) videoDecode.decodeIn(clientStream.readFrameFromVideo());
    } catch (Exception ignored) {
    } finally {
      if (videoDecode != null) videoDecode.release();
    }
  }

  public void close() {
    if (isClose) return;
    isClose = true;
    mainStreamInThread.interrupt();
    videoStreamInThread.interrupt();
    playHandlerThread.interrupt();
  }
}
