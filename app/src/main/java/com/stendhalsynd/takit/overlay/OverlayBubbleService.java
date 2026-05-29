package com.stendhalsynd.takit.overlay;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.stendhalsynd.takit.R;
import com.stendhalsynd.takit.capture.CaptureActions;
import com.stendhalsynd.takit.capture.CaptureActivity;
import com.stendhalsynd.takit.capture.CaptureNotification;
import com.stendhalsynd.takit.storage.FolderRepository;

public class OverlayBubbleService extends Service {
    private static final int NOTIFICATION_ID = 4201;

    private WindowManager windowManager;
    private LinearLayout bubbleView;
    private WindowManager.LayoutParams layoutParams;
    private LinearLayout actionPanel;
    private float touchStartX;
    private float touchStartY;
    private int initialX;
    private int initialY;
    private boolean moved;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Takit 버블 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        startBubbleForeground();
        windowManager = getSystemService(WindowManager.class);
        bubbleView = buildBubbleView();
        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.END;
        layoutParams.x = dp(18);
        layoutParams.y = dp(120);
        windowManager.addView(bubbleView, layoutParams);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (windowManager != null && bubbleView != null) {
            windowManager.removeView(bubbleView);
        }
        super.onDestroy();
    }

    private void startBubbleForeground() {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    CaptureNotification.build(
                            this,
                            getString(R.string.overlay_notification_title),
                            "스크린샷과 사진 촬영 바로가기를 표시 중"
                    ),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(
                    NOTIFICATION_ID,
                    CaptureNotification.build(
                            this,
                            getString(R.string.overlay_notification_title),
                            "스크린샷과 사진 촬영 바로가기를 표시 중"
                    )
            );
        }
    }

    private LinearLayout buildBubbleView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));

        TextView bubble = new BubbleTextView(this);
        bubble.setText("T");
        bubble.setTextColor(Color.WHITE);
        bubble.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bubble.setTextSize(22);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(circle(0xFF006D77));
        root.addView(bubble, new LinearLayout.LayoutParams(dp(56), dp(56)));

        actionPanel = new LinearLayout(this);
        actionPanel.setOrientation(LinearLayout.VERTICAL);
        actionPanel.setVisibility(View.GONE);
        actionPanel.setPadding(dp(8), 0, 0, 0);
        root.addView(actionPanel);

        TextView screenshot = actionButton("스크린샷");
        screenshot.setOnClickListener(v -> launchCapture(CaptureActions.ACTION_SCREENSHOT));
        actionPanel.addView(screenshot);

        TextView camera = actionButton("사진");
        camera.setOnClickListener(v -> launchCapture(CaptureActions.ACTION_CAMERA));
        actionPanel.addView(camera);

        TextView folder = actionButton("폴더");
        folder.setOnClickListener(v -> showCurrentFolder());
        actionPanel.addView(folder);

        bubble.setOnClickListener(view -> togglePanel());
        bubble.setOnTouchListener(this::handleBubbleTouch);
        return root;
    }

    private boolean handleBubbleTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                moved = false;
                initialX = layoutParams.x;
                initialY = layoutParams.y;
                touchStartX = event.getRawX();
                touchStartY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                int deltaX = Math.round(touchStartX - event.getRawX());
                int deltaY = Math.round(event.getRawY() - touchStartY);
                if (Math.abs(deltaX) > dp(4) || Math.abs(deltaY) > dp(4)) {
                    moved = true;
                    layoutParams.x = Math.max(0, initialX + deltaX);
                    layoutParams.y = Math.max(0, initialY + deltaY);
                    windowManager.updateViewLayout(bubbleView, layoutParams);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    view.performClick();
                }
                return true;
            default:
                return false;
        }
    }

    private void togglePanel() {
        actionPanel.setVisibility(actionPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        windowManager.updateViewLayout(bubbleView, layoutParams);
    }

    private void launchCapture(String action) {
        actionPanel.setVisibility(View.GONE);
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void showCurrentFolder() {
        String path = new FolderRepository(this).getSelectedFolder().getRelativePath();
        Toast.makeText(this, path, Toast.LENGTH_SHORT).show();
    }

    private TextView actionButton(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(0xFF153E3B);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setBackground(rounded(0xFFFFFFFF));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(96), dp(42));
        params.setMargins(0, 0, 0, dp(6));
        view.setLayoutParams(params);
        return view;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(10));
        drawable.setColor(color);
        drawable.setStroke(dp(1), 0x33000000);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class BubbleTextView extends TextView {
        BubbleTextView(Context context) {
            super(context);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
