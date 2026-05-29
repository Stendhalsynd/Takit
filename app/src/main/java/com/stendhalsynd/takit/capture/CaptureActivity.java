package com.stendhalsynd.takit.capture;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import com.stendhalsynd.takit.storage.FolderRepository;
import com.stendhalsynd.takit.storage.GalleryFolder;
import com.stendhalsynd.takit.storage.MediaStoreImageWriter;

public class CaptureActivity extends Activity {
    private static final int REQUEST_CAMERA = 3001;
    private static final int REQUEST_SCREENSHOT = 3002;
    private static final long INITIAL_SCREENSHOT_CAPTURE_DELAY_MS = 1200L;
    private static final String STATE_CAPTURE_STARTED = "state_capture_started";
    private static final String STATE_PENDING_CAMERA_URI = "state_pending_camera_uri";

    private FolderRepository folderRepository;
    private Uri pendingCameraUri;
    private boolean captureStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        folderRepository = new FolderRepository(this);
        if (savedInstanceState != null) {
            captureStarted = savedInstanceState.getBoolean(STATE_CAPTURE_STARTED, false);
            pendingCameraUri = savedInstanceState.getParcelable(STATE_PENDING_CAMERA_URI);
        }
        if (captureStarted) {
            return;
        }
        String action = getIntent().getAction();
        if (CaptureActions.ACTION_CAMERA.equals(action)) {
            startCameraCapture();
        } else if (CaptureActions.ACTION_SCREENSHOT.equals(action)) {
            startScreenshotConsent();
        } else {
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_CAPTURE_STARTED, captureStarted);
        outState.putParcelable(STATE_PENDING_CAMERA_URI, pendingCameraUri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERA) {
            handleCameraResult(resultCode);
            finish();
            return;
        }
        if (requestCode == REQUEST_SCREENSHOT) {
            handleScreenshotResult(resultCode, data);
            finish();
        }
    }

    private void startCameraCapture() {
        captureStarted = true;
        GalleryFolder folder = folderRepository.getSelectedFolder();
        pendingCameraUri = MediaStoreImageWriter.createCameraOutputImage(getContentResolver(), folder);
        if (pendingCameraUri == null) {
            Toast.makeText(this, "사진 저장 위치를 만들 수 없습니다.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        cameraIntent.setClipData(ClipData.newUri(getContentResolver(), "Takit camera output", pendingCameraUri));
        cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (cameraIntent.resolveActivity(getPackageManager()) == null) {
            MediaStoreImageWriter.deleteQuietly(getContentResolver(), pendingCameraUri);
            Toast.makeText(this, "사용 가능한 카메라 앱이 없습니다.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        startActivityForResult(cameraIntent, REQUEST_CAMERA);
    }

    private void handleCameraResult(int resultCode) {
        if (pendingCameraUri == null) {
            return;
        }
        if (resultCode == RESULT_OK) {
            Toast.makeText(this, "사진이 선택한 폴더에 저장됐습니다.", Toast.LENGTH_SHORT).show();
        } else {
            MediaStoreImageWriter.deleteQuietly(getContentResolver(), pendingCameraUri);
        }
        revokeUriPermission(pendingCameraUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private void startScreenshotConsent() {
        captureStarted = true;
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        Intent consentIntent;
        if (Build.VERSION.SDK_INT >= 34) {
            consentIntent = manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        } else {
            consentIntent = manager.createScreenCaptureIntent();
        }
        startActivityForResult(consentIntent, REQUEST_SCREENSHOT);
    }

    private void handleScreenshotResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "스크린샷 권한이 취소됐습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent service = new Intent(this, ScreenshotCaptureService.class);
        service.putExtra(CaptureActions.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(CaptureActions.EXTRA_RESULT_DATA, data);
        service.putExtra(CaptureActions.EXTRA_FOLDER_PATH, folderRepository.getSelectedFolder().getRelativePath());
        service.putExtra(CaptureActions.EXTRA_CAPTURE_DELAY_MS, INITIAL_SCREENSHOT_CAPTURE_DELAY_MS);
        service.putExtra(CaptureActions.EXTRA_CAPTURE_AFTER_START, true);
        startForegroundService(service);
        moveTaskToBack(true);
    }
}
