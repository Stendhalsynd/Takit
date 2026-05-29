package com.stendhalsynd.takit.overlay;

import android.app.Service;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.stendhalsynd.takit.R;
import com.stendhalsynd.takit.capture.CaptureActions;
import com.stendhalsynd.takit.capture.CaptureActivity;
import com.stendhalsynd.takit.capture.CaptureNotification;
import com.stendhalsynd.takit.storage.FolderListDirection;
import com.stendhalsynd.takit.storage.FolderListItem;
import com.stendhalsynd.takit.storage.FolderListRules;
import com.stendhalsynd.takit.storage.FolderListSort;
import com.stendhalsynd.takit.storage.FolderRepository;
import com.stendhalsynd.takit.storage.GalleryFolder;

import java.util.List;
import java.util.Locale;

public class OverlayBubbleService extends Service {
    public static final String ACTION_UPDATE_SETTINGS = "com.stendhalsynd.takit.action.UPDATE_OVERLAY_SETTINGS";

    private static final int NOTIFICATION_ID = 4201;
    private static final long SCREENSHOT_BUBBLE_HIDE_MS = 5500L;
    private static final int COLOR_TEXT = 0xFF202033;
    private static final int COLOR_MUTED = 0xFF7A7687;
    private static final int COLOR_LAVENDER = 0xFF9B6BE8;
    private static final int COLOR_LAVENDER_SOFT = 0xFFF2ECFF;
    private static final int COLOR_MINT_SOFT = 0xFFDDF6F2;
    private static final int COLOR_BLUE_SOFT = 0xFFE7EEFF;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_LINE = 0xFFEFEAF2;
    private static final int BUBBLE_SIZE_DP = 44;
    private static final int DISMISS_CONTAINER_DP = 160;
    private static final int DISMISS_CIRCLE_DP = 104;
    private static final float DISMISS_SELECTED_SCALE = 1.12f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private OverlaySettingsRepository overlaySettingsRepository;
    private ImageView bubbleView;
    private View miniPanelScrimView;
    private LinearLayout miniPanelView;
    private FrameLayout dismissTargetView;
    private TextView dismissTargetCircleView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams miniPanelParams;
    private WindowManager.LayoutParams dismissParams;
    private float touchStartX;
    private float touchStartY;
    private int initialX;
    private int initialY;
    private boolean moved;
    private boolean miniPanelAttached;
    private boolean dismissAttached;
    private String miniFolderQuery = "";
    private ValueAnimator bubbleAnimator;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Takit 버블 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        overlaySettingsRepository = new OverlaySettingsRepository(this);
        startBubbleForeground();
        windowManager = getSystemService(WindowManager.class);
        bubbleView = buildBubbleView();
        bubbleParams = new WindowManager.LayoutParams(
                dp(BUBBLE_SIZE_DP),
                dp(BUBBLE_SIZE_DP),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = Math.max(0, screenWidth() - dp(66));
        bubbleParams.y = dp(120);
        applyOverlaySettings();
        windowManager.addView(bubbleView, bubbleParams);
        overlaySettingsRepository.saveBubbleActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_UPDATE_SETTINGS.equals(intent.getAction())) {
            applyOverlaySettings();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (bubbleAnimator != null) {
            bubbleAnimator.cancel();
        }
        hideMiniPanel(false);
        hideDismissTarget();
        if (windowManager != null && bubbleView != null) {
            windowManager.removeView(bubbleView);
        }
        if (overlaySettingsRepository != null) {
            overlaySettingsRepository.saveBubbleActive(false);
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

    private ImageView buildBubbleView() {
        ImageView bubble = new BubbleImageView(this);
        bubble.setImageResource(R.drawable.takit_bubble_icon);
        bubble.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bubble.setBackground(circle(0xFF9B6BE8));
        bubble.setClipToOutline(true);
        bubble.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        bubble.setOnClickListener(view -> toggleMiniPanel());
        bubble.setOnTouchListener(this::handleBubbleTouch);
        return bubble;
    }

    private void applyOverlaySettings() {
        if (bubbleView != null && overlaySettingsRepository != null) {
            bubbleView.setAlpha(overlaySettingsRepository.getBubbleAlpha());
        }
    }

    private boolean handleBubbleTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                hideMiniPanel(false);
                moved = false;
                initialX = bubbleParams.x;
                initialY = bubbleParams.y;
                touchStartX = event.getRawX();
                touchStartY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                int deltaX = Math.round(event.getRawX() - touchStartX);
                int deltaY = Math.round(event.getRawY() - touchStartY);
                if (Math.abs(deltaX) > dp(4) || Math.abs(deltaY) > dp(4)) {
                    moved = true;
                    showDismissTarget();
                    bubbleParams.x = clamp(initialX + deltaX, 0, Math.max(0, screenWidth() - dp(BUBBLE_SIZE_DP)));
                    bubbleParams.y = clamp(initialY + deltaY, 0, Math.max(0, screenHeight() - dp(70)));
                    windowManager.updateViewLayout(bubbleView, bubbleParams);
                    updateDismissTargetState();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    view.performClick();
                } else if (isBubbleInsideDismissTarget()) {
                    overlaySettingsRepository.saveBubbleActive(false);
                    stopSelf();
                } else {
                    hideDismissTarget();
                    snapBubbleToEdge();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                hideDismissTarget();
                snapBubbleToEdge();
                return true;
            default:
                return false;
        }
    }

    private void toggleMiniPanel() {
        if (miniPanelAttached) {
            hideMiniPanel();
        } else {
            showMiniPanel();
        }
    }

    private void showMiniPanel() {
        hideDismissTarget();
        if (miniPanelAttached) {
            return;
        }
        animateBubbleTo((screenWidth() - dp(BUBBLE_SIZE_DP)) / 2, dp(28), 180L);
        miniPanelScrimView = buildMiniPanelScrim();
        WindowManager.LayoutParams miniPanelScrimParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        miniPanelScrimParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(miniPanelScrimView, miniPanelScrimParams);

        miniPanelView = buildMiniPanel();
        miniPanelParams = new WindowManager.LayoutParams(
                Math.min(screenWidth() - dp(36), dp(360)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        miniPanelParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        miniPanelParams.y = dp(72);
        miniPanelView.setAlpha(0f);
        miniPanelView.setScaleX(0.92f);
        miniPanelView.setScaleY(0.92f);
        windowManager.addView(miniPanelView, miniPanelParams);
        miniPanelAttached = true;
        bubbleView.animate()
                .alpha(0.2f)
                .setDuration(140L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        miniPanelView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void hideMiniPanel() {
        hideMiniPanel(true);
    }

    private void hideMiniPanel(boolean restoreBubble) {
        if (miniPanelAttached && windowManager != null && miniPanelView != null) {
            windowManager.removeView(miniPanelView);
        }
        if (miniPanelAttached && windowManager != null && miniPanelScrimView != null) {
            windowManager.removeView(miniPanelScrimView);
        }
        miniPanelAttached = false;
        miniPanelView = null;
        miniPanelScrimView = null;
        if (bubbleView != null && overlaySettingsRepository != null) {
            bubbleView.animate().alpha(overlaySettingsRepository.getBubbleAlpha()).setDuration(120L).start();
        }
        if (restoreBubble && bubbleView != null && bubbleParams != null) {
            snapBubbleToEdge();
        }
    }

    private View buildMiniPanelScrim() {
        View scrim = new View(this);
        scrim.setBackgroundColor(Color.TRANSPARENT);
        scrim.setOnClickListener(v -> hideMiniPanel());
        return scrim;
    }

    private LinearLayout buildMiniPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setFocusableInTouchMode(true);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.setBackground(rounded(COLOR_SURFACE, dp(18), 0x22000000));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Takit", 18, Typeface.BOLD, COLOR_TEXT);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(titleRow);

        LinearLayout actionRow = horizontal();
        Button camera = pastelButton("사진 촬영", COLOR_MINT_SOFT, 0xFF2C9A92);
        camera.setOnClickListener(v -> launchCapture(CaptureActions.ACTION_CAMERA));
        actionRow.addView(camera, weightParams());
        Button screenshot = pastelButton("스크린샷", COLOR_BLUE_SOFT, 0xFF486A9F);
        screenshot.setOnClickListener(v -> launchCapture(CaptureActions.ACTION_SCREENSHOT));
        actionRow.addView(screenshot, weightParams());
        panel.addView(actionRow);

        TextView folderTitle = text("저장 폴더", 14, Typeface.BOLD, COLOR_TEXT);
        folderTitle.setPadding(0, dp(12), 0, dp(6));
        panel.addView(folderTitle);

        EditText search = new EditText(this);
        search.setHint("DCIM 폴더 검색");
        search.setSingleLine(true);
        search.setTextSize(13);
        search.setText(miniFolderQuery);
        search.setBackground(rounded(0xFFFCFAFF, dp(12), COLOR_LINE));
        search.setPadding(dp(12), 0, dp(12), 0);
        search.clearFocus();
        panel.addView(search, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout folderList = new LinearLayout(this);
        folderList.setOrientation(LinearLayout.VERTICAL);
        panel.addView(folderList);
        renderMiniFolders(folderList, miniFolderQuery);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                miniFolderQuery = editable.toString();
                renderMiniFolders(folderList, miniFolderQuery);
            }
        });
        panel.requestFocus();
        return panel;
    }

    private void renderMiniFolders(LinearLayout folderList, String query) {
        folderList.removeAllViews();
        FolderRepository repository = new FolderRepository(this);
        List<FolderListItem> folders = FolderListRules.page(
                FolderListRules.sort(
                        FolderListRules.filter(repository.getFolderItems(), query),
                        FolderListSort.NAME,
                        FolderListDirection.ASCENDING
                ),
                0,
                5
        );
        if (folders.isEmpty()) {
            TextView empty = text("검색 결과가 없습니다.", 13, Typeface.NORMAL, COLOR_MUTED);
            empty.setPadding(dp(4), dp(12), dp(4), dp(8));
            folderList.addView(empty);
            return;
        }
        for (FolderListItem item : folders) {
            folderList.addView(miniFolderRow(repository, item));
        }
    }

    private View miniFolderRow(FolderRepository repository, FolderListItem item) {
        GalleryFolder folder = item.getFolder();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), 0, dp(2));

        TextView icon = text("□", 18, Typeface.BOLD, COLOR_LAVENDER);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(COLOR_LAVENDER_SOFT, dp(10), 0));
        row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(folder.getDisplayName(), 13, Typeface.BOLD, COLOR_TEXT));
        copy.addView(text(folder.getRelativePath(), 11, Typeface.NORMAL, COLOR_MUTED));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(10), 0, dp(8), 0);
        row.addView(copy, copyParams);

        Button select = smallButton("선택");
        select.setOnClickListener(v -> {
            repository.saveSelectedFolder(folder);
            Toast.makeText(this, String.format(Locale.KOREA, "%s 선택됨", folder.getDisplayName()), Toast.LENGTH_SHORT).show();
            hideMiniPanel();
        });
        row.addView(select, new LinearLayout.LayoutParams(dp(58), dp(34)));
        return row;
    }

    private void launchCapture(String action) {
        hideMiniPanel();
        if (CaptureActions.ACTION_SCREENSHOT.equals(action)) {
            bubbleView.setVisibility(View.INVISIBLE);
            handler.postDelayed(() -> {
                if (bubbleView != null) {
                    bubbleView.setVisibility(View.VISIBLE);
                }
            }, SCREENSHOT_BUBBLE_HIDE_MS);
        }
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void showDismissTarget() {
        if (dismissAttached) {
            return;
        }
        int containerSize = dp(DISMISS_CONTAINER_DP);
        int circleSize = dp(DISMISS_CIRCLE_DP);
        if (!OverlayDragRules.hasUnclippedScaledCircle(containerSize, circleSize, DISMISS_SELECTED_SCALE)) {
            containerSize = Math.round(circleSize * DISMISS_SELECTED_SCALE);
        }
        dismissTargetView = new FrameLayout(this);
        dismissTargetView.setClipChildren(false);
        dismissTargetView.setClipToPadding(false);
        dismissTargetCircleView = text("끄기", 14, Typeface.BOLD, Color.WHITE);
        dismissTargetCircleView.setGravity(Gravity.CENTER);
        dismissTargetCircleView.setBackground(circle(0xCC9B6BE8));
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER);
        dismissTargetView.addView(dismissTargetCircleView, circleParams);
        dismissParams = new WindowManager.LayoutParams(
                containerSize,
                containerSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        dismissParams.gravity = Gravity.TOP | Gravity.START;
        dismissParams.x = (screenWidth() - containerSize) / 2;
        dismissParams.y = screenHeight() - dp(108) - (containerSize / 2);
        windowManager.addView(dismissTargetView, dismissParams);
        dismissAttached = true;
    }

    private void hideDismissTarget() {
        if (dismissAttached && windowManager != null && dismissTargetView != null) {
            windowManager.removeView(dismissTargetView);
        }
        dismissAttached = false;
        dismissTargetView = null;
        dismissTargetCircleView = null;
    }

    private void updateDismissTargetState() {
        if (dismissTargetCircleView == null) {
            return;
        }
        boolean inside = isBubbleInsideDismissTarget();
        dismissTargetCircleView.setScaleX(inside ? DISMISS_SELECTED_SCALE : 1f);
        dismissTargetCircleView.setScaleY(inside ? DISMISS_SELECTED_SCALE : 1f);
        dismissTargetCircleView.setBackground(circle(inside ? 0xEE8F55DF : 0xCC9B6BE8));
    }

    private boolean isBubbleInsideDismissTarget() {
        if (dismissParams == null) {
            return false;
        }
        float bubbleCenterX = bubbleParams.x + (dp(BUBBLE_SIZE_DP) / 2f);
        float bubbleCenterY = bubbleParams.y + (dp(BUBBLE_SIZE_DP) / 2f);
        float targetCenterX = dismissParams.x + (dismissParams.width / 2f);
        float targetCenterY = dismissParams.y + (dismissParams.height / 2f);
        return OverlayDragRules.isInsideDismissCircle(
                bubbleCenterX,
                bubbleCenterY,
                targetCenterX,
                targetCenterY,
                dp(DISMISS_CIRCLE_DP) / 2f
        );
    }

    private void snapBubbleToEdge() {
        int targetX = bubbleParams.x < screenWidth() / 2 ? dp(12) : Math.max(0, screenWidth() - dp(BUBBLE_SIZE_DP + 12));
        int targetY = clamp(bubbleParams.y, dp(24), Math.max(dp(24), screenHeight() - dp(88)));
        animateBubbleTo(targetX, targetY, 180L);
    }

    private void animateBubbleTo(int targetX, int targetY, long durationMs) {
        if (bubbleAnimator != null) {
            bubbleAnimator.cancel();
        }
        int startX = bubbleParams.x;
        int startY = bubbleParams.y;
        bubbleAnimator = ValueAnimator.ofFloat(0f, 1f);
        bubbleAnimator.setDuration(durationMs);
        bubbleAnimator.setInterpolator(new DecelerateInterpolator());
        bubbleAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            bubbleParams.x = Math.round(startX + (targetX - startX) * fraction);
            bubbleParams.y = Math.round(startY + (targetY - startY) * fraction);
            if (windowManager != null && bubbleView != null) {
                windowManager.updateViewLayout(bubbleView, bubbleParams);
            }
        });
        bubbleAnimator.start();
    }

    private Button pastelButton(String label, int background, int textColor) {
        Button button = baseButton(label);
        button.setTextColor(textColor);
        button.setBackground(rounded(background, dp(14), 0));
        return button;
    }

    private Button smallButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(11);
        button.setTextColor(COLOR_LAVENDER);
        button.setBackground(rounded(COLOR_LAVENDER_SOFT, dp(9), 0));
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private Button baseButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private TextView text(String value, int sp, int style, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        return view;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        return layout;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(dp(5), 0, dp(5), 0);
        return params;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(radius);
        drawable.setColor(color);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int screenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class BubbleImageView extends ImageView {
        BubbleImageView(Context context) {
            super(context);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
