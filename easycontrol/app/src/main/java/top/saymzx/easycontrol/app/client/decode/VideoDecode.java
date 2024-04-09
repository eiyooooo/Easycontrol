package top.saymzx.easycontrol.app.client.decode;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.client.tools.ControlPacket;
import top.saymzx.easycontrol.app.client.view.FullActivity;
import top.saymzx.easycontrol.app.client.view.MiniView;
import top.saymzx.easycontrol.app.client.view.SmallView;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.helper.PublicTools;

public class VideoDecode implements TextureView.SurfaceTextureListener {
  private final int id;
  private boolean isClosed = false;
  private final Handler mainHandler;
  private final Device device;

  private final TextureView textureView = new TextureView(AppData.applicationContext);
  private SurfaceTexture surfaceTexture;

  private SmallView smallView;
  private MiniView miniView;
  private FullActivity fullView;

  private Pair<Integer, Integer> screenSize;
  private Pair<Integer, Integer> videoSize;
  private Pair<Integer, Integer> maxSize;
  private Pair<Integer, Integer> surfaceSize;

  private MediaCodec decodec;
  private final MediaFormat decodecFormat = new MediaFormat();
  private boolean supportH265;
  private final MediaCodec.Callback callback = new MediaCodec.Callback() {
    @Override
    public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int inIndex) {
      bufferQueue.offer(inIndex);
    }

