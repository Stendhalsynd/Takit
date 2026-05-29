package com.stendhalsynd.takit.capture;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.widget.Toast;

import com.stendhalsynd.takit.R;
import com.stendhalsynd.takit.storage.GalleryFolder;
import com.stendhalsynd.takit.storage.MediaStoreImageWriter;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenshotCaptureService extends Service {
    private static final int NOTIFICATION_ID = 4202;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private MediaProjection.Callback projectionCallback;
    private GalleryFolder targetFolder;
    private boolean completed;
    private boolean released;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startCaptureForeground();
        if (intent == null || !intent.hasExtra(CaptureActions.EXTRA_RESULT_CODE)) {
            fail("스크린샷 권한 데이터가 없습니다.");
            return START_NOT_STICKY;
        }
        targetFolder = GalleryFolder.fromRelativePath(intent.getStringExtra(CaptureActions.EXTRA_FOLDER_PATH));
        int resultCode = intent.getIntExtra(CaptureActions.EXTRA_RESULT_CODE, 0);
        Intent resultData = intent.getParcelableExtra(CaptureActions.EXTRA_RESULT_DATA);
        beginCapture(resultCode, resultData);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        releaseCapture();
        super.onDestroy();
    }

    private void startCaptureForeground() {
        startForeground(
                NOTIFICATION_ID,
                CaptureNotification.build(
                        this,
                        getString(R.string.screenshot_notification_title),
                        "선택한 폴더에 화면을 저장하는 중"
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        );
    }

    private void beginCapture(int resultCode, Intent resultData) {
        try {
            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            mediaProjection = manager.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) {
                fail("스크린샷 세션을 시작할 수 없습니다.");
                return;
            }
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = Math.max(1, metrics.widthPixels);
            int height = Math.max(1, metrics.heightPixels);
            int density = metrics.densityDpi;
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    releaseCapture();
                }
            };
            mediaProjection.registerCallback(projectionCallback, handler);
            imageReader.setOnImageAvailableListener(reader -> captureOneFrame(reader, width, height), handler);
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "TakitScreenshot",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    handler
            );
            handler.postDelayed(() -> {
                if (!completed) {
                    fail("스크린샷 프레임을 받지 못했습니다.");
                }
            }, 4000L);
        } catch (RuntimeException error) {
            fail("스크린샷 저장에 실패했습니다: " + error.getMessage());
        }
    }

    private void captureOneFrame(ImageReader reader, int width, int height) {
        if (completed) {
            return;
        }
        Image image = reader.acquireLatestImage();
        if (image == null) {
            return;
        }
        completed = true;
        try (Image autoClosed = image) {
            Image.Plane plane = autoClosed.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = Math.max(0, rowStride - pixelStride * width);
            Bitmap fullBitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            fullBitmap.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(fullBitmap, 0, 0, width, height);
            MediaStoreImageWriter.savePng(getContentResolver(), targetFolder, cropped, "TAKIT_SCREENSHOT");
            cropped.recycle();
            fullBitmap.recycle();
            Toast.makeText(this, "스크린샷이 선택한 폴더에 저장됐습니다.", Toast.LENGTH_SHORT).show();
        } catch (IOException | RuntimeException error) {
            Toast.makeText(this, "스크린샷 저장 실패: " + error.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            stopSelf();
        }
    }

    private void fail(String message) {
        completed = true;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        stopSelf();
    }

    private void releaseCapture() {
        if (released) {
            return;
        }
        released = true;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            if (projectionCallback != null) {
                mediaProjection.unregisterCallback(projectionCallback);
            }
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
}
