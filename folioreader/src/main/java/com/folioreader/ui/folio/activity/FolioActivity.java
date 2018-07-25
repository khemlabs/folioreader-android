/*
 * Copyright (C) 2016 Pedro Paulo de Amorim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.folioreader.ui.folio.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.folioreader.Config;
import com.folioreader.Constants;
import com.folioreader.FolioReader;
import com.folioreader.R;
import com.folioreader.model.HighlightImpl;
import com.folioreader.model.ReadPosition;
import com.folioreader.model.event.MediaOverlayPlayPauseEvent;
import com.folioreader.ui.folio.adapter.FolioPageFragmentAdapter;
import com.folioreader.ui.folio.fragment.FolioPageFragment;
import com.folioreader.ui.folio.fragment.MediaControllerFragment;
import com.folioreader.ui.folio.presenter.MainMvpView;
import com.folioreader.ui.folio.presenter.MainPresenter;
import com.folioreader.util.AppUtil;
import com.folioreader.util.FileUtil;
import com.folioreader.util.UiUtil;
import com.folioreader.view.ConfigBottomSheetDialogFragment;
import com.folioreader.view.DirectionalViewpager;
import com.folioreader.view.FolioAppBarLayout;
import com.folioreader.view.MediaControllerCallback;

import org.greenrobot.eventbus.EventBus;
import org.readium.r2_streamer.model.container.Container;
import org.readium.r2_streamer.model.container.EpubContainer;
import org.readium.r2_streamer.model.publication.EpubPublication;
import org.readium.r2_streamer.model.publication.link.Link;
import org.readium.r2_streamer.server.EpubServer;
import org.readium.r2_streamer.server.EpubServerSingleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.folioreader.Constants.CHAPTER_SELECTED;
import static com.folioreader.Constants.HIGHLIGHT_SELECTED;
import static com.folioreader.Constants.SELECTED_CHAPTER_POSITION;
import static com.folioreader.Constants.TYPE;

public class FolioActivity
        extends AppCompatActivity
        implements FolioActivityCallback, MainMvpView, MediaControllerCallback,
        View.OnSystemUiVisibilityChangeListener {

    private static final String LOG_TAG = "FolioActivity";

    public static final String INTENT_EPUB_SOURCE_PATH = "com.folioreader.epub_asset_path";
    public static final String INTENT_EPUB_SOURCE_TYPE = "epub_source_type";
    public static final String INTENT_HIGHLIGHTS_LIST = "highlight_list";
    public static final String EXTRA_READ_POSITION = "com.folioreader.extra.READ_POSITION";
    private static final String BUNDLE_READ_POSITION_CONFIG_CHANGE = "BUNDLE_READ_POSITION_CONFIG_CHANGE";
    private static final String BUNDLE_DISTRACTION_FREE_MODE = "BUNDLE_DISTRACTION_FREE_MODE";

    public enum EpubSourceType {
        RAW,
        ASSETS,
        SD_CARD
    }

    public static final int ACTION_CONTENT_HIGHLIGHT = 77;
    private String bookFileName;
    private static final String HIGHLIGHT_ITEM = "highlight_item";

    private DirectionalViewpager mFolioPageViewPager;
    private ActionBar actionBar;
    private FolioAppBarLayout appBarLayout;
    private Toolbar toolbar;
    private SearchView searchView;
    private boolean distractionFreeMode;
    private Handler handler;

    private int mChapterPosition;
    private FolioPageFragmentAdapter mFolioPageFragmentAdapter;
    private ReadPosition entryReadPosition;
    private ReadPosition lastReadPosition;
    private Bundle outState;
    private Bundle savedInstanceState;

    private List<Link> mSpineReferenceList = new ArrayList<>();
    private EpubServer mEpubServer;

    private String mBookId;
    private String mEpubFilePath;
    private EpubSourceType mEpubSourceType;
    int mEpubRawId = 0;
    private MediaControllerFragment mediaControllerFragment;
    private Config.Direction direction = Config.Direction.VERTICAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler();
        setConfig(savedInstanceState);
        initDistractionFreeMode(savedInstanceState);

        setContentView(R.layout.folio_activity);
        this.savedInstanceState = savedInstanceState;

        mBookId = getIntent().getStringExtra(FolioReader.INTENT_BOOK_ID);
        mEpubSourceType = (EpubSourceType)
                getIntent().getExtras().getSerializable(FolioActivity.INTENT_EPUB_SOURCE_TYPE);
        if (mEpubSourceType.equals(EpubSourceType.RAW)) {
            mEpubRawId = getIntent().getExtras().getInt(FolioActivity.INTENT_EPUB_SOURCE_PATH);
        } else {
            mEpubFilePath = getIntent().getExtras()
                    .getString(FolioActivity.INTENT_EPUB_SOURCE_PATH);
        }

        initActionBar();
        initMediaController();

        if (ContextCompat.checkSelfPermission(FolioActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(FolioActivity.this, Constants.getWriteExternalStoragePerms(), Constants.WRITE_EXTERNAL_STORAGE_REQUEST);
        } else {
            setupBook();
        }
    }

    private void initActionBar() {

        appBarLayout = findViewById(R.id.appBarLayout);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        actionBar = getSupportActionBar();
        toolbar.setNavigationIcon(R.drawable.ic_drawer);

        Config config = AppUtil.getSavedConfig(getApplicationContext());
        assert config != null;

        if (config.isNightMode()) {
            setNightMode();
        } else {
            setDayMode();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int color;
            if (config.isNightMode()) {
                color = ContextCompat.getColor(this, R.color.black);
            } else {
                int[] attrs = {android.R.attr.navigationBarColor};
                TypedArray typedArray = getTheme().obtainStyledAttributes(attrs);
                color = typedArray.getColor(0, ContextCompat.getColor(this, R.color.white));
            }
            getWindow().setNavigationBarColor(color);
        }

        if (Build.VERSION.SDK_INT < 16) {
            // Fix for appBarLayout.fitSystemWindows() not being called on API < 16
            appBarLayout.setTopMargin(getStatusBarHeight());
        }
    }

    @Override
    public void setDayMode() {
        Log.d(LOG_TAG, "-> setDayMode");

        actionBar.setBackgroundDrawable(
                new ColorDrawable(ContextCompat.getColor(this, R.color.white)));
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.black));
    }

    @Override
    public void setNightMode() {
        Log.d(LOG_TAG, "-> setNightMode");

        actionBar.setBackgroundDrawable(
                new ColorDrawable(ContextCompat.getColor(this, R.color.black)));
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.white));
    }

    private void initMediaController() {
        Log.d(LOG_TAG, "-> initMediaController");

        mediaControllerFragment = MediaControllerFragment.
                getInstance(getSupportFragmentManager(), this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        Config config = AppUtil.getSavedConfig(getApplicationContext());
        assert config != null;
        UiUtil.setColorIntToDrawable(config.getThemeColor(), menu.findItem(R.id.itemSearch).getIcon());
        UiUtil.setColorIntToDrawable(config.getThemeColor(), menu.findItem(R.id.itemConfig).getIcon());
        UiUtil.setColorIntToDrawable(config.getThemeColor(), menu.findItem(R.id.itemTts).getIcon());

        if (!config.isShowTts())
            menu.findItem(R.id.itemTts).setVisible(false);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //Log.d(LOG_TAG, "-> onOptionsItemSelected -> " + item.getItemId());

        if (item.getItemId() == android.R.id.home) {
            Log.d(LOG_TAG, "-> onOptionsItemSelected -> drawer");
            startContentHighlightActivity();
            return true;

        } else if (item.getItemId() == R.id.itemSearch) {
            Log.d(LOG_TAG, "-> onOptionsItemSelected -> " + item.getTitle());

            return true;

        } else if (item.getItemId() == R.id.itemConfig) {
            Log.d(LOG_TAG, "-> onOptionsItemSelected -> " + item.getTitle());
            showConfigBottomSheetDialogFragment();
            return true;

        } else if (item.getItemId() == R.id.itemTts) {
            Log.d(LOG_TAG, "-> onOptionsItemSelected -> " + item.getTitle());
            showMediaController();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void startContentHighlightActivity() {

        Intent intent = new Intent(FolioActivity.this, ContentHighlightActivity.class);

        try {
            intent.putExtra(CHAPTER_SELECTED, mSpineReferenceList.get(mChapterPosition).href);
        } catch (NullPointerException | IndexOutOfBoundsException e) {
            Log.w(LOG_TAG, "-> " + e);
            intent.putExtra(CHAPTER_SELECTED, "");
        }

        intent.putExtra(FolioReader.INTENT_BOOK_ID, mBookId);
        intent.putExtra(Constants.BOOK_TITLE, bookFileName);
        startActivityForResult(intent, ACTION_CONTENT_HIGHLIGHT);
        overridePendingTransition(R.anim.slide_in_up, R.anim.slide_out_up);
    }

    public void showConfigBottomSheetDialogFragment() {
        new ConfigBottomSheetDialogFragment().show(getSupportFragmentManager(),
                ConfigBottomSheetDialogFragment.LOG_TAG);
    }

    public void showMediaController() {
        mediaControllerFragment.show(getSupportFragmentManager());
    }

    private void initBook(String mEpubFileName, int mEpubRawId, String mEpubFilePath, EpubSourceType mEpubSourceType) {
        try {
            int portNumber = getIntent().getIntExtra(Config.INTENT_PORT, Constants.PORT_NUMBER);
            mEpubServer = EpubServerSingleton.getEpubServerInstance(portNumber);
            mEpubServer.start();
            String path = FileUtil.saveEpubFileAndLoadLazyBook(FolioActivity.this, mEpubSourceType, mEpubFilePath,
                    mEpubRawId, mEpubFileName);
            addEpub(path);

            String urlString = Constants.LOCALHOST + bookFileName + "/manifest";
            new MainPresenter(this).parseManifest(urlString);

        } catch (IOException e) {
            Log.e(LOG_TAG, "initBook failed", e);
        }
    }

    private void addEpub(String path) throws IOException {
        Container epubContainer = new EpubContainer(path);
        mEpubServer.addEpub(epubContainer, "/" + bookFileName);
        getEpubResource();
    }

    private void getEpubResource() {
    }

    @Override
    public void onDirectionChange(@NonNull Config.Direction newDirection) {
        Log.v(LOG_TAG, "-> onDirectionChange");

        FolioPageFragment folioPageFragment = (FolioPageFragment)
                mFolioPageFragmentAdapter.getItem(mFolioPageViewPager.getCurrentItem());
        entryReadPosition = folioPageFragment.getLastReadPosition();

        direction = newDirection;

        mFolioPageViewPager.setDirection(newDirection);
        mFolioPageFragmentAdapter = new FolioPageFragmentAdapter(getSupportFragmentManager(),
                        mSpineReferenceList, bookFileName, mBookId);
        mFolioPageViewPager.setAdapter(mFolioPageFragmentAdapter);
        mFolioPageViewPager.setCurrentItem(mChapterPosition);
    }

    public void initDistractionFreeMode(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "-> initDistractionFreeMode");

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);

        // Deliberately Hidden and shown to make activity contents laid out behind SystemUI
        hideSystemUI();
        showSystemUI();

        distractionFreeMode = savedInstanceState != null &&
                savedInstanceState.getBoolean(BUNDLE_DISTRACTION_FREE_MODE);

        if (distractionFreeMode) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    hideSystemUI();
                }
            });
        }
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        Log.d(LOG_TAG, "-> onSystemUiVisibilityChange -> visibility = " + visibility);

        distractionFreeMode = visibility != View.SYSTEM_UI_FLAG_VISIBLE;
        Log.d(LOG_TAG, "-> distractionFreeMode = " + distractionFreeMode);

        if (actionBar != null) {
            if (distractionFreeMode) {
                actionBar.hide();
            } else {
                actionBar.show();
            }
        }
    }

    @Override
    public void toggleSystemUI() {

        if (distractionFreeMode) {
            showSystemUI();
        } else {
            hideSystemUI();
        }
    }

    public void showSystemUI() {
        Log.d(LOG_TAG, "-> showSystemUI");

        if (Build.VERSION.SDK_INT >= 16) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (appBarLayout != null)
                appBarLayout.setTopMargin(getStatusBarHeight());
            onSystemUiVisibilityChange(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    public void hideSystemUI() {
        Log.d(LOG_TAG, "-> hideSystemUI");

        if (Build.VERSION.SDK_INT >= 16) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE
                    // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    // Hide the nav bar and status bar
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            // Specified 1 just to mock anything other than View.SYSTEM_UI_FLAG_VISIBLE
            onSystemUiVisibilityChange(1);
        }
    }

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @Override
    public ReadPosition getEntryReadPosition() {
        if (entryReadPosition != null) {
            ReadPosition tempReadPosition = entryReadPosition;
            entryReadPosition = null;
            return tempReadPosition;
        }
        return null;
    }

    /**
     * Go to chapter specified by href
     * @param href http link or relative link to the page or to the anchor
     * @return true if href is of EPUB or false if other link
     */
    @Override
    public boolean goToChapter(String href) {

        for (Link spine : mSpineReferenceList) {
            if (href.contains(spine.href)) {
                mChapterPosition = mSpineReferenceList.indexOf(spine);
                mFolioPageViewPager.setCurrentItem(mChapterPosition);
                FolioPageFragment folioPageFragment = (FolioPageFragment)
                        mFolioPageFragmentAdapter.getItem(mChapterPosition);
                folioPageFragment.scrollToFirst();
                folioPageFragment.scrollToAnchorId(href);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTION_CONTENT_HIGHLIGHT && resultCode == RESULT_OK && data.hasExtra(TYPE)) {

            String type = data.getStringExtra(TYPE);

            if (type.equals(CHAPTER_SELECTED)) {
                goToChapter(data.getStringExtra(SELECTED_CHAPTER_POSITION));

            } else if (type.equals(HIGHLIGHT_SELECTED)) {
                HighlightImpl highlightImpl = data.getParcelableExtra(HIGHLIGHT_ITEM);
                mFolioPageViewPager.setCurrentItem(highlightImpl.getPageNumber());
                FolioPageFragment folioPageFragment = (FolioPageFragment)
                        mFolioPageFragmentAdapter.getItem(highlightImpl.getPageNumber());
                folioPageFragment.scrollToHighlightId(highlightImpl.getRangy());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (outState != null)
            outState.putParcelable(BUNDLE_READ_POSITION_CONFIG_CHANGE, lastReadPosition);

        if (mEpubServer != null) {
            mEpubServer.stop();
        }
    }

    @Override
    public int getChapterPosition() {
        return mChapterPosition;
    }

    @Override
    public void onLoadPublication(EpubPublication publication) {

        mSpineReferenceList.addAll(publication.spines);
        if (publication.metadata.title != null) {
            toolbar.setTitle(publication.metadata.title);
        }

        if (mBookId == null) {
            if (publication.metadata.identifier != null) {
                mBookId = publication.metadata.identifier;
            } else {
                if (publication.metadata.title != null) {
                    mBookId = String.valueOf(publication.metadata.title.hashCode());
                } else {
                    mBookId = String.valueOf(bookFileName.hashCode());
                }
            }
        }
        configFolio();
    }

    private void configFolio() {

        mFolioPageViewPager = findViewById(R.id.folioPageViewPager);
        // Replacing with addOnPageChangeListener(), onPageSelected() is not invoked
        mFolioPageViewPager.setOnPageChangeListener(new DirectionalViewpager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                Log.v(LOG_TAG, "-> onPageSelected -> DirectionalViewpager -> position = " + position);

                EventBus.getDefault().post(new MediaOverlayPlayPauseEvent(
                        mSpineReferenceList.get(mChapterPosition).href, false, true));
                mediaControllerFragment.setPlayButtonDrawable();
                mChapterPosition = position;
                toolbar.setTitle(mSpineReferenceList.get(mChapterPosition).bookTitle);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

                if (state == DirectionalViewpager.SCROLL_STATE_IDLE) {
                    int position = mFolioPageViewPager.getCurrentItem();
                    Log.v(LOG_TAG, "-> onPageScrollStateChanged -> DirectionalViewpager -> " +
                            "position = " + position);

                    FolioPageFragment folioPageFragment =
                            (FolioPageFragment) mFolioPageFragmentAdapter.getItem(position - 1);
                    if (folioPageFragment != null)
                        folioPageFragment.scrollToLast();

                    folioPageFragment =
                            (FolioPageFragment) mFolioPageFragmentAdapter.getItem(position + 1);
                    if (folioPageFragment != null)
                        folioPageFragment.scrollToFirst();
                }
            }
        });

        if (mSpineReferenceList != null) {

            mFolioPageViewPager.setDirection(direction);
            mFolioPageFragmentAdapter = new FolioPageFragmentAdapter(getSupportFragmentManager(),
                    mSpineReferenceList, bookFileName, mBookId);
            mFolioPageViewPager.setAdapter(mFolioPageFragmentAdapter);

            ReadPosition readPosition;
            if (savedInstanceState == null) {
                readPosition = getIntent().getParcelableExtra(FolioActivity.EXTRA_READ_POSITION);
                entryReadPosition = readPosition;
            } else {
                readPosition = savedInstanceState.getParcelable(BUNDLE_READ_POSITION_CONFIG_CHANGE);
                lastReadPosition = readPosition;
            }
            mFolioPageViewPager.setCurrentItem(getChapterIndex(readPosition));
        }
    }

    /**
     * Returns the index of the chapter by following priority -
     * 1. id
     * 2. href
     * 3. index
     *
     * @param readPosition Last read position
     * @return index of the chapter
     */
    private int getChapterIndex(ReadPosition readPosition) {
        if (readPosition == null) {
            return 0;

        } else if (!TextUtils.isEmpty(readPosition.getChapterId())) {
            return getChapterIndex("id", readPosition.getChapterId());

        } else if (!TextUtils.isEmpty(readPosition.getChapterHref())) {
            return getChapterIndex("href", readPosition.getChapterHref());

        } else if (readPosition.getChapterIndex() > -1
                && readPosition.getChapterIndex() < mSpineReferenceList.size()) {
            return readPosition.getChapterIndex();
        }

        return 0;
    }

    private int getChapterIndex(String caseString, String value) {
        for (int i = 0; i < mSpineReferenceList.size(); i++) {
            switch (caseString) {
                case "id":
                    if (mSpineReferenceList.get(i).getId().equals(value))
                        return i;
                case "href":
                    if (mSpineReferenceList.get(i).getOriginalHref().equals(value))
                        return i;
            }
        }
        return 0;
    }

    /**
     * If called, this method will occur after onStop() for applications targeting platforms
     * starting with Build.VERSION_CODES.P. For applications targeting earlier platform versions
     * this method will occur before onStop() and there are no guarantees about whether it will
     * occur before or after onPause()
     *
     * @see Activity#onSaveInstanceState(Bundle) of Build.VERSION_CODES.P
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.v(LOG_TAG, "-> onSaveInstanceState");
        this.outState = outState;

        outState.putBoolean(BUNDLE_DISTRACTION_FREE_MODE, distractionFreeMode);
    }

    @Override
    public void storeLastReadPosition(ReadPosition lastReadPosition) {
        Log.v(LOG_TAG, "-> storeLastReadPosition");
        this.lastReadPosition = lastReadPosition;
    }

    private void setConfig(Bundle savedInstanceState) {

        Config config;
        Config intentConfig = getIntent().getParcelableExtra(Config.INTENT_CONFIG);
        boolean overrideConfig = getIntent().getBooleanExtra(Config.EXTRA_OVERRIDE_CONFIG, false);
        Config savedConfig = AppUtil.getSavedConfig(this);

        if (savedInstanceState != null) {
            config = savedConfig;

        } else if (savedConfig == null) {
            if (intentConfig == null) {
                config = new Config();
            } else {
                config = intentConfig;
            }

        } else {
            if (intentConfig != null && overrideConfig) {
                config = intentConfig;
            } else {
                config = savedConfig;
            }
        }

        // Code would never enter this if, just added for any unexpected error
        // and to avoid lint warning
        if (config == null)
            config = new Config();

        AppUtil.saveConfig(this, config);
        direction = config.getDirection();
    }

    @Override
    public void play() {
        EventBus.getDefault().post(new MediaOverlayPlayPauseEvent(
                mSpineReferenceList.get(mChapterPosition).href, true, false));
    }

    @Override
    public void pause() {
        EventBus.getDefault().post(new MediaOverlayPlayPauseEvent(
                mSpineReferenceList.get(mChapterPosition).href, false, false));
    }

    @Override
    public void onError() {
    }

    private void setupBook() {
        bookFileName = FileUtil.getEpubFilename(this, mEpubSourceType, mEpubFilePath, mEpubRawId);
        initBook(bookFileName, mEpubRawId, mEpubFilePath, mEpubSourceType);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case Constants.WRITE_EXTERNAL_STORAGE_REQUEST:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupBook();
                } else {
                    Toast.makeText(this, getString(R.string.cannot_access_epub_message), Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
        }
    }

    @Override
    public Config.Direction getDirection() {
        return direction;
    }
}