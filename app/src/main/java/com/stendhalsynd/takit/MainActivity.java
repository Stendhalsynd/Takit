package com.stendhalsynd.takit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.stendhalsynd.takit.capture.CaptureActions;
import com.stendhalsynd.takit.capture.CaptureActivity;
import com.stendhalsynd.takit.overlay.OverlayBubbleService;
import com.stendhalsynd.takit.overlay.OverlaySettings;
import com.stendhalsynd.takit.overlay.OverlaySettingsRepository;
import com.stendhalsynd.takit.storage.FolderRepository;
import com.stendhalsynd.takit.storage.GalleryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String ACTION_CHOOSE_FOLDER = "com.stendhalsynd.takit.action.CHOOSE_FOLDER";

    private static final int REQUEST_READ_MEDIA = 2001;
    private static final int REQUEST_NOTIFICATIONS = 2002;

    private FolderRepository folderRepository;
    private OverlaySettingsRepository overlaySettingsRepository;
    private TextView selectedFolderView;
    private TextView opacityValueView;
    private EditText newFolderInput;
    private LinearLayout favoritesContainer;
    private LinearLayout allFoldersContainer;
    private List<GalleryFolder> currentFolders = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        folderRepository = new FolderRepository(this);
        overlaySettingsRepository = new OverlaySettingsRepository(this);
        setContentView(buildContentView());
        refreshFolders();
        requestNotificationPermissionIfNeeded();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFolders();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_MEDIA && hasAnyGranted(grantResults)) {
            importGalleryFolders();
        }
    }

    private View buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        root.setBackgroundColor(0xFFF4F6F8);
        scrollView.addView(root);

        TextView title = text("Takit", 32, Typeface.BOLD);
        title.setTextColor(0xFF111827);
        root.addView(title);

        TextView subtitle = text("자주 쓰는 폴더로 빠르게 촬영하고 저장합니다.", 15, Typeface.NORMAL);
        subtitle.setTextColor(0xFF6B7280);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        LinearLayout selectedCard = card();
        selectedCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        selectedCard.addView(label("현재 저장 폴더"));
        selectedFolderView = text("", 18, Typeface.BOLD);
        selectedFolderView.setTextColor(0xFF111827);
        selectedFolderView.setPadding(0, dp(8), 0, 0);
        selectedCard.addView(selectedFolderView);
        root.addView(selectedCard);

        root.addView(sectionTitle("버블"));
        LinearLayout overlayCard = card();
        overlayCard.addView(label("투명도"));
        opacityValueView = text("", 14, Typeface.BOLD);
        opacityValueView.setTextColor(0xFF006D77);
        opacityValueView.setGravity(Gravity.END);
        overlayCard.addView(opacityValueView);
        SeekBar opacitySeek = new SeekBar(this);
        opacitySeek.setMax(100);
        opacitySeek.setProgress(OverlaySettings.progressFromAlpha(overlaySettingsRepository.getBubbleAlpha()));
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float alpha = OverlaySettings.alphaFromProgress(progress);
                overlaySettingsRepository.saveBubbleAlpha(alpha);
                updateOpacityLabel(alpha);
                notifyOverlaySettingsChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        overlayCard.addView(opacitySeek);
        updateOpacityLabel(overlaySettingsRepository.getBubbleAlpha());

        LinearLayout overlayButtons = horizontal();
        Button startOverlay = compactButton("버블 켜기");
        startOverlay.setOnClickListener(v -> startOverlayBubble());
        overlayButtons.addView(startOverlay, weightParams());
        Button stopOverlay = compactButton("버블 끄기");
        stopOverlay.setOnClickListener(v -> stopOverlayBubble());
        overlayButtons.addView(stopOverlay, weightParams());
        overlayCard.addView(overlayButtons);
        root.addView(overlayCard);

        root.addView(sectionTitle("빠른 실행"));
        LinearLayout captureButtons = horizontal();
        Button camera = compactButton("사진 촬영");
        camera.setOnClickListener(v -> launchCapture(CaptureActions.ACTION_CAMERA));
        captureButtons.addView(camera, weightParams());
        Button screenshot = compactButton("스크린샷");
        screenshot.setOnClickListener(v -> launchCapture(CaptureActions.ACTION_SCREENSHOT));
        captureButtons.addView(screenshot, weightParams());
        root.addView(captureButtons);

        root.addView(sectionTitle("폴더 추가"));
        LinearLayout addCard = card();
        newFolderInput = new EditText(this);
        newFolderInput.setHint("새 DCIM/Takit 폴더 이름");
        newFolderInput.setSingleLine(true);
        newFolderInput.setTextSize(16);
        addCard.addView(newFolderInput);
        Button createFolder = compactButton("폴더 만들고 선택");
        createFolder.setOnClickListener(v -> createFolder());
        addCard.addView(createFolder);
        Button importFolders = compactButton("갤러리 폴더 가져오기");
        importFolders.setOnClickListener(v -> ensureReadPermissionThenImport());
        addCard.addView(importFolders);
        root.addView(addCard);

        root.addView(sectionTitle("주로 쓰는 폴더"));
        favoritesContainer = new LinearLayout(this);
        favoritesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(favoritesContainer);

        root.addView(sectionTitle("전체 갤러리 폴더"));
        allFoldersContainer = new LinearLayout(this);
        allFoldersContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(allFoldersContainer);

        return scrollView;
    }

    private void refreshFolders() {
        if (selectedFolderView == null) {
            return;
        }
        currentFolders = folderRepository.getFolders();
        GalleryFolder selected = folderRepository.getSelectedFolder();
        selectedFolderView.setText(getString(
                R.string.selected_folder_detail_format,
                selected.getDisplayName(),
                selected.getRelativePath()
        ));
        renderFolderSection(favoritesContainer, folderRepository.getFavoriteFolders(), true);
        renderFolderSection(allFoldersContainer, currentFolders, false);
    }

    private void renderFolderSection(LinearLayout container, List<GalleryFolder> folders, boolean favoritesOnly) {
        container.removeAllViews();
        if (folders.isEmpty()) {
            TextView empty = text(favoritesOnly ? "등록된 주로 쓰는 폴더가 없습니다." : "가져온 갤러리 폴더가 없습니다.", 14, Typeface.NORMAL);
            empty.setTextColor(0xFF9CA3AF);
            empty.setPadding(0, dp(8), 0, dp(8));
            container.addView(empty);
            return;
        }
        for (GalleryFolder folder : folders) {
            container.addView(folderRow(folder));
        }
    }

    private LinearLayout folderRow(GalleryFolder folder) {
        LinearLayout row = card();
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView name = text(folder.getDisplayName(), 17, Typeface.BOLD);
        name.setTextColor(0xFF111827);
        row.addView(name);

        TextView path = text(folder.getRelativePath(), 13, Typeface.NORMAL);
        path.setTextColor(0xFF6B7280);
        path.setPadding(0, dp(4), 0, dp(10));
        row.addView(path);

        LinearLayout actions = horizontal();
        Button select = compactButton("선택");
        select.setOnClickListener(v -> {
            folderRepository.saveSelectedFolder(folder);
            refreshFolders();
            Toast.makeText(this, folder.getDisplayName() + " 선택됨", Toast.LENGTH_SHORT).show();
        });
        actions.addView(select, weightParams());

        Button favorite = compactButton(folderRepository.isFavorite(folder) ? "주로 쓰기 해제" : "주로 쓰기 등록");
        favorite.setOnClickListener(v -> {
            folderRepository.toggleFavorite(folder);
            refreshFolders();
        });
        actions.addView(favorite, weightParams());
        row.addView(actions);
        return row;
    }

    private void updateOpacityLabel(float alpha) {
        if (opacityValueView != null) {
            opacityValueView.setText(String.format(Locale.US, "%.0f%%", alpha * 100f));
        }
    }

    private void createFolder() {
        GalleryFolder folder = folderRepository.addFolder(newFolderInput.getText().toString());
        newFolderInput.setText("");
        refreshFolders();
        Toast.makeText(this, folder.getDisplayName() + " 선택됨", Toast.LENGTH_SHORT).show();
    }

    private void ensureReadPermissionThenImport() {
        if (folderRepository.canReadMediaFolders()) {
            importGalleryFolders();
            return;
        }
        requestPermissions(readMediaPermissions(), REQUEST_READ_MEDIA);
    }

    private void importGalleryFolders() {
        int imported = folderRepository.importExistingGalleryFolders();
        refreshFolders();
        Toast.makeText(this, imported + "개 갤러리 폴더를 가져왔습니다.", Toast.LENGTH_SHORT).show();
    }

    private void startOverlayBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
            Toast.makeText(this, "Takit 버블 권한을 허용해 주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        requestNotificationPermissionIfNeeded();
        startForegroundService(new Intent(this, OverlayBubbleService.class));
    }

    private void stopOverlayBubble() {
        overlaySettingsRepository.saveBubbleActive(false);
        stopService(new Intent(this, OverlayBubbleService.class));
    }

    private void notifyOverlaySettingsChanged() {
        if (!Settings.canDrawOverlays(this) || !overlaySettingsRepository.isBubbleActive()) {
            return;
        }
        Intent intent = new Intent(this, OverlayBubbleService.class);
        intent.setAction(OverlayBubbleService.ACTION_UPDATE_SETTINGS);
        startService(intent);
    }

    private void launchCapture(String action) {
        requestNotificationPermissionIfNeeded();
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.setAction(action);
        startActivity(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && ACTION_CHOOSE_FOLDER.equals(intent.getAction())) {
            showFolderChooserDialog();
        }
    }

    private void showFolderChooserDialog() {
        refreshFolders();
        List<GalleryFolder> quickFolders = folderRepository.getFavoriteFolders();
        if (quickFolders.isEmpty()) {
            quickFolders = currentFolders;
        }
        final List<GalleryFolder> dialogFolders = quickFolders;
        List<String> labels = new ArrayList<>();
        for (GalleryFolder folder : dialogFolders) {
            labels.add(folder.getDisplayName() + "\n" + folder.getRelativePath());
        }
        new AlertDialog.Builder(this)
                .setTitle("저장 폴더 빠른 선택")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which >= 0 && which < dialogFolders.size()) {
                        GalleryFolder folder = dialogFolders.get(which);
                        folderRepository.saveSelectedFolder(folder);
                        refreshFolders();
                        Toast.makeText(this, folder.getDisplayName() + " 선택됨", Toast.LENGTH_SHORT).show();
                    }
                })
                .setPositiveButton("갤러리 폴더 가져오기", (dialog, which) -> ensureReadPermissionThenImport())
                .setNegativeButton("닫기", null)
                .show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private String[] readMediaPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            return new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            };
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasAnyGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 18, Typeface.BOLD);
        view.setTextColor(0xFF111827);
        view.setPadding(0, dp(24), 0, dp(10));
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13, Typeface.BOLD);
        view.setTextColor(0xFF6B7280);
        return view;
    }

    private TextView text(String value, int sp, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        return view;
    }

    private Button compactButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(0xFF006D77, dp(8), 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(0xFFFFFFFF, dp(8), 0x11000000));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        return layout;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
