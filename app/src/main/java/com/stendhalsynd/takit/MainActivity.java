package com.stendhalsynd.takit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.stendhalsynd.takit.capture.CaptureActions;
import com.stendhalsynd.takit.capture.CaptureActivity;
import com.stendhalsynd.takit.overlay.OverlayBubbleService;
import com.stendhalsynd.takit.storage.FolderRepository;
import com.stendhalsynd.takit.storage.GalleryFolder;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    public static final String ACTION_CHOOSE_FOLDER = "com.stendhalsynd.takit.action.CHOOSE_FOLDER";

    private static final int REQUEST_READ_MEDIA = 2001;
    private static final int REQUEST_NOTIFICATIONS = 2002;

    private FolderRepository folderRepository;
    private Spinner folderSpinner;
    private TextView selectedFolderView;
    private EditText newFolderInput;
    private List<GalleryFolder> currentFolders = new ArrayList<>();
    private boolean updatingSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        folderRepository = new FolderRepository(this);
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        root.setBackgroundColor(0xFFF7FAF8);
        scrollView.addView(root);

        TextView title = text("Takit", 32, Typeface.BOLD);
        title.setTextColor(0xFF153E3B);
        root.addView(title);

        TextView subtitle = text("저장 폴더를 고르고 버블에서 바로 촬영합니다.", 15, Typeface.NORMAL);
        subtitle.setTextColor(0xFF52645F);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle);

        selectedFolderView = text("", 18, Typeface.BOLD);
        selectedFolderView.setTextColor(0xFF0D3B36);
        selectedFolderView.setPadding(0, 0, 0, dp(12));
        root.addView(selectedFolderView);

        folderSpinner = new Spinner(this);
        folderSpinner.setPadding(0, 0, 0, dp(16));
        folderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!updatingSpinner && position >= 0 && position < currentFolders.size()) {
                    folderRepository.saveSelectedFolder(currentFolders.get(position));
                    updateSelectedFolderLabel();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        root.addView(folderSpinner);

        newFolderInput = new EditText(this);
        newFolderInput.setHint("새 갤러리 폴더 이름");
        newFolderInput.setSingleLine(true);
        root.addView(newFolderInput);

        Button createFolder = button("폴더 만들기");
        createFolder.setOnClickListener(v -> createFolder());
        root.addView(createFolder);

        Button importFolders = button("기존 사진 폴더 가져오기");
        importFolders.setOnClickListener(v -> ensureReadPermissionThenImport());
        root.addView(importFolders);

        root.addView(sectionGap());

        Button startOverlay = button("버블 켜기");
        startOverlay.setOnClickListener(v -> startOverlayBubble());
        root.addView(startOverlay);

        Button stopOverlay = button("버블 끄기");
        stopOverlay.setOnClickListener(v -> stopService(new Intent(this, OverlayBubbleService.class)));
        root.addView(stopOverlay);

        root.addView(sectionGap());

        Button camera = button("사진 촬영");
        camera.setOnClickListener(v -> launchCapture(CaptureActions.ACTION_CAMERA));
        root.addView(camera);

        Button screenshot = button("스크린샷");
        screenshot.setOnClickListener(v -> launchCapture(CaptureActions.ACTION_SCREENSHOT));
        root.addView(screenshot);

        return scrollView;
    }

    private void refreshFolders() {
        if (folderSpinner == null) {
            return;
        }
        updatingSpinner = true;
        currentFolders = folderRepository.getFolders();
        List<String> labels = new ArrayList<>();
        GalleryFolder selected = folderRepository.getSelectedFolder();
        int selectedIndex = 0;
        for (int i = 0; i < currentFolders.size(); i++) {
            GalleryFolder folder = currentFolders.get(i);
            labels.add(folder.getDisplayName() + "  -  " + folder.getRelativePath());
            if (folder.equals(selected)) {
                selectedIndex = i;
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        folderSpinner.setAdapter(adapter);
        folderSpinner.setSelection(selectedIndex);
        updatingSpinner = false;
        updateSelectedFolderLabel();
    }

    private void updateSelectedFolderLabel() {
        GalleryFolder selected = folderRepository.getSelectedFolder();
        selectedFolderView.setText(getString(R.string.selected_folder_format, selected.getRelativePath()));
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
        Intent service = new Intent(this, OverlayBubbleService.class);
        startForegroundService(service);
    }

    private void launchCapture(String action) {
        requestNotificationPermissionIfNeeded();
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.setAction(action);
        startActivity(intent);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null && ACTION_CHOOSE_FOLDER.equals(intent.getAction())) {
            showFolderChooserDialog();
        }
    }

    private void showFolderChooserDialog() {
        refreshFolders();
        List<String> labels = new ArrayList<>();
        for (GalleryFolder folder : currentFolders) {
            labels.add(folder.getDisplayName() + "\n" + folder.getRelativePath());
        }
        new AlertDialog.Builder(this)
                .setTitle("저장 폴더 선택")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which >= 0 && which < currentFolders.size()) {
                        folderRepository.saveSelectedFolder(currentFolders.get(which));
                        refreshFolders();
                        Toast.makeText(this, currentFolders.get(which).getDisplayName() + " 선택됨", Toast.LENGTH_SHORT).show();
                    }
                })
                .setPositiveButton("기존 폴더 가져오기", (dialog, which) -> ensureReadPermissionThenImport())
                .setNegativeButton("닫기", null)
                .show();
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

    private TextView text(String value, int sp, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private View sectionGap() {
        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, dp(14)));
        return gap;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