    @Override
    public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int outIndex, @NonNull MediaCodec.BufferInfo bufferInfo) {
      try {
        mediaCodec.releaseOutputBuffer(outIndex, bufferInfo.presentationTimeUs);
      } catch (IllegalStateException ignored) {
      }
    }

    @Override
    public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
    }

    @Override
    public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat format) {
    }
  };
  private final ArrayList<Thread> workThreads = new ArrayList<>();

  public VideoDecode(int id, Handler mainHandler, Device device, ControlPacket controlPacket) throws Exception {
    this.id = id;
    this.mainHandler = mainHandler;
    this.device = device;
    this.controlPacket = controlPacket;
    supportH265 = device.useH265 && DecodecTools.isSupportH265();
    setTouchListener();
    // 发送启动报文
    controlPacket.videoInit(id, device.useH265 && supportH265, device.startApp);
    // 发送配置报文
    controlPacket.videoConfig(id, device.maxSize, device.maxVideoBit, device.maxFps);
    // 定时任务
    mainHandler.postDelayed(this::checkSizeAndSite, 1000);
  }

  public void handle(ByteBuffer byteBuffer) {
    if (isClosed) return;
    try {
      int mode = byteBuffer.getInt();
      if (mode == ControlPacket.VIDEO_ERROR) handleVideoError(byteBuffer);
      else if (mode == ControlPacket.VIDEO_INFO) handleVideoInfo(byteBuffer);
      else if (mode == ControlPacket.VIDEO_FRAME) handleVideoFrame(byteBuffer);
    } catch (Exception e) {
      release(e.toString());
    }
  }

  private void handleVideoError(ByteBuffer byteBuffer) {
    String err = byteBuffer.toString();
    PublicTools.logToast("videoDecode", err, false);
    release(err);
  }

  private void handleVideoInfo(ByteBuffer byteBuffer) {
    supportH265 = byteBuffer.getInt() == 1;
    screenSize = new Pair<>(byteBuffer.getInt(), byteBuffer.getInt());
    videoSize = new Pair<>(byteBuffer.getInt(), byteBuffer.getInt());
    AppData.uiHandler.post(this::reCalculateTextureViewSize);
  }

  private ByteBuffer csd0;
  private ByteBuffer csd1;

  private void handleVideoFrame(ByteBuffer byteBuffer) throws IOException {
    if (decodec == null) {
      if (csd0 == null) {
        csd0 = byteBuffer;
        if (supportH265) createVideoDecodec();
      } else {
        csd1 = byteBuffer;
        createVideoDecodec();
      }
    } else dataQueue.offer(byteBuffer);
  }

  private final LinkedBlockingQueue<ByteBuffer> dataQueue = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<Integer> bufferQueue = new LinkedBlockingQueue<>();

  public void executeIn() {
    while (!Thread.interrupted()) {
      try {
        int bufferIndex = bufferQueue.take();
        ByteBuffer buffer = decodec.getInputBuffer(bufferIndex);
        if (buffer == null) continue;
        ByteBuffer data = dataQueue.take();
        long pts = data.getLong();
        int size = data.remaining();
        buffer.put(data);
        decodec.queueInputBuffer(bufferIndex, 0, size, pts, 0);
      } catch (IllegalStateException ignored) {
      } catch (Exception e) {
        release(e.toString());
      }
    }
  }

  // 创建Codec
  private void createVideoDecodec() throws IOException {
    // 读取数据
    long csd0Pts = csd0.getLong();
    byte[] csd0Byte = new byte[csd0.remaining()];
    csd0.get(csd0Byte);
    long csd1Pts = 0;
    byte[] csd1Byte = null;
    if (!supportH265) {
      csd1Pts = csd1.getLong();
      csd1Byte = new byte[csd1.remaining()];
      csd1.get(csd1Byte);
    }
    // 创建编码器
    String codecMime = supportH265 ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
    try {
      String codecName = DecodecTools.getVideoDecoder(supportH265);
      if (Objects.equals(codecName, "")) decodec = MediaCodec.createDecoderByType(codecMime);
      else decodec = MediaCodec.createByCodecName(codecName);
    } catch (Exception ignord) {
      decodec = MediaCodec.createDecoderByType(codecMime);
    }
    decodecFormat.setString(MediaFormat.KEY_MIME, codecMime);
    decodecFormat.setInteger(MediaFormat.KEY_WIDTH, videoSize.first);
    decodecFormat.setInteger(MediaFormat.KEY_HEIGHT, videoSize.second);
    // 获取视频标识头
    decodecFormat.setByteBuffer("csd-0", ByteBuffer.wrap(csd0Byte));
    if (!supportH265) decodecFormat.setByteBuffer("csd-1", ByteBuffer.wrap(csd1Byte));
    // 异步解码
    decodec.setCallback(callback);
    // 配置解码器
    decodec.configure(decodecFormat, new Surface(textureView.getSurfaceTexture()), null, 0);
    // 启动解码器
    decodec.start();
    workThreads.clear();
    workThreads.add(new Thread(this::executeIn));
    for (Thread thread : workThreads) thread.start();
    // 解析首帧，解决开始黑屏问题
    csd0 = ByteBuffer.allocate(8 + csd0Byte.length);
    csd0.putLong(csd0Pts);
    csd0.put(csd0Byte);
    csd0.flip();
    handleVideoFrame(csd0);
    if (!supportH265) {
      csd1 = ByteBuffer.allocate(8 + csd1Byte.length);
      csd1.putLong(csd1Pts);
      csd1.put(csd1Byte);
      csd1.flip();
      handleVideoFrame(csd1);
    }
  }

  public void setFullView(FullActivity fullView) {
    this.fullView = fullView;
  }

  private synchronized void changeToFull() {
    hide();
    Intent intent = new Intent(AppData.mainActivity, FullActivity.class);
    intent.putExtra("uuid", device.uuid);
    AppData.mainActivity.startActivity(intent);
  }

  private synchronized void changeToSmall() {
    hide();
    if (noFloatPermission()) {
      PublicTools.logToast("controller", AppData.applicationContext.getString(R.string.toast_float_per), true);
      changeToFull();
    } else {
      if (smallView == null) smallView = new SmallView(device.uuid);
      AppData.uiHandler.post(smallView::show);
    }
  }

  private synchronized void changeToMini(ByteBuffer byteBuffer) {
    hide();
    if (noFloatPermission()) {
      PublicTools.logToast("controller", AppData.applicationContext.getString(R.string.toast_float_per), true);
      changeToFull();
    } else {
      if (miniView == null) miniView = new MiniView(device.uuid);
      AppData.uiHandler.post(() -> miniView.show(byteBuffer));
    }
  }

  // 检查悬浮窗权限
  private boolean noFloatPermission() {
    // 检查悬浮窗权限，防止某些设备如鸿蒙不兼容
    try {
      return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(AppData.applicationContext);
    } catch (Exception ignored) {
      return false;
    }
  }

  private synchronized void hide() {
    if (fullView != null) AppData.uiHandler.post(fullView::hide);
    fullView = null;
    if (smallView != null) AppData.uiHandler.post(smallView::hide);
    if (miniView != null) AppData.uiHandler.post(miniView::hide);
  }

  private static final int minLength = PublicTools.dp2px(200f);

  public void updateMaxSize(Pair<Integer, Integer> maxSize) {
    int width = Math.max(maxSize.first, minLength);
    int height = Math.max(maxSize.second, minLength);
    this.maxSize = new Pair<>(width, height);
    AppData.uiHandler.post(this::reCalculateTextureViewSize);
  }

  // 重新计算TextureView大小
  private void reCalculateTextureViewSize() {
    if (maxSize == null || videoSize == null) return;
    // 根据原画面大小videoSize计算在maxSize空间内的最大缩放大小
    int tmp1 = videoSize.second * maxSize.first / videoSize.first;
    // 横向最大不会超出
    if (maxSize.second > tmp1) surfaceSize = new Pair<>(maxSize.first, tmp1);
      // 竖向最大不会超出
    else surfaceSize = new Pair<>(videoSize.first * maxSize.second / videoSize.second, maxSize.second);
    // 更新大小
    ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
    layoutParams.width = surfaceSize.first;
    layoutParams.height = surfaceSize.second;
    textureView.setLayoutParams(layoutParams);
  }

  // 检查画面是否超出
  private void checkSizeAndSite() {
    // 碎碎念，感谢 波瑠卡 的关爱，今天一家四口一起去医院进年货去了，每人提了一袋子(´；ω；`)
    if (isClosed) return;
    if (smallView != null) AppData.uiHandler.post(smallView::checkSizeAndSite);
    mainHandler.postDelayed(this::checkSizeAndSite, 1000);
  }

  // 设置视频区域触摸监听
  @SuppressLint("ClickableViewAccessibility")
  private void setTouchListener() {
    textureView.setOnTouchListener((view, event) -> {
      if (surfaceSize == null) return true;
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
        int i = event.getActionIndex();
        pointerDownTime[i] = event.getEventTime();
        createTouchPacket(event, MotionEvent.ACTION_DOWN, i);
      } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) createTouchPacket(event, MotionEvent.ACTION_UP, event.getActionIndex());
      else for (int i = 0; i < event.getPointerCount(); i++) createTouchPacket(event, MotionEvent.ACTION_MOVE, i);
      return true;
    });
  }

  private final int[] pointerList = new int[20];
  private final long[] pointerDownTime = new long[10];

  private void createTouchPacket(MotionEvent event, int action, int i) {
    int offsetTime = (int) (event.getEventTime() - pointerDownTime[i]);
    int x = (int) event.getX(i);
    int y = (int) event.getY(i);
    int p = event.getPointerId(i);
    if (action == MotionEvent.ACTION_MOVE) {
      // 减少发送小范围移动(小于4的圆内不做处理)
      int flipY = pointerList[10 + p] - y;
      if (flipY > -4 && flipY < 4) {
        int flipX = pointerList[p] - x;
        if (flipX > -4 && flipX < 4) return;
      }
    }
    pointerList[p] = x;
    pointerList[10 + p] = y;
    handleAction("writeByteBuffer", ControlPacket.createTouchEvent(action, p, (float) x / surfaceSize.first, (float) y / surfaceSize.second, offsetTime), 0);
  }


  public void release(String err) {
    try {
      for (Thread thread : workThreads) thread.interrupt();
      decodec.stop();
      decodec.release();
    } catch (Exception ignored) {
    }
  }

  @Override
  public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
    // 初始化
    if (this.surfaceTexture == null) {
      this.surfaceTexture = surfaceTexture;
    } else textureView.setSurfaceTexture(this.surfaceTexture);
  }

  @Override
  public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
  }

  @Override
  public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
    return false;
  }

  @Override
  public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
  }

}
