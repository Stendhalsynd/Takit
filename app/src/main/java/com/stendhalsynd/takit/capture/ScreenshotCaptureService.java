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
import com.stendhalsynd.takit.storage.FolderRepository;
import com.stendhalsynd.takit.storage.GalleryFolder;
import com.stendhalsynd.takit.storage.MediaStoreImageWriter;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenshotCaptureService extends Service {
    private static final int NOTIFICATION_ID = 4202;
    private static final long CAPTURE_TIMEOUT_MS = 4000L;

    private static volatile boolean sessionReady;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private MediaProjection.Callback projectionCallback;
    private GalleryFolder pendingTargetFolder;
    private boolean pendingCapture;
    private boolean released;
    private int captureRequestId;
    private int captureWidth;
    private int captureHeight;

    public static boolean isSessionReady() {
        return sessionReady;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (CaptureActions.ACTION_SCREENSHOT_CAPTURE_NOW.equals(action)) {
            requestCaptureFromActiveSession(intent);
            return START_NOT_STICKY;
        }
        startCaptureForeground();
        if (!intent.hasExtra(CaptureActions.EXTRA_RESULT_CODE)) {
            failAndStop("스크린샷 권한 데이터가 없습니다.");
            return START_NOT_STICKY;
        }
        int resultCode = intent.getIntExtra(CaptureActions.EXTRA_RESULT_CODE, 0);
        Intent resultData = intent.getParcelableExtra(CaptureActions.EXTRA_RESULT_DATA);
        boolean captureAfterStart = intent.getBooleanExtra(CaptureActions.EXTRA_CAPTURE_AFTER_START, true);
        long delayMs = ScreenshotSessionRules.sanitizedDelay(
                intent.getLongExtra(CaptureActions.EXTRA_CAPTURE_DELAY_MS, 0L)
        );
        beginSession(resultCode, resultData, captureAfterStart, delayMs, folderFromIntent(intent));
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseCapture();
        super.onDestroy();
    }

    private void startCaptureForeground() {
        startForeground(
                NOTIFICATION_ID,
                CaptureNotification.build(
                        this,
                        getString(R.string.screenshot_notification_title),
                        "스크린샷 세션 준비됨"
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        );
    }

    private void beginSession(
            int resultCode,
            Intent resultData,
            boolean captureAfterStart,
            long delayMs,
            GalleryFolder targetFolder
    ) {
        try {
            if (sessionReady && mediaProjection != null && imageReader != null) {
                if (captureAfterStart) {
                    requestCapture(targetFolder, delayMs);
                }
                return;
            }
            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            mediaProjection = manager.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) {
                failAndStop("스크린샷 세션을 시작할 수 없습니다.");
                return;
            }
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            captureWidth = Math.max(1, metrics.widthPixels);
            captureHeight = Math.max(1, metrics.heightPixels);
            int density = metrics.densityDpi;
            imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 3);
            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    releaseCapture();
                    stopSelf();
                }
            };
            mediaProjection.registerCallback(projectionCallback, handler);
            imageReader.setOnImageAvailableListener(this::onImageAvailable, handler);
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "TakitScreenshot",
                    captureWidth,
                    captureHeight,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    handler
            );
            sessionReady = true;
            if (captureAfterStart) {
                requestCapture(targetFolder, delayMs);
            }
        } catch (RuntimeException error) {
            failAndStop("스크린샷 저장에 실패했습니다: " + error.getMessage());
        }
    }

    private void requestCaptureFromActiveSession(Intent intent) {
        if (!sessionReady || mediaProjection == null || imageReader == null) {
            Toast.makeText(this, "스크린샷 세션이 만료되어 다시 동의가 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        long delayMs = ScreenshotSessionRules.sanitizedDelay(
                intent.getLongExtra(CaptureActions.EXTRA_CAPTURE_DELAY_MS, 0L)
        );
        requestCapture(folderFromIntent(intent), delayMs);
    }

    private void requestCapture(GalleryFolder targetFolder, long delayMs) {
        pendingCapture = false;
        pendingTargetFolder = targetFolder;
        drainLatestImage();
        int requestId = ++captureRequestId;
        handler.postDelayed(() -> {
            if (released || mediaProjection == null || imageReader == null || requestId != captureRequestId) {
                return;
            }
            pendingCapture = true;
            handler.postDelayed(() -> {
                if (pendingCapture && requestId == captureRequestId) {
                    pendingCapture = false;
                    Toast.makeText(this, "스크린샷 프레임을 받지 못했습니다.", Toast.LENGTH_LONG).show();
                }
            }, CAPTURE_TIMEOUT_MS);
        }, delayMs);
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) {
            return;
        }
        if (!pendingCapture) {
            image.close();
            return;
        }
        pendingCapture = false;
        saveImage(image);
    }

    private void saveImage(Image image) {
        GalleryFolder targetFolder = pendingTargetFolder == null
                ? new FolderRepository(this).getSelectedFolder()
                : pendingTargetFolder;
        try (Image autoClosed = image) {
            Image.Plane plane = autoClosed.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = Math.max(0, rowStride - pixelStride * captureWidth);
            Bitmap fullBitmap = Bitmap.createBitmap(
                    captureWidth + rowPadding / pixelStride,
                    captureHeight,
                    Bitmap.Config.ARGB_8888
            );
            fullBitmap.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(fullBitmap, 0, 0, captureWidth, captureHeight);
            MediaStoreImageWriter.savePng(getContentResolver(), targetFolder, cropped, "TAKIT_SCREENSHOT");
            cropped.recycle();
            fullBitmap.recycle();
            Toast.makeText(this, "스크린샷이 선택한 폴더에 저장됐습니다.", Toast.LENGTH_SHORT).show();
        } catch (IOException | RuntimeException error) {
            Toast.makeText(this, "스크린샷 저장 실패: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private GalleryFolder folderFromIntent(Intent intent) {
        String folderPath = intent.getStringExtra(CaptureActions.EXTRA_FOLDER_PATH);
        if (folderPath == null || folderPath.trim().isEmpty()) {
            return new FolderRepository(this).getSelectedFolder();
        }
        return GalleryFolder.fromRelativePath(folderPath);
    }

    private void drainLatestImage() {
        if (imageReader == null) {
            return;
        }
        Image image = imageReader.acquireLatestImage();
        if (image != null) {
            image.close();
        }
    }

    private void failAndStop(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        stopSelf();
    }

    private void releaseCapture() {
        if (released) {
            return;
        }
        released = true;
        sessionReady = false;
        pendingCapture = false;
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
