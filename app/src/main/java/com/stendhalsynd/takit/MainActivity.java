package com.stendhalsynd.takit;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.stendhalsynd.takit.storage.FolderListDirection;
import com.stendhalsynd.takit.storage.FolderListItem;
import com.stendhalsynd.takit.storage.FolderListRules;
import com.stendhalsynd.takit.storage.FolderListSort;
import com.stendhalsynd.takit.storage.FolderRepository;
import com.stendhalsynd.takit.storage.GalleryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String ACTION_CHOOSE_FOLDER = "com.stendhalsynd.takit.action.CHOOSE_FOLDER";

    private static final int REQUEST_READ_MEDIA = 2001;
    private static final int REQUEST_NOTIFICATIONS = 2002;
    private static final int FOLDER_PAGE_SIZE = 8;

    private static final String TAB_HOME = "home";
    private static final String TAB_FOLDER = "folder";
    private static final String TAB_SETTINGS = "settings";
    private static final String PREFS_FOLDER_VIEW = "takit_folder_view";
    private static final String KEY_SORT = "sort";
    private static final String KEY_DIRECTION = "direction";
    private static final String KEY_QUERY = "query";
    private static final String KEY_PAGE = "page";

    private static final int COLOR_BACKGROUND = 0xFFFFF9F2;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF202033;
    private static final int COLOR_MUTED = 0xFF7A7687;
    private static final int COLOR_LAVENDER = 0xFF9B6BE8;
    private static final int COLOR_LAVENDER_SOFT = 0xFFF2ECFF;
    private static final int COLOR_MINT_SOFT = 0xFFDDF6F2;
    private static final int COLOR_BLUE_SOFT = 0xFFE7EEFF;
    private static final int COLOR_PINK_SOFT = 0xFFFFE8F2;
    private static final int COLOR_YELLOW_SOFT = 0xFFFFF4CA;
    private static final int COLOR_LINE = 0xFFEFEAF2;

    private FolderRepository folderRepository;
    private OverlaySettingsRepository overlaySettingsRepository;
    private SharedPreferences folderViewPreferences;
    private TextView selectedFolderNameView;
    private TextView selectedFolderPathView;
    private TextView opacityValueView;
    private TextView folderSummaryView;
    private TextView pageStatusView;
    private EditText newFolderInput;
    private EditText folderSearchInput;
    private LinearLayout homeSection;
    private LinearLayout folderSection;
    private LinearLayout settingsSection;
    private LinearLayout favoritesContainer;
    private LinearLayout allFoldersContainer;
    private Button homeTab;
    private Button folderTab;
    private Button settingsTab;
    private Button sortNameButton;
    private Button sortModifiedButton;
    private Button directionButton;
    private String activeTab = TAB_HOME;
    private String folderSearchQuery = "";
    private FolderListSort folderSort = FolderListSort.NAME;
    private FolderListDirection folderDirection = FolderListDirection.ASCENDING;
    private int folderPageIndex = 0;
    private List<FolderListItem> currentFolderItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        folderRepository = new FolderRepository(this);
        overlaySettingsRepository = new OverlaySettingsRepository(this);
        folderViewPreferences = getSharedPreferences(PREFS_FOLDER_VIEW, MODE_PRIVATE);
        loadFolderViewSettings();
        setContentView(buildContentView());
        refreshFolders();
        showTab(TAB_HOME);
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
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(COLOR_BACKGROUND);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(18));
        scrollView.addView(root);
        screen.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        root.addView(buildHeader());

        homeSection = new LinearLayout(this);
        homeSection.setOrientation(LinearLayout.VERTICAL);
        homeSection.addView(buildSelectedFolderCard());
        homeSection.addView(sectionTitle("빠른 실행"));
        homeSection.addView(buildQuickActions());
        homeSection.addView(sectionTitle("자주 쓰는 폴더"));
        favoritesContainer = new LinearLayout(this);
        favoritesContainer.setOrientation(LinearLayout.VERTICAL);
        homeSection.addView(favoritesContainer);
        root.addView(homeSection);

        folderSection = new LinearLayout(this);
        folderSection.setOrientation(LinearLayout.VERTICAL);
        folderSection.addView(sectionTitle("폴더 추가"));
        folderSection.addView(buildFolderAddCard());
        folderSection.addView(sectionTitle("전체 갤러리 폴더"));
        folderSection.addView(buildFolderControls());
        allFoldersContainer = new LinearLayout(this);
        allFoldersContainer.setOrientation(LinearLayout.VERTICAL);
        folderSection.addView(allFoldersContainer);
        folderSection.addView(buildPaginationControls());
        root.addView(folderSection);

        settingsSection = new LinearLayout(this);
        settingsSection.setOrientation(LinearLayout.VERTICAL);
        settingsSection.addView(sectionTitle("버블"));
        settingsSection.addView(buildBubbleSettingsCard());
        root.addView(settingsSection);

        screen.addView(buildBottomNav());
        return screen;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(18));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Takit", 34, Typeface.BOLD, COLOR_TEXT);
        TextView subtitle = text("자주 쓰는 폴더로 빠르게 저장하고 관리해요", 14, Typeface.NORMAL, COLOR_MUTED);
        copy.addView(title);
        copy.addView(subtitle);
        header.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView profile = text("⌕", 26, Typeface.BOLD, COLOR_TEXT);
        profile.setGravity(Gravity.CENTER);
        profile.setBackground(rounded(COLOR_SURFACE, dp(18), 0));
        header.addView(profile, new LinearLayout.LayoutParams(dp(54), dp(54)));
        return header;
    }

    private View buildSelectedFolderCard() {
        LinearLayout card = card(dp(16));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = iconTile("□", COLOR_LAVENDER_SOFT, COLOR_LAVENDER);
        card.addView(icon);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("현재 저장 폴더", 12, Typeface.BOLD, COLOR_MUTED);
        selectedFolderNameView = text("", 19, Typeface.BOLD, COLOR_TEXT);
        selectedFolderPathView = text("", 13, Typeface.NORMAL, COLOR_MUTED);
        copy.addView(label);
        copy.addView(selectedFolderNameView);
        copy.addView(selectedFolderPathView);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(14), 0, dp(10), 0);
        card.addView(copy, copyParams);

        TextView chevron = text("›", 28, Typeface.NORMAL, COLOR_MUTED);
        chevron.setGravity(Gravity.CENTER);
        card.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(48)));
        card.setOnClickListener(v -> showTab(TAB_FOLDER));
        return card;
    }

    private View buildQuickActions() {
        LinearLayout row = horizontal();
        Button camera = pastelButton("▣  사진 촬영", COLOR_MINT_SOFT, 0xFF2C9A92);
        camera.setOnClickListener(v -> launchCapture(CaptureActions.ACTION_CAMERA));
        row.addView(camera, weightParams());

        Button screenshot = pastelButton("⌗  스크린샷", COLOR_BLUE_SOFT, 0xFF486A9F);
        screenshot.setOnClickListener(v -> launchCapture(CaptureActions.ACTION_SCREENSHOT));
        row.addView(screenshot, weightParams());
        return row;
    }

    private View buildFolderAddCard() {
        LinearLayout card = card(dp(14));
        card.setBackground(dashed(COLOR_SURFACE, dp(14), 0xFFD7CFD6));

        LinearLayout titleRow = horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = iconTile("+", 0xFFF7FAEE, 0xFF6F8A42);
        titleRow.addView(icon);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("새 폴더 만들기", 16, Typeface.BOLD, COLOR_TEXT));
        copy.addView(text("DCIM/Takit 폴더 이름", 12, Typeface.NORMAL, COLOR_MUTED));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(12), 0, 0, 0);
        titleRow.addView(copy, copyParams);
        card.addView(titleRow);

        newFolderInput = new EditText(this);
        newFolderInput.setHint("폴더 이름 직접 입력");
        newFolderInput.setSingleLine(true);
        newFolderInput.setTextSize(14);
        newFolderInput.setBackground(rounded(0xFFFBFBF0, dp(10), COLOR_LINE));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        inputParams.setMargins(0, dp(12), 0, 0);
        card.addView(newFolderInput, inputParams);

        LinearLayout actions = horizontal();
        Button sync = outlineButton("기존 폴더 동기화");
        sync.setOnClickListener(v -> ensureReadPermissionThenImport());
        actions.addView(sync, weightParams());
        Button create = outlineButton("폴더 만들고 선택");
        create.setOnClickListener(v -> createFolder());
        actions.addView(create, weightParams());
        card.addView(actions);
        return card;
    }

    private View buildFolderControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);

        folderSearchInput = new EditText(this);
        folderSearchInput.setHint("DCIM 폴더명 검색");
        folderSearchInput.setSingleLine(true);
        folderSearchInput.setTextSize(14);
        folderSearchInput.setText(folderSearchQuery);
        folderSearchInput.setBackground(rounded(COLOR_SURFACE, dp(14), COLOR_LINE));
        folderSearchInput.setPadding(dp(14), 0, dp(14), 0);
        folderSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                folderSearchQuery = editable.toString();
                folderPageIndex = 0;
                saveFolderViewSettings();
                refreshFolders();
            }
        });
        controls.addView(folderSearchInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        LinearLayout sortRow = horizontal();
        sortNameButton = chipButton("이름순");
        sortNameButton.setOnClickListener(v -> {
            folderSort = FolderListSort.NAME;
            folderPageIndex = 0;
            saveFolderViewSettings();
            refreshFolders();
        });
        sortRow.addView(sortNameButton, weightParams());

        sortModifiedButton = chipButton("최근 수정순");
        sortModifiedButton.setOnClickListener(v -> {
            folderSort = FolderListSort.MODIFIED;
            folderPageIndex = 0;
            saveFolderViewSettings();
            refreshFolders();
        });
        sortRow.addView(sortModifiedButton, weightParams());

        directionButton = chipButton("오름차순");
        directionButton.setOnClickListener(v -> {
            folderDirection = folderDirection == FolderListDirection.ASCENDING
                    ? FolderListDirection.DESCENDING
                    : FolderListDirection.ASCENDING;
            folderPageIndex = 0;
            saveFolderViewSettings();
            refreshFolders();
        });
        sortRow.addView(directionButton, weightParams());
        controls.addView(sortRow);

        folderSummaryView = text("", 13, Typeface.BOLD, COLOR_MUTED);
        folderSummaryView.setPadding(dp(2), dp(8), 0, dp(8));
        controls.addView(folderSummaryView);
        return controls;
    }

    private View buildPaginationControls() {
        LinearLayout pager = horizontal();
        pager.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = outlineButton("이전");
        previous.setOnClickListener(v -> {
            folderPageIndex = Math.max(0, folderPageIndex - 1);
            saveFolderViewSettings();
            refreshFolders();
        });
        pager.addView(previous, weightParams());

        pageStatusView = text("", 14, Typeface.BOLD, COLOR_TEXT);
        pageStatusView.setGravity(Gravity.CENTER);
        pager.addView(pageStatusView, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button next = outlineButton("다음");
        next.setOnClickListener(v -> {
            folderPageIndex += 1;
            saveFolderViewSettings();
            refreshFolders();
        });
        pager.addView(next, weightParams());
        return pager;
    }

    private View buildBubbleSettingsCard() {
        LinearLayout card = card(dp(16));
        card.addView(text("투명도", 13, Typeface.BOLD, COLOR_MUTED));
        opacityValueView = text("", 14, Typeface.BOLD, COLOR_LAVENDER);
        opacityValueView.setGravity(Gravity.END);
        card.addView(opacityValueView);

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
        card.addView(opacitySeek);
        updateOpacityLabel(overlaySettingsRepository.getBubbleAlpha());

        LinearLayout overlayButtons = horizontal();
        Button startOverlay = primaryButton("버블 켜기");
        startOverlay.setOnClickListener(v -> startOverlayBubble());
        overlayButtons.addView(startOverlay, weightParams());
        Button stopOverlay = primaryButton("버블 끄기");
        stopOverlay.setOnClickListener(v -> stopOverlayBubble());
        overlayButtons.addView(stopOverlay, weightParams());
        card.addView(overlayButtons);
        return card;
    }

    private View buildBottomNav() {
        LinearLayout nav = horizontal();
        nav.setPadding(dp(14), dp(8), dp(14), dp(8));
        nav.setBackground(rounded(COLOR_SURFACE, dp(24), COLOR_LINE));
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(66)
        );
        navParams.setMargins(dp(20), 0, dp(20), dp(14));
        nav.setLayoutParams(navParams);

        homeTab = navButton("홈");
        homeTab.setOnClickListener(v -> showTab(TAB_HOME));
        nav.addView(homeTab, weightParams());
        folderTab = navButton("폴더");
        folderTab.setOnClickListener(v -> showTab(TAB_FOLDER));
        nav.addView(folderTab, weightParams());
        settingsTab = navButton("설정");
        settingsTab.setOnClickListener(v -> showTab(TAB_SETTINGS));
        nav.addView(settingsTab, weightParams());
        return nav;
    }

    private void refreshFolders() {
        if (selectedFolderNameView == null) {
            return;
        }
        currentFolderItems = folderRepository.getFolderItems();
        GalleryFolder selected = folderRepository.getSelectedFolder();
        selectedFolderNameView.setText(selected.getDisplayName());
        selectedFolderPathView.setText(selected.getRelativePath());

        renderFavorites();
        renderAllFolders();
        updateSortButtons();
    }

    private void renderFavorites() {
        favoritesContainer.removeAllViews();
        List<GalleryFolder> favorites = folderRepository.getFavoriteFolders();
        if (favorites.isEmpty()) {
            favoritesContainer.addView(emptyText("등록된 주로 쓰는 폴더가 없습니다."));
            return;
        }
        LinearLayout row = horizontal();
        int count = Math.min(3, favorites.size());
        for (int i = 0; i < count; i++) {
            row.addView(favoriteCard(favorites.get(i), i), weightParams());
        }
        favoritesContainer.addView(row);
        if (favorites.size() > count) {
            TextView more = text(String.format(Locale.KOREA, "그 외 %d개는 폴더 탭에서 관리", favorites.size() - count), 12, Typeface.BOLD, COLOR_MUTED);
            more.setPadding(dp(4), dp(8), 0, 0);
            favoritesContainer.addView(more);
        }
    }

    private View favoriteCard(GalleryFolder folder, int index) {
        int[] colors = {COLOR_LAVENDER_SOFT, 0xFFFFEFE2, COLOR_PINK_SOFT};
        LinearLayout card = card(dp(12));
        card.setBackground(rounded(colors[index % colors.length], dp(14), 0));
        card.addView(text(folder.getDisplayName(), 14, Typeface.BOLD, COLOR_TEXT));
        card.addView(text(folder.getRelativePath(), 11, Typeface.NORMAL, COLOR_MUTED));
        FolderListItem item = findItem(folder);
        card.addView(text(String.format(Locale.KOREA, "%d개 항목", item == null ? 0 : item.getItemCount()), 12, Typeface.BOLD, COLOR_LAVENDER));
        card.setOnClickListener(v -> {
            folderRepository.saveSelectedFolder(folder);
            refreshFolders();
            Toast.makeText(this, String.format(Locale.KOREA, "%s 선택됨", folder.getDisplayName()), Toast.LENGTH_SHORT).show();
        });
        return card;
    }

    private void renderAllFolders() {
        allFoldersContainer.removeAllViews();
        List<FolderListItem> filtered = FolderListRules.filter(currentFolderItems, folderSearchQuery);
        List<FolderListItem> sorted = FolderListRules.sort(filtered, folderSort, folderDirection);
        int maxPageIndex = FolderListRules.maxPageIndex(sorted.size(), FOLDER_PAGE_SIZE);
        folderPageIndex = Math.max(0, Math.min(folderPageIndex, maxPageIndex));
        saveFolderViewSettings();
        List<FolderListItem> page = FolderListRules.page(sorted, folderPageIndex, FOLDER_PAGE_SIZE);

        folderSummaryView.setText(String.format(Locale.KOREA, "DCIM 기준 %d개 폴더", sorted.size()));
        pageStatusView.setText(String.format(Locale.KOREA, "%d / %d", folderPageIndex + 1, maxPageIndex + 1));
        if (page.isEmpty()) {
            allFoldersContainer.addView(emptyText("검색 결과가 없습니다."));
            return;
        }
        LinearLayout listCard = card(dp(0));
        listCard.setPadding(0, 0, 0, 0);
        for (int i = 0; i < page.size(); i++) {
            listCard.addView(folderRow(page.get(i)));
            if (i < page.size() - 1) {
                listCard.addView(divider());
            }
        }
        allFoldersContainer.addView(listCard);
    }

    private View folderRow(FolderListItem item) {
        GalleryFolder folder = item.getFolder();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));

        row.addView(iconTile("□", folderColor(folder), COLOR_LAVENDER));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(folder.getDisplayName(), 14, Typeface.BOLD, COLOR_TEXT));
        copy.addView(text(folder.getRelativePath(), 11, Typeface.NORMAL, COLOR_MUTED));
        copy.addView(text(String.format(Locale.KOREA, "%d개 항목", item.getItemCount()), 11, Typeface.BOLD, COLOR_LAVENDER));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(10), 0, dp(8), 0);
        row.addView(copy, copyParams);

        Button select = miniButton("선택", true);
        select.setOnClickListener(v -> {
            folderRepository.saveSelectedFolder(folder);
            refreshFolders();
            Toast.makeText(this, String.format(Locale.KOREA, "%s 선택됨", folder.getDisplayName()), Toast.LENGTH_SHORT).show();
        });
        row.addView(select);

        Button favorite = miniButton(folderRepository.isFavorite(folder) ? "해제" : "주로 쓰기", false);
        favorite.setOnClickListener(v -> {
            folderRepository.toggleFavorite(folder);
            refreshFolders();
        });
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(dp(74), dp(34));
        favoriteParams.setMargins(dp(6), 0, 0, 0);
        row.addView(favorite, favoriteParams);
        return row;
    }

    private void showTab(String tab) {
        activeTab = tab;
        if (homeSection == null) {
            return;
        }
        homeSection.setVisibility(TAB_HOME.equals(tab) ? View.VISIBLE : View.GONE);
        folderSection.setVisibility(TAB_FOLDER.equals(tab) ? View.VISIBLE : View.GONE);
        settingsSection.setVisibility(TAB_SETTINGS.equals(tab) ? View.VISIBLE : View.GONE);
        updateTabButton(homeTab, TAB_HOME.equals(tab));
        updateTabButton(folderTab, TAB_FOLDER.equals(tab));
        updateTabButton(settingsTab, TAB_SETTINGS.equals(tab));
    }

    private void loadFolderViewSettings() {
        folderSort = FolderListSort.fromValue(folderViewPreferences.getString(KEY_SORT, FolderListSort.NAME.getValue()));
        folderDirection = FolderListDirection.fromValue(folderViewPreferences.getString(KEY_DIRECTION, FolderListDirection.ASCENDING.getValue()));
        folderSearchQuery = folderViewPreferences.getString(KEY_QUERY, "");
        folderPageIndex = Math.max(0, folderViewPreferences.getInt(KEY_PAGE, 0));
    }

    private void saveFolderViewSettings() {
        if (folderViewPreferences == null) {
            return;
        }
        folderViewPreferences.edit()
                .putString(KEY_SORT, folderSort.getValue())
                .putString(KEY_DIRECTION, folderDirection.getValue())
                .putString(KEY_QUERY, folderSearchQuery)
                .putInt(KEY_PAGE, folderPageIndex)
                .apply();
    }

    private void updateTabButton(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setTextColor(selected ? COLOR_LAVENDER : COLOR_MUTED);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(rounded(selected ? COLOR_LAVENDER_SOFT : COLOR_SURFACE, dp(20), 0));
    }

    private void updateSortButtons() {
        if (sortNameButton == null) {
            return;
        }
        styleChip(sortNameButton, folderSort == FolderListSort.NAME);
        styleChip(sortModifiedButton, folderSort == FolderListSort.MODIFIED);
        directionButton.setText(folderDirection == FolderListDirection.ASCENDING ? "오름차순" : "내림차순");
        styleChip(directionButton, true);
    }

    private void createFolder() {
        GalleryFolder folder = folderRepository.addFolder(newFolderInput.getText().toString());
        newFolderInput.setText("");
        refreshFolders();
        Toast.makeText(this, String.format(Locale.KOREA, "%s 선택됨", folder.getDisplayName()), Toast.LENGTH_SHORT).show();
    }

    private void ensureReadPermissionThenImport() {
        if (folderRepository.canReadMediaFolders()) {
            importGalleryFolders();
            return;
        }
        requestPermissions(readMediaPermissions(), REQUEST_READ_MEDIA);
    }

    private void importGalleryFolders() {
        int synced = folderRepository.syncExistingGalleryFolders();
        refreshFolders();
        Toast.makeText(this, String.format(Locale.KOREA, "%d개 DCIM 폴더를 동기화했습니다.", synced), Toast.LENGTH_SHORT).show();
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
            showTab(TAB_FOLDER);
            Toast.makeText(this, "폴더 탭에서 저장 폴더를 선택하세요.", Toast.LENGTH_SHORT).show();
        }
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

    private FolderListItem findItem(GalleryFolder folder) {
        for (FolderListItem item : currentFolderItems) {
            if (item.getFolder().equals(folder)) {
                return item;
            }
        }
        return null;
    }

    private int folderColor(GalleryFolder folder) {
        int hash = Math.abs(folder.getRelativePath().hashCode());
        int[] colors = {COLOR_LAVENDER_SOFT, COLOR_PINK_SOFT, 0xFFFFEFE2, COLOR_MINT_SOFT, COLOR_BLUE_SOFT, COLOR_YELLOW_SOFT};
        return colors[hash % colors.length];
    }

    private void updateOpacityLabel(float alpha) {
        if (opacityValueView != null) {
            opacityValueView.setText(String.format(Locale.US, "%.0f%%", alpha * 100f));
        }
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 18, Typeface.BOLD, COLOR_TEXT);
        view.setPadding(0, dp(24), 0, dp(10));
        return view;
    }

    private TextView emptyText(String value) {
        TextView view = text(value, 14, Typeface.NORMAL, COLOR_MUTED);
        view.setPadding(dp(4), dp(10), dp(4), dp(10));
        return view;
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

    private TextView iconTile(String value, int backgroundColor, int textColor) {
        TextView icon = text(value, 22, Typeface.BOLD, textColor);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(backgroundColor, dp(12), 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
        icon.setLayoutParams(params);
        return icon;
    }

    private Button primaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(COLOR_LAVENDER, dp(14), 0));
        return button;
    }

    private Button pastelButton(String label, int background, int textColor) {
        Button button = baseButton(label);
        button.setTextColor(textColor);
        button.setBackground(rounded(background, dp(16), 0));
        return button;
    }

    private Button outlineButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(COLOR_LAVENDER);
        button.setBackground(rounded(0xFFFCFBF5, dp(10), COLOR_LINE));
        return button;
    }

    private Button chipButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private Button miniButton(String label, boolean filled) {
        Button button = baseButton(label);
        button.setTextSize(11);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setTextColor(filled ? COLOR_LAVENDER : COLOR_TEXT);
        button.setBackground(rounded(filled ? COLOR_LAVENDER_SOFT : COLOR_SURFACE, dp(9), filled ? 0 : COLOR_LINE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(54), dp(34));
        button.setLayoutParams(params);
        return button;
    }

    private Button navButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(13);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        return button;
    }

    private Button baseButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        params.setMargins(dp(4), dp(8), dp(4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void styleChip(Button button, boolean selected) {
        button.setTextColor(selected ? COLOR_LAVENDER : COLOR_MUTED);
        button.setBackground(rounded(selected ? COLOR_LAVENDER_SOFT : COLOR_SURFACE, dp(12), COLOR_LINE));
    }

    private LinearLayout card(int radius) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(COLOR_SURFACE, radius, 0x11000000));
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

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_LINE);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return divider;
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

    private GradientDrawable dashed(int color, int radius, int strokeColor) {
        GradientDrawable drawable = rounded(color, radius, 0);
        drawable.setStroke(dp(1), strokeColor, dp(6), dp(5));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
