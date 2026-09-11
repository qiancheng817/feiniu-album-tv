package com.fnphoto.tv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.*;

import com.fnphoto.tv.api.FnAuthUtils;
import com.fnphoto.tv.api.FnHttpApi;
import com.fnphoto.tv.api.HttpClientProvider;
import com.fnphoto.tv.ui.HeroHost;
import com.fnphoto.tv.ui.TvUiText;
import com.google.gson.Gson;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainFragment extends BrowseSupportFragment {
    private static final String TAG = "MainFragment";
    private static final int PREVIEW_LOAD_DELAY = 300; // 延迟加载时间
    private static final int VISIBLE_RANGE_BUFFER = 40; // 可视范围前后的缓冲数量

    private FnHttpApi api;
    private String token;
    private String baseUrl;
    private ArrayObjectAdapter mRowsAdapter;
    private CardPresenter mCardPresenter;
    private List<FnHttpApi.TimelineItem> timelineItems;
    private boolean isPhotoListView = false;
    private boolean isHomeView = false;
    private List<MediaItem> currentMediaList;
    
    // 懒加载相关
    private List<MediaItem> allDateItems = new ArrayList<>();
    private List<FnHttpApi.TimelineItem> allTimelineItems = new ArrayList<>();
    private Set<Integer> loadedIndexes = new HashSet<>();
    private Handler lazyLoadHandler = new Handler(Looper.getMainLooper());
    private Handler positionHandler = new Handler(Looper.getMainLooper()); // 专门用于位置恢复
    private int lastVisibleIndex = -1;
    
    // 预览缩略图缓存（按日期字符串缓存，避免返回时重新加载）
    private Map<String, List<String>> previewThumbnailCache = new HashMap<>();

    // 保存滚动位置
    private int savedTimelinePosition = -1;  // 保存时间线的选中位置
    private int savedPhotoListPosition = -1; // 保存照片列表的选中位置
    private SharedPreferences.OnSharedPreferenceChangeListener tokenChangeListener;

    // 人物模式
    private int currentPersonId = -1;
    private String currentPersonName;
    private List<FnHttpApi.PersonTimelineItem> savedPersonTimelineItems;
    private List<MediaItem> personDateItems = new ArrayList<>();
    private List<FnHttpApi.PersonTimelineItem> personTimelineItemsList = new ArrayList<>();
    private Set<Integer> personLoadedIdx = new HashSet<>();
    private int personLastVisibleIdx = -1;
    private int pendingHomeRequests = 0;
    private boolean homeHasRows = false;
    private List<FnHttpApi.GalleryPhoto> homeRecentPhotos;
    private List<FnHttpApi.PersonItem> homePeopleItems;
    private List<FnHttpApi.NewAlbum> homeAlbumItems;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getArguments() != null) {
            baseUrl = getArguments().getString("nas_url", "");
            token = getArguments().getString("api_token", "");
        }

        setupUI();
        
        if (baseUrl != null && !baseUrl.isEmpty()) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl + "/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(HttpClientProvider.getClient(getActivity()))
                    .build();
            api = retrofit.create(FnHttpApi.class);
        }

        setupEventListeners();
        watchTokenChanges();
    }

    private void watchTokenChanges() {
        if (getActivity() == null) return;
        SharedPreferences prefs = getActivity().getSharedPreferences("fn_photo_prefs", Context.MODE_PRIVATE);
        tokenChangeListener = (sharedPreferences, key) -> {
            if ("api_token".equals(key)) {
                String newToken = sharedPreferences.getString(key, "");
                if (!newToken.isEmpty() && !newToken.equals(token)) {
                    token = newToken;
                    Log.i(TAG, "Token updated from SharedPreferences");
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(tokenChangeListener);
    }

    private void setupUI() {
        setTitle("");
        setHeadersState(BrowseSupportFragment.HEADERS_DISABLED);
        setBrandColor(getResources().getColor(android.R.color.transparent));
        setSearchAffordanceColor(getResources().getColor(android.R.color.transparent));
        
        mCardPresenter = new CardPresenter(baseUrl);
        ListRowPresenter rowPresenter = new ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE);
        rowPresenter.setShadowEnabled(false);
        rowPresenter.setSelectEffectEnabled(false);
        mRowsAdapter = new ArrayObjectAdapter(rowPresenter);
        setAdapter(mRowsAdapter);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(0x00000000);
        if (view instanceof ViewGroup) {
            ((ViewGroup) view).setClipToPadding(false);
        }
        view.setPadding(
                getResources().getDimensionPixelSize(R.dimen.tv_rail_width_collapsed),
                getResources().getDimensionPixelSize(R.dimen.tv_home_content_top),
                48,
                0);
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                      RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof MediaItem) {
                    MediaItem mediaItem = (MediaItem) item;
                    
                    if ("date".equals(mediaItem.getType())) {
                        loadPhotosByDate(mediaItem.getDateStr(), mediaItem.getPhotoCount());
                    } else if ("person_date".equals(mediaItem.getType())) {
                        loadPersonPhotosByDate(mediaItem.getDateStr(), mediaItem.getPhotoCount());
                    } else if ("folder".equals(mediaItem.getType())) {
                        openFolderBrowse(mediaItem);
                    } else if ("album".equals(mediaItem.getType())) {
                        loadPhotosByAlbum(mediaItem.getId(), mediaItem.getTitle());
                    } else if ("place".equals(mediaItem.getType())) {
                        loadPhotosByGeo(mediaItem);
                    } else if ("person".equals(mediaItem.getType())) {
                        int personId = Integer.parseInt(mediaItem.getId());
                        loadPersonTimeline(personId, mediaItem.getTitle());
                    } else if ("video".equals(mediaItem.getType()) || "photo".equals(mediaItem.getType())) {
                        openMediaDetail(mediaItem);
                    }
                }
            }
        });

        // 监听选中项变化，实现懒加载
        setOnItemViewSelectedListener(new OnItemViewSelectedListener() {
            @Override
            public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                       RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof MediaItem) {
                    MediaItem mi = (MediaItem) item;
                    updateHeroFromItem(mi);
                    if ("date".equals(mi.getType())) {
                        int selectedIndex = allDateItems.indexOf(item);
                        if (selectedIndex >= 0) {
                            scheduleLazyLoad(selectedIndex);
                        }
                    } else if ("person_date".equals(mi.getType())) {
                        int selectedIndex = personDateItems.indexOf(item);
                        if (selectedIndex >= 0) {
                            schedulePersonLazyLoad(selectedIndex);
                        }
                    }
                }
            }
        });
    }

    private void updateHeroFromItem(MediaItem item) {
        if (!(getActivity() instanceof HeroHost) || item == null) {
            return;
        }

        if ("photo".equals(item.getType()) || "video".equals(item.getType())) {
            ((HeroHost) getActivity()).clearHero();
            return;
        }

        String imageUrl = item.getThumbnailUrl();
        if ((imageUrl == null || imageUrl.isEmpty())
                && item.getPreviewThumbUrls() != null
                && !item.getPreviewThumbUrls().isEmpty()) {
            imageUrl = item.getPreviewThumbUrls().get(0);
        }

        String title = item.getTitle();
        String subtitle = normalizeHeroSubtitle(item.getDateStr());
        if ("photo".equals(item.getType()) || "video".equals(item.getType())) {
            String mediaTitle = TvUiText.mediaHeroTitle(item.getType());
            if (mediaTitle != null && !mediaTitle.isEmpty()) {
                title = mediaTitle;
            }
        }
        ((HeroHost) getActivity()).updateHero(imageUrl, title, subtitle);
    }

    private String normalizeHeroSubtitle(String subtitle) {
        if (subtitle == null) {
            return "";
        }
        return subtitle.replaceFirst("^(\\d{4}):(\\d{2}):(\\d{2})", "$1-$2-$3");
    }

    private void clearHero() {
        if (getActivity() instanceof HeroHost) {
            ((HeroHost) getActivity()).clearHero();
        }
    }
    
    private void scheduleLazyLoad(int centerIndex) {
        lastVisibleIndex = centerIndex;
        
        // 取消之前的延迟任务
        lazyLoadHandler.removeCallbacksAndMessages(null);
        
        // 延迟加载，避免快速滚动时频繁加载
        lazyLoadHandler.postDelayed(() -> {
            if (centerIndex == lastVisibleIndex) {
                // 用户停止滚动，加载可视范围内的预览
                loadVisiblePreviews(centerIndex);
            }
        }, PREVIEW_LOAD_DELAY);
    }
    
    private void loadVisiblePreviews(int centerIndex) {
        if (allDateItems.isEmpty() || allTimelineItems.isEmpty()) return;
        
        int start = Math.max(0, centerIndex - VISIBLE_RANGE_BUFFER);
        int end = Math.min(allDateItems.size(), centerIndex + VISIBLE_RANGE_BUFFER + 1);
        
        Log.d(TAG, "Loading previews for visible range: " + start + " to " + end);
        
        for (int i = start; i < end; i++) {
            if (!loadedIndexes.contains(i)) {
                loadedIndexes.add(i);
                final int index = i;
                final MediaItem mediaItem = allDateItems.get(i);
                final FnHttpApi.TimelineItem timelineItem = allTimelineItems.get(i);
                
                if (timelineItem.itemCount > 0) {
                    // 如果已有缓存的预览缩略图，直接通知更新，无需重新请求
                    if (mediaItem.getPreviewThumbUrls() != null && !mediaItem.getPreviewThumbUrls().isEmpty()) {
                        Log.d(TAG, "Using cached preview for " + mediaItem.getDateStr());
                        notifyItemChanged(mediaItem);
                    } else {
                        loadDatePreviewThumbnails(mediaItem, timelineItem, () -> {
                            notifyItemChanged(mediaItem);
                        });
                    }
                }
            }
        }
    }
    
    private void notifyItemChanged(MediaItem item) {
        // 遍历所有行，找到并更新对应的项
        for (int i = 0; i < mRowsAdapter.size(); i++) {
            Object row = mRowsAdapter.get(i);
            if (row instanceof ListRow) {
                ArrayObjectAdapter rowAdapter = (ArrayObjectAdapter) ((ListRow) row).getAdapter();
                int itemIndex = rowAdapter.indexOf(item);
                if (itemIndex >= 0) {
                    rowAdapter.notifyItemRangeChanged(itemIndex, 1);
                    break;
                }
            }
        }
    }

    public boolean onBackPressed() {
        if (isPhotoListView) {
            if (timelineItems != null) {
                Log.d(TAG, "Returning to timeline");
                savePhotoListPosition();
                displayTimeline(timelineItems);
            } else if (savedAlbumList != null) {
                Log.d(TAG, "Returning to album list");
                displayAlbums(savedAlbumList);
            } else if (savedPlacesList != null) {
                Log.d(TAG, "Returning to places list");
                displayPlaces(savedPlacesList);
            } else if (savedPersonTimelineItems != null) {
                Log.d(TAG, "Returning to person timeline");
                displayPersonTimeline(savedPersonTimelineItems);
            } else if (isHomeView) {
                Log.d(TAG, "Returning to home");
                loadHome();
            } else {
                return false;
            }
            return true;
        } else if (currentPersonId > 0) {
            Log.d(TAG, "Returning from person timeline to person list");
            currentPersonId = -1;
            currentPersonName = null;
            if (savedPersonList != null) {
                displayPeople(savedPersonList);
                return true;
            }
            return false;
        }
        return false;
    }
    
    private void saveTimelinePosition() {
        try {
            // 找到当前选中的日期项在allDateItems中的索引
            int selectedRow = getSelectedPosition();
            if (selectedRow >= 0 && selectedRow < mRowsAdapter.size()) {
                Object row = mRowsAdapter.get(selectedRow);
                if (row instanceof ListRow) {
                    // 获取当前行的选中位置
                    // 注意：Leanback不直接提供行内选中位置，我们使用lastVisibleIndex
                    savedTimelinePosition = lastVisibleIndex;
                    Log.d(TAG, "Saved timeline position: " + savedTimelinePosition);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving timeline position", e);
        }
    }
    
    private void savePhotoListPosition() {
        try {
            savedPhotoListPosition = getSelectedPosition();
            Log.d(TAG, "Saved photo list position: " + savedPhotoListPosition);
        } catch (Exception e) {
            Log.e(TAG, "Error saving photo list position", e);
        }
    }
    
    private void restoreTimelinePosition() {
        Log.d(TAG, "restoreTimelinePosition called, saved position: " + savedTimelinePosition + ", total items: " + allDateItems.size());
        if (savedTimelinePosition >= 0 && savedTimelinePosition < allDateItems.size()) {
            // 使用独立的 Handler，避免被 lazyLoadHandler 清空
            positionHandler.postDelayed(() -> {
                try {
                    // 找到该日期项所在的行
                    MediaItem targetItem = allDateItems.get(savedTimelinePosition);
                    Log.d(TAG, "Looking for item: " + targetItem.getId() + " at position " + savedTimelinePosition);
                    
                    for (int rowIdx = 0; rowIdx < mRowsAdapter.size(); rowIdx++) {
                        Object row = mRowsAdapter.get(rowIdx);
                        if (row instanceof ListRow) {
                            ArrayObjectAdapter rowAdapter = (ArrayObjectAdapter) ((ListRow) row).getAdapter();
                            int itemIdx = rowAdapter.indexOf(targetItem);
                            Log.d(TAG, "Row " + rowIdx + ": item index = " + itemIdx);
                            
                            if (itemIdx >= 0) {
                                // 找到行，选中它
                                setSelectedPosition(rowIdx);
                                Log.d(TAG, "Restored timeline position - row: " + rowIdx + ", item index: " + savedTimelinePosition);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring timeline position", e);
                }
            }, 800); // 延迟800ms等待视图准备好
        } else {
            Log.w(TAG, "Invalid saved timeline position: " + savedTimelinePosition);
        }
    }

    private void resetHomeState() {
        isPhotoListView = false;
        isHomeView = true;
        timelineItems = null;
        savedAlbumList = null;
        savedPlacesList = null;
        savedPersonList = null;
        savedPersonTimelineItems = null;
        currentPersonId = -1;
        currentPersonName = null;
        currentMediaList = null;
        allDateItems.clear();
        allTimelineItems.clear();
        loadedIndexes.clear();
        personDateItems.clear();
        personTimelineItemsList.clear();
        personLoadedIdx.clear();
        mRowsAdapter.clear();
        homeHasRows = false;
        homeRecentPhotos = null;
        homePeopleItems = null;
        homeAlbumItems = null;
    }

    private void finishHomeRequest() {
        pendingHomeRequests--;
        if (pendingHomeRequests <= 0) {
            renderHomeRows();
            hideLoadingOverlay();
            if (!homeHasRows) {
                showEmptyState(TvUiText.emptyMessage("photos"));
            }
        }
    }

    private void renderHomeRows() {
        mRowsAdapter.clear();
        allDateItems.clear();
        allTimelineItems.clear();
        loadedIndexes.clear();
        homeHasRows = false;
        currentMediaList = new ArrayList<>();

        addHomePhotoRows(homeRecentPhotos, 36);
    }

    private void addHomePhotoRows(List<FnHttpApi.GalleryPhoto> photos, int maxItems) {
        if (photos == null || photos.isEmpty()) {
            return;
        }

        int count = Math.min(photos.size(), maxItems);
        Map<String, List<FnHttpApi.GalleryPhoto>> groupedPhotos = new LinkedHashMap<>();

        for (int i = 0; i < count; i++) {
            FnHttpApi.GalleryPhoto photo = photos.get(i);
            String key = galleryDateKey(photo);
            if (!groupedPhotos.containsKey(key)) {
                groupedPhotos.put(key, new ArrayList<>());
            }
            groupedPhotos.get(key).add(photo);
        }

        for (Map.Entry<String, List<FnHttpApi.GalleryPhoto>> entry : groupedPhotos.entrySet()) {
            HeaderItem header = new HeaderItem(entry.getKey());
            ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(mCardPresenter);
            for (FnHttpApi.GalleryPhoto photo : entry.getValue()) {
                MediaItem mediaItem = mediaItemFromGalleryPhoto(photo);
                currentMediaList.add(mediaItem);
                rowAdapter.add(mediaItem);
            }
            mRowsAdapter.add(new ListRow(header, rowAdapter));
            homeHasRows = true;
        }
    }

    private String galleryDateKey(FnHttpApi.GalleryPhoto photo) {
        String rawDate = photo.photoDateTime != null && !photo.photoDateTime.isEmpty()
                ? photo.photoDateTime
                : photo.dateTime;
        if (rawDate == null || rawDate.length() < 10) {
            return TvUiText.homeRecentPhotosTitle();
        }

        String normalized = rawDate.replace(":", "-");
        try {
            int year = Integer.parseInt(normalized.substring(0, 4));
            int month = Integer.parseInt(normalized.substring(5, 7));
            int day = Integer.parseInt(normalized.substring(8, 10));
            return TvUiText.photoDateTitle(year, month, day);
        } catch (RuntimeException e) {
            return TvUiText.homeRecentPhotosTitle();
        }
    }

    private interface HomePhotosCallback {
        void onLoaded(List<FnHttpApi.GalleryPhoto> photos);
    }

    private void loadHomePhotosFromTimeline(List<FnHttpApi.TimelineItem> items,
                                            int maxPhotos,
                                            HomePhotosCallback callback) {
        List<FnHttpApi.GalleryPhoto> photos = new ArrayList<>();
        loadHomePhotosFromTimeline(items, 0, 0, maxPhotos, photos, callback);
    }

    private void loadHomePhotosFromTimeline(List<FnHttpApi.TimelineItem> items,
                                            int index,
                                            int visitedDates,
                                            int maxPhotos,
                                            List<FnHttpApi.GalleryPhoto> photos,
                                            HomePhotosCallback callback) {
        if (items == null || index >= items.size() || visitedDates >= 8 || photos.size() >= maxPhotos) {
            callback.onLoaded(photos);
            return;
        }

        FnHttpApi.TimelineItem item = items.get(index);
        if (item.itemCount <= 0) {
            loadHomePhotosFromTimeline(items, index + 1, visitedDates + 1, maxPhotos, photos, callback);
            return;
        }

        String dateStr = item.year + "-" + String.format("%02d", item.month) + "-" + String.format("%02d", item.day);
        String dateTime = dateStr.replace("-", ":");
        String startTime = dateTime + " 00:00:00";
        String endTime = dateTime + " 23:59:59";
        int remaining = maxPhotos - photos.size();
        int limit = Math.max(1, Math.min(item.itemCount, Math.min(remaining, 12)));
        String mode = "index";

        StringBuilder paramsBuilder = new StringBuilder();
        paramsBuilder.append("end_time=").append(endTime);
        paramsBuilder.append("&limit=").append(limit);
        paramsBuilder.append("&mode=").append(mode);
        paramsBuilder.append("&offset=0");
        paramsBuilder.append("&start_time=").append(startTime);

        String authx = FnAuthUtils.generateAuthX("/p/api/v1/gallery/getList", "GET", paramsBuilder.toString());
        api.getPhotosByTimeRange(token, authx, startTime, endTime, limit, 0, mode, null)
                .enqueue(new Callback<FnHttpApi.GalleryListResponse>() {
                    @Override
                    public void onResponse(Call<FnHttpApi.GalleryListResponse> call,
                                           Response<FnHttpApi.GalleryListResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().data != null
                                && response.body().data.list != null) {
                            photos.addAll(response.body().data.list);
                        }
                        loadHomePhotosFromTimeline(items, index + 1, visitedDates + 1, maxPhotos, photos, callback);
                    }

                    @Override
                    public void onFailure(Call<FnHttpApi.GalleryListResponse> call, Throwable t) {
                        Log.e(TAG, "首页照片流日期加载失败: " + dateStr, t);
                        loadHomePhotosFromTimeline(items, index + 1, visitedDates + 1, maxPhotos, photos, callback);
                    }
                });
    }

    private MediaItem mediaItemFromGalleryPhoto(FnHttpApi.GalleryPhoto photo) {
        String thumbUrl = null;
        String originalUrl = null;

        if (photo.additional != null && photo.additional.thumbnail != null) {
            FnHttpApi.GalleryThumbnail thumbnail = photo.additional.thumbnail;
            thumbUrl = thumbnail.mUrl != null ? baseUrl + thumbnail.mUrl : (thumbnail.sUrl != null ? baseUrl + thumbnail.sUrl : null);
            originalUrl = thumbnail.originalUrl != null ? baseUrl + thumbnail.originalUrl : (thumbnail.mUrl != null ? baseUrl + thumbnail.mUrl : null);
        }

        MediaItem item = new MediaItem(
                String.valueOf(photo.id),
                photo.fileName,
                photo.category,
                thumbUrl,
                originalUrl
        );
        item.setDateStr(photo.photoDateTime != null && !photo.photoDateTime.isEmpty() ? photo.photoDateTime : photo.dateTime);
        return item;
    }

    private void addHomePeopleRow(List<FnHttpApi.PersonItem> persons, int maxItems) {
        if (persons == null || persons.isEmpty()) {
            return;
        }

        HeaderItem header = new HeaderItem("人物");
        ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(mCardPresenter);
        int count = Math.min(persons.size(), maxItems);

        for (int i = 0; i < count; i++) {
            FnHttpApi.PersonItem person = persons.get(i);
            String thumbUrl = person.faceId > 0 ? baseUrl + "/p/api/v1/stream/face/" + person.faceId : null;
            MediaItem item = new MediaItem(String.valueOf(person.id), person.name, "person", thumbUrl, null);
            item.setPhotoCount(person.itemCount);
            rowAdapter.add(item);
        }

        if (rowAdapter.size() > 0) {
            mRowsAdapter.add(new ListRow(header, rowAdapter));
            homeHasRows = true;
        }
    }

    private void addHomeAlbumRow(List<FnHttpApi.NewAlbum> albums, int maxItems) {
        if (albums == null || albums.isEmpty()) {
            return;
        }

        HeaderItem header = new HeaderItem("相册");
        ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(mCardPresenter);
        int count = Math.min(albums.size(), maxItems);

        for (int i = 0; i < count; i++) {
            FnHttpApi.NewAlbum album = albums.get(i);
            String posterUrl = album.posterUrl != null ? baseUrl + album.posterUrl : null;
            MediaItem item = new MediaItem(String.valueOf(album.albumId), album.albumName, "album", posterUrl, posterUrl);
            String countInfo = TvUiText.mediaCountText(album.photoCount, album.videoCount);
            if (!countInfo.isEmpty()) {
                item.setDateStr(countInfo);
            }
            rowAdapter.add(item);
        }

        if (rowAdapter.size() > 0) {
            mRowsAdapter.add(new ListRow(header, rowAdapter));
            homeHasRows = true;
        }
    }

    public void loadHome() {
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        resetHomeState();
        showLoadingOverlay("正在准备首页...");
        pendingHomeRequests = 1;

        String recentAuth = FnAuthUtils.generateAuthX("/p/api/v1/explore/recent_timeline", "GET", null);
        api.getRecentTimeline(token, recentAuth).enqueue(new Callback<FnHttpApi.TimelineResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.TimelineResponse> call, Response<FnHttpApi.TimelineResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().data != null && response.body().data.list != null) {
                    loadHomePhotosFromTimeline(response.body().data.list, 36, this::completeHomeRecentPhotos);
                } else {
                    finishHomeRequest();
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.TimelineResponse> call, Throwable t) {
                Log.e(TAG, "首页最近照片加载失败", t);
                finishHomeRequest();
            }

            private void completeHomeRecentPhotos(List<FnHttpApi.GalleryPhoto> photos) {
                homeRecentPhotos = photos;
                finishHomeRequest();
            }
        });
    }

    public void loadTimeline() {
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        showLoadingOverlay("正在加载...");
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/gallery/timeline", "GET", null);

        api.getTimeline(token, authx, null).enqueue(new Callback<FnHttpApi.TimelineResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.TimelineResponse> call, 
                                   Response<FnHttpApi.TimelineResponse> response) {
                hideLoadingOverlay();
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.TimelineResponse result = response.body();
                    if (result.code == 0 && result.data != null && result.data.list != null) {
                        displayTimeline(result.data.list);
                    }
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.TimelineResponse> call, Throwable t) {
                Log.e(TAG, "加载时间线失败", t);
                hideLoadingOverlay();
            }
        });
    }

    private void displayTimeline(List<FnHttpApi.TimelineItem> items) {
        timelineItems = items;
        isPhotoListView = false;
        isHomeView = false;
        allDateItems.clear();
        allTimelineItems.clear();
        loadedIndexes.clear();
        
        mRowsAdapter.clear();
        
        String currentYearMonth = "";
        ArrayObjectAdapter currentRowAdapter = null;
        
        for (FnHttpApi.TimelineItem item : items) {
            String yearMonth = item.year + "年" + item.month + "月";
            String dateStr = item.year + "-" + String.format("%02d", item.month) + "-" + String.format("%02d", item.day);
            
            if (!yearMonth.equals(currentYearMonth)) {
                currentYearMonth = yearMonth;
                HeaderItem header = new HeaderItem("图库 · " + yearMonth);
                currentRowAdapter = new ArrayObjectAdapter(mCardPresenter);
                mRowsAdapter.add(new ListRow(header, currentRowAdapter));
            }
            
            MediaItem mediaItem = new MediaItem(
                dateStr,
                item.day + "日 · " + item.itemCount + "张",
                item.itemCount
            );
            // 如果缓存中有预览缩略图，直接复用
            if (previewThumbnailCache.containsKey(dateStr)) {
                mediaItem.setPreviewThumbUrls(previewThumbnailCache.get(dateStr));
            }
            currentRowAdapter.add(mediaItem);
            allDateItems.add(mediaItem);
            allTimelineItems.add(item);
        }
        
        // 初始加载前几个可见项的预览
        if (!allDateItems.isEmpty()) {
            lazyLoadHandler.postDelayed(() -> loadVisiblePreviews(0), 500);
        }
        
        // 恢复之前保存的位置
        restoreTimelinePosition();
    }

    public void loadFolders() {
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        showLoadingOverlay("正在加载文件夹...");
        Boolean desc = false;
        Integer orderBy = 2;
        StringBuilder paramsBuilder = new StringBuilder();
        paramsBuilder.append("desc=").append(desc);
        paramsBuilder.append("&orderBy=").append(orderBy);

        String params = paramsBuilder.toString();

        String authx = FnAuthUtils.generateAuthX("/p/api/v1/photo/folder/list", "GET", params);

        api.getManagedFolders(token, authx, desc, orderBy).enqueue(new Callback<FnHttpApi.FolderListResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.FolderListResponse> call,
                                   Response<FnHttpApi.FolderListResponse> response) {
                hideLoadingOverlay();
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.FolderListResponse result = response.body();
                    if (result.code == 0 && result.data != null && result.data.list != null) {
                        displayFolders(result.data.list);
                    } else {
                        Log.e(TAG, "加载文件夹失败: code=" + result.code + ", msg=" + result.msg);
                    }
                } else {
                    Log.e(TAG, "加载文件夹失败: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.FolderListResponse> call, Throwable t) {
                Log.e(TAG, "加载文件夹失败", t);
                hideLoadingOverlay();
            }
        });
    }

    private void displayFolders(List<FnHttpApi.FolderItem> folders) {
        isPhotoListView = false;
        isHomeView = false;
        timelineItems = null;
        mRowsAdapter.clear();

        int itemsPerRow = 6;
        int totalRows = (int) Math.ceil((double) folders.size() / itemsPerRow);

        for (int row = 0; row < totalRows; row++) {
            int start = row * itemsPerRow;
            int end = Math.min(start + itemsPerRow, folders.size());

            HeaderItem header = row == 0 ? new HeaderItem("文件夹 · " + folders.size()) : null;
            ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(mCardPresenter);

            for (int i = start; i < end; i++) {
                FnHttpApi.FolderItem folder = folders.get(i);
                String folderName = folder.getFolderName();
                MediaItem item = new MediaItem(
                    String.valueOf(folder.folderId),
                    folderName,
                    "folder",
                    null,
                    folder.folderPath
                );

                String countInfo = TvUiText.mediaCountText(folder.photoCount, folder.videoCount);
                if (!countInfo.isEmpty()) {
                    item.setDateStr(countInfo);
                }

                rowAdapter.add(item);
            }

            mRowsAdapter.add(new ListRow(header, rowAdapter));
        }
    }

    public void loadAlbums() {
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        showLoadingOverlay("正在加载相册...");
        String params = "sort_direction=desc&sort_by=date_time&offset=0&limit=1000";
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/album/list", "GET", params);

        api.getAlbums(token, authx, "desc", "date_time", 0, 1000).enqueue(new Callback<FnHttpApi.AlbumListResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.AlbumListResponse> call,
                                   Response<FnHttpApi.AlbumListResponse> response) {
                hideLoadingOverlay();
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.AlbumListResponse result = response.body();
                    if (result.code == 0 && result.data != null && result.data.list != null) {
                        displayAlbums(result.data.list);
                    }
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.AlbumListResponse> call, Throwable t) {
                Log.e(TAG, "加载相册失败", t);
                hideLoadingOverlay();
            }
        });
    }

    private void displayAlbums(List<FnHttpApi.NewAlbum> albums) {
        savedAlbumList = new ArrayList<>(albums);
        isPhotoListView = false;
        isHomeView = false;
        timelineItems = null;
        mRowsAdapter.clear();
        
        int itemsPerRow = 6;
        int totalRows = (int) Math.ceil((double) albums.size() / itemsPerRow);

        for (int row = 0; row < totalRows; row++) {
            int start = row * itemsPerRow;
            int end = Math.min(start + itemsPerRow, albums.size());

            HeaderItem header = row == 0 ? new HeaderItem("相册 · " + albums.size()) : null;
            ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(mCardPresenter);

            for (int i = start; i < end; i++) {
                FnHttpApi.NewAlbum album = albums.get(i);
                String posterUrl = album.posterUrl != null ? baseUrl + album.posterUrl : null;
                MediaItem item = new MediaItem(
                    String.valueOf(album.albumId),
                    album.albumName,
                    "album",
                    posterUrl,
                    posterUrl
                );
                String countInfo = TvUiText.mediaCountText(album.photoCount, album.videoCount);
                if (!countInfo.isEmpty()) {
                    item.setDateStr(countInfo);
                }
                rowAdapter.add(item);
            }

            mRowsAdapter.add(new ListRow(header, rowAdapter));
        }
    }

    private void loadDatePreviewThumbnails(final MediaItem mediaItem, 
                                           final FnHttpApi.TimelineItem timelineItem,
                                           final Runnable onComplete) {
        if (api == null || token == null || token.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
        String dateStr = mediaItem.getDateStr();
        String dateTime = dateStr.replace("-", ":");
        String startTime = dateTime + " 00:00:00";
        String endTime = dateTime + " 23:59:59";
        int limit = Math.min(timelineItem.itemCount,4);
        int offset = 0;
        String mode = "index";
        
        StringBuilder paramsBuilder = new StringBuilder();
        paramsBuilder.append("end_time=").append(endTime);
        paramsBuilder.append("&limit=").append(limit);
        paramsBuilder.append("&mode=").append(mode);
        paramsBuilder.append("&offset=").append(offset);
        paramsBuilder.append("&start_time=").append(startTime);
        
        String params = paramsBuilder.toString();
        String path = "/p/api/v1/gallery/getList";
        
        String authx = FnAuthUtils.generateAuthX(path, "GET", params);
        
        api.getPhotosByTimeRange(token, authx, startTime, endTime, limit, offset, mode, null)
            .enqueue(new Callback<FnHttpApi.GalleryListResponse>() {
                @Override
                public void onResponse(Call<FnHttpApi.GalleryListResponse> call,
                                       Response<FnHttpApi.GalleryListResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        FnHttpApi.GalleryListResponse result = response.body();
                        if (result.code == 0 && result.data != null && result.data.list != null) {
                            List<String> thumbUrls = new ArrayList<>();
                            for (FnHttpApi.GalleryPhoto photo : result.data.list) {
                                if (photo.additional != null && photo.additional.thumbnail != null) {
                                    String thumbUrl = photo.additional.thumbnail.mUrl;
                                    if (thumbUrl == null) {
                                        thumbUrl = photo.additional.thumbnail.sUrl;
                                    }
                                    if (thumbUrl != null) {
                                        if (!thumbUrl.startsWith("http") && baseUrl != null) {
                                            thumbUrl = baseUrl + thumbUrl;
                                        }
                                        thumbUrls.add(thumbUrl);
                                    }
                                }
                            }
                            mediaItem.setPreviewThumbUrls(thumbUrls);
                            previewThumbnailCache.put(dateStr, thumbUrls);
                        }
                    }
                    if (onComplete != null) onComplete.run();
                }

                @Override
                public void onFailure(Call<FnHttpApi.GalleryListResponse> call, Throwable t) {
                    Log.e(TAG, "加载预览缩略图失败: " + dateStr, t);
                    if (onComplete != null) onComplete.run();
                }
            });
    }

    private void schedulePersonLazyLoad(int centerIndex) {
        personLastVisibleIdx = centerIndex;
        lazyLoadHandler.removeCallbacksAndMessages(null);
        lazyLoadHandler.postDelayed(() -> {
            if (centerIndex == personLastVisibleIdx) {
                loadVisiblePersonPreviews(centerIndex);
            }
        }, PREVIEW_LOAD_DELAY);
    }

    private void loadVisiblePersonPreviews(int centerIndex) {
        if (personDateItems.isEmpty() || personTimelineItemsList.isEmpty()) return;

        int start = Math.max(0, centerIndex - VISIBLE_RANGE_BUFFER);
        int end = Math.min(personDateItems.size(), centerIndex + VISIBLE_RANGE_BUFFER + 1);

        for (int i = start; i < end; i++) {
            if (!personLoadedIdx.contains(i)) {
                personLoadedIdx.add(i);
                final MediaItem mediaItem = personDateItems.get(i);
                final FnHttpApi.PersonTimelineItem personItem = personTimelineItemsList.get(i);

                if (personItem.itemCount > 0) {
                    if (mediaItem.getPreviewThumbUrls() != null && !mediaItem.getPreviewThumbUrls().isEmpty()) {
                        notifyItemChanged(mediaItem);
                    } else {
                        loadPersonDatePreviewThumbnails(mediaItem, personItem, () -> {
                            notifyItemChanged(mediaItem);
                        });
                    }
                }
            }
        }
    }

    private void loadPersonDatePreviewThumbnails(final MediaItem mediaItem,
                                                  final FnHttpApi.PersonTimelineItem personItem,
                                                  final Runnable onComplete) {
        if (api == null || token == null || token.isEmpty() || currentPersonId < 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        String dateStr = mediaItem.getDateStr();
        String dateTime = dateStr.replace("-", ":");
        String startTime = dateTime + " 00:00:00";
        String endTime = dateTime + " 23:59:59";
        int limit = Math.min(personItem.itemCount, 4);

        String params = "album_id=" + currentPersonId
            + "&endTime=" + endTime
            + "&end_time=" + endTime
            + "&limit=" + limit
            + "&offset=0"
            + "&personId=" + currentPersonId
            + "&startTime=" + startTime
            + "&start_time=" + startTime;
        String path = "/p/api/v1/ai-person/photoLibrary/list";
        String authx = FnAuthUtils.generateAuthX(path, "GET", params);

        api.getPersonPhotos(token, authx, currentPersonId, startTime, endTime, limit, 0, currentPersonId, startTime, endTime)
            .enqueue(new Callback<FnHttpApi.GalleryListResponse>() {
                @Override
                public void onResponse(Call<FnHttpApi.GalleryListResponse> call,
                                       Response<FnHttpApi.GalleryListResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        FnHttpApi.GalleryListResponse result = response.body();
                        if (result.code == 0 && result.data != null && result.data.list != null) {
                            List<String> thumbUrls = new ArrayList<>();
                            for (FnHttpApi.GalleryPhoto photo : result.data.list) {
                                if (photo.additional != null && photo.additional.thumbnail != null) {
                                    String thumbUrl = photo.additional.thumbnail.mUrl;
                                    if (thumbUrl == null) {
                                        thumbUrl = photo.additional.thumbnail.sUrl;
                                    }
                                    if (thumbUrl != null) {
                                        if (!thumbUrl.startsWith("http") && baseUrl != null) {
                                            thumbUrl = baseUrl + thumbUrl;
                                        }
                                        thumbUrls.add(thumbUrl);
                                    }
                                }
                            }
                            mediaItem.setPreviewThumbUrls(thumbUrls);
                        }
                    }
                    if (onComplete != null) onComplete.run();
                }

                @Override
                public void onFailure(Call<FnHttpApi.GalleryListResponse> call, Throwable t) {
                    if (onComplete != null) onComplete.run();
                }
            });
    }

    public void loadPhotosByDate(String dateStr, int itemCount) {
        // 保存时间线的滚动位置
        saveTimelinePosition();
        
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        showLoadingOverlay("正在加载照片...");
        Log.d(TAG, "Loading photos for date: " + dateStr);

        String dateTime = dateStr.replace("-", ":");
        String startTime = dateTime + " 00:00:00";
        String endTime = dateTime + " 23:59:59";
        int limit = itemCount;
        int offset = 0;
        String mode = "index";

        StringBuilder paramsBuilder = new StringBuilder();
        paramsBuilder.append("end_time=").append(endTime);
        paramsBuilder.append("&limit=").append(limit);
        paramsBuilder.append("&mode=").append(mode);
        paramsBuilder.append("&offset=").append(offset);
        paramsBuilder.append("&start_time=").append(startTime);
        
        String params = paramsBuilder.toString();
        String path = "/p/api/v1/gallery/getList";
        
        Log.d(TAG, "Path: " + path);
        Log.d(TAG, "Params: " + params);

        String authx = FnAuthUtils.generateAuthX(path, "GET", params);

        api.getPhotosByTimeRange(token, authx, startTime, endTime, limit, offset, mode, null)
            .enqueue(new Callback<FnHttpApi.GalleryListResponse>() {
                @Override
                public void onResponse(Call<FnHttpApi.GalleryListResponse> call,
                                       Response<FnHttpApi.GalleryListResponse> response) {
                    hideLoadingOverlay();
                    if (response.isSuccessful() && response.body() != null) {
                        FnHttpApi.GalleryListResponse result = response.body();
                        if (result.code == 0 && result.data != null && result.data.list != null) {
                            displayPhotosByDate(dateStr, result.data.list);
                        }
                    }
                }

                @Override
                public void onFailure(Call<FnHttpApi.GalleryListResponse> call, Throwable t) {
                    Log.e(TAG, "加载照片列表失败", t);
                    hideLoadingOverlay();
                }
            });
    }

    private void displayPhotosByDate(String dateStr, List<FnHttpApi.GalleryPhoto> photos) {
        isPhotoListView = true;
        mRowsAdapter.clear();

        HeaderItem header = new HeaderItem(dateStr + " · " + photos.size() + "张");
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(mCardPresenter);

        currentMediaList = new ArrayList<>();

        for (FnHttpApi.GalleryPhoto photo : photos) {
            String thumbUrl = null;
            String originalUrl = null;

            if (photo.additional != null && photo.additional.thumbnail != null) {
                FnHttpApi.GalleryThumbnail thumbnail = photo.additional.thumbnail;
                
                thumbUrl = thumbnail.mUrl != null ? baseUrl + thumbnail.mUrl : (thumbnail.sUrl != null ? baseUrl + thumbnail.sUrl : null);
                
                originalUrl = thumbnail.originalUrl != null ? baseUrl + thumbnail.originalUrl : (thumbnail.mUrl != null ? baseUrl + thumbnail.mUrl : null);
            }

            MediaItem item = new MediaItem(
                String.valueOf(photo.id),
                photo.fileName,
                photo.category,
                thumbUrl,
                originalUrl
            );
            currentMediaList.add(item);
            listRowAdapter.add(item);
        }

        mRowsAdapter.add(new ListRow(header, listRowAdapter));
        
        // 恢复照片列表的位置
        if (savedPhotoListPosition >= 0) {
            lazyLoadHandler.postDelayed(() -> {
                try {
                    setSelectedPosition(savedPhotoListPosition);
                    Log.d(TAG, "Restored photo list position: " + savedPhotoListPosition);
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring photo list position", e);
                }
            }, 300);
        }
    }

    public void loadFavorites() {
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        showLoadingOverlay("正在加载收藏...");
        String params = "is_collect=1";
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/gallery/timeline", "GET", params);

        api.getTimeline(token, authx, 1).enqueue(new Callback<FnHttpApi.TimelineResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.TimelineResponse> call,
                                   Response<FnHttpApi.TimelineResponse> response) {
                hideLoadingOverlay();
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.TimelineResponse result = response.body();
                    if (result.code == 0 && result.data != null && result.data.list != null) {
                        displayTimeline(result.data.list);
                    } else {
                        showEmptyState(TvUiText.emptyMessage("favorites"));
                    }
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.TimelineResponse> call, Throwable t) {
                Log.e(TAG, "加载收藏失败", t);
                hideLoadingOverlay();
                showEmptyState(TvUiText.errorMessage("photos"));
            }
        });
    }

    public void loadRecent() {
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        showLoadingOverlay("正在加载最近照片...");
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/explore/recent_timeline", "GET", null);

        api.getRecentTimeline(token, authx).enqueue(new Callback<FnHttpApi.TimelineResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.TimelineResponse> call,
                                   Response<FnHttpApi.TimelineResponse> response) {
                hideLoadingOverlay();
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.TimelineResponse result = response.body();
                    if (result.code == 0 && result.data != null && result.data.list != null) {
                        displayTimeline(result.data.list);
                    } else {
                        showEmptyState(TvUiText.emptyMessage("photos"));
                    }
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.TimelineResponse> call, Throwable t) {
                Log.e(TAG, "加载最近照片失败", t);
                hideLoadingOverlay();
                showEmptyState(TvUiText.errorMessage("photos"));
            }
        });
    }

    public void loadPlaces() {
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        showLoadingOverlay("正在加载地点...");
        String params = "offset=0&limit=-1";
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/explore/geos", "GET", params);

        api.getGeos(token, authx, 0, -1).enqueue(new Callback<FnHttpApi.GeoListResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.GeoListResponse> call,
                                   Response<FnHttpApi.GeoListResponse> response) {
                hideLoadingOverlay();
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.GeoListResponse result = response.body();
                    if (result.code == 0 && result.data != null && result.data.list != null) {
                        displayPlaces(result.data.list);
                    } else {
                        showEmptyState(TvUiText.emptyMessage("places"));
                    }
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.GeoListResponse> call, Throwable t) {
                Log.e(TAG, "加载地点失败", t);
                hideLoadingOverlay();
                showEmptyState(TvUiText.errorMessage("photos"));
            }
        });
    }

    private void displayPlaces(List<FnHttpApi.GeoItem> places) {
        isPhotoListView = false;
        isHomeView = false;
        timelineItems = null;
        savedAlbumList = null;
        savedPlacesList = new ArrayList<>(places);
        mRowsAdapter.clear();

        int itemsPerRow = 6;
        int totalRows = (int) Math.ceil((double) places.size() / itemsPerRow);

        for (int row = 0; row < totalRows; row++) {
            int start = row * itemsPerRow;
            int end = Math.min(start + itemsPerRow, places.size());

            HeaderItem header = row == 0 ? new HeaderItem("地点 · " + places.size()) : null;
            ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(mCardPresenter);

            for (int i = start; i < end; i++) {
                FnHttpApi.GeoItem geo = places.get(i);
                String posterUrl = geo.posterUrl != null ? baseUrl + geo.posterUrl : null;
                MediaItem item = new MediaItem(
                    geo.country + "/" + geo.city,
                    geo.city + "，" + geo.country,
                    "place",
                    posterUrl,
                    posterUrl
                );
                item.setDateStr(geo.itemCount + "张照片");
                rowAdapter.add(item);
            }

            mRowsAdapter.add(new ListRow(header, rowAdapter));
        }
    }

    private void displayPhotoList(String title, List<FnHttpApi.GalleryPhoto> photos) {
        isPhotoListView = true;
        timelineItems = null;
        mRowsAdapter.clear();

        HeaderItem header = new HeaderItem(title + " · " + photos.size());
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(mCardPresenter);

        currentMediaList = new ArrayList<>();

        for (FnHttpApi.GalleryPhoto photo : photos) {
            String thumbUrl = null;
            String originalUrl = null;

                if (photo.additional != null && photo.additional.thumbnail != null) {
                FnHttpApi.GalleryThumbnail thumbnail = photo.additional.thumbnail;
                thumbUrl = thumbnail.mUrl != null ? baseUrl + thumbnail.mUrl : (thumbnail.sUrl != null ? baseUrl + thumbnail.sUrl : null);
                originalUrl = thumbnail.originalUrl != null ? baseUrl + thumbnail.originalUrl : (thumbnail.mUrl != null ? baseUrl + thumbnail.mUrl : null);
            }

            MediaItem item = new MediaItem(
                String.valueOf(photo.id),
                photo.fileName,
                photo.category,
                thumbUrl,
                originalUrl
            );
            currentMediaList.add(item);
            listRowAdapter.add(item);
        }

        mRowsAdapter.add(new ListRow(header, listRowAdapter));
    }

    private void showEmptyState(String message) {
        clearHero();
        isPhotoListView = true;
        timelineItems = null;
        mRowsAdapter.clear();

        HeaderItem header = new HeaderItem(message);
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(mCardPresenter);
        mRowsAdapter.add(new ListRow(header, listRowAdapter));
    }

    private List<FnHttpApi.NewAlbum> savedAlbumList;
    private List<FnHttpApi.GeoItem> savedPlacesList;
    private List<FnHttpApi.PersonItem> savedPersonList;

    private void loadAlbumPhotosPage(final String albumName, final int albumId,
                                     final int offset, final List<FnHttpApi.GalleryPhoto> allPhotos) {
        String params = "album_id=" + albumId + "&sort_by=date_time&sort_direction=desc&offset=" + offset + "&limit=35";
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/album/photos", "GET", params);

        api.getAlbumPhotos(token, authx, albumId, "date_time", "desc", offset, 35)
            .enqueue(new Callback<FnHttpApi.GalleryListResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.GalleryListResponse> call,
                                   Response<FnHttpApi.GalleryListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.GalleryListResponse result = response.body();
                    if (result.code == 0 && result.data != null && result.data.list != null) {
                        allPhotos.addAll(result.data.list);
                        if (result.data.list.size() >= 35) {
                            loadAlbumPhotosPage(albumName, albumId, offset + 35, allPhotos);
                            return;
                        }
                    }
                }
                displayAlbumPhotos(albumName, allPhotos);
            }

            @Override
            public void onFailure(Call<FnHttpApi.GalleryListResponse> call, Throwable t) {
                if (!allPhotos.isEmpty()) {
                    displayAlbumPhotos(albumName, allPhotos);
                } else {
                    Log.e(TAG, "加载相册照片失败", t);
                }
            }
        });
    }

    public void loadPhotosByAlbum(String albumId, String albumName) {
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        saveTimelinePosition();
        loadAlbumPhotosPage(albumName, Integer.parseInt(albumId), 0, new ArrayList<FnHttpApi.GalleryPhoto>());
    }

    private void displayAlbumPhotos(String albumName, List<FnHttpApi.GalleryPhoto> photos) {
        isPhotoListView = true;
        timelineItems = null;
        mRowsAdapter.clear();

        currentMediaList = new ArrayList<>();

        int itemsPerRow = 6;
        int totalRows = (int) Math.ceil((double) photos.size() / itemsPerRow);

        for (int row = 0; row < totalRows; row++) {
            int start = row * itemsPerRow;
            int end = Math.min(start + itemsPerRow, photos.size());

            HeaderItem header = row == 0 ? new HeaderItem(albumName + " · " + photos.size() + "张") : null;
            ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(mCardPresenter);

            for (int i = start; i < end; i++) {
                FnHttpApi.GalleryPhoto photo = photos.get(i);

                String thumbUrl = null;
                String mediaUrl = null;

                if (photo.additional != null && photo.additional.thumbnail != null) {
                    FnHttpApi.GalleryThumbnail thumbnail = photo.additional.thumbnail;
                    thumbUrl = thumbnail.mUrl != null ? baseUrl + thumbnail.mUrl : (thumbnail.sUrl != null ? baseUrl + thumbnail.sUrl : null);
                    mediaUrl = thumbnail.originalUrl != null ? baseUrl + thumbnail.originalUrl : null;
                }

                MediaItem item = new MediaItem(
                    String.valueOf(photo.id),
                    photo.fileName,
                    photo.category,
                    thumbUrl,
                    mediaUrl
                );
                currentMediaList.add(item);
                rowAdapter.add(item);
            }

            mRowsAdapter.add(new ListRow(header, rowAdapter));
        }
    }

    public void loadPhotosByGeo(MediaItem placeItem) {
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        showLoadingOverlay("正在加载地点照片...");

        String[] parts = placeItem.getId().split("/", 2);
        final String country = parts.length > 0 ? parts[0] : "";
        final String city = parts.length > 1 ? parts[1] : "";
        final String placeName = placeItem.getTitle();

        final String requestBody = "{\"keyword\":\"\",\"filters\":[{\"filterName\":\"photo_location\",\"filterValue\":\"" + country + "\",\"subFilters\":[{\"filterName\":\"" + country + "\",\"filterValue\":\"" + city + "\"}]}],\"antiFilters\":[]}";
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/search/results", "POST", requestBody);
        final String url = baseUrl + "/p/api/v1/search/results";

        Log.d(TAG, "=== 地点照片请求(OkHttp) ===");
        Log.d(TAG, "URL: " + url);
        Log.d(TAG, "Method: POST");
        Log.d(TAG, "Body: " + requestBody);
        Log.d(TAG, "authx: " + authx);

        MediaType JSON = MediaType.parse("application/json; charset=UTF-8");
        RequestBody body = RequestBody.create(JSON, requestBody);

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .header("accesstoken", token)
            .header("authx", authx)
            .build();

        OkHttpClient client = HttpClientProvider.getClient(getActivity());
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "地点照片请求网络异常", e);
                getActivity().runOnUiThread(() -> {
                    hideLoadingOverlay();
                    showEmptyState("网络异常: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                try {
                    String responseBody = response.body() != null ? response.body().string() : null;
                    Log.d(TAG, "Response code: " + response.code());
                    Log.d(TAG, "Response body: " + responseBody);

                    if (response.isSuccessful() && responseBody != null) {
                        Gson gson = new Gson();
                        FnHttpApi.SearchResultsResponse result = gson.fromJson(responseBody, FnHttpApi.SearchResultsResponse.class);
                        if (result.code == 0 && result.data != null && result.data.list != null) {
                            final List<FnHttpApi.GalleryPhoto> photos = result.data.list;
                            getActivity().runOnUiThread(() -> {
                                hideLoadingOverlay();
                                displayGeoPhotos(placeName, photos);
                            });
                        } else {
                            Log.e(TAG, "地点照片返回异常: code=" + result.code);
                            getActivity().runOnUiThread(() -> {
                                hideLoadingOverlay();
                                showEmptyState(TvUiText.emptyMessage("photos"));
                            });
                        }
                    } else {
                        Log.e(TAG, "地点照片请求失败: code=" + response.code() + " body=" + responseBody);
                        getActivity().runOnUiThread(() -> {
                            hideLoadingOverlay();
                            showEmptyState(TvUiText.errorMessage("photos") + " (" + response.code() + ")");
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析响应异常", e);
                    getActivity().runOnUiThread(() -> {
                        hideLoadingOverlay();
                        showEmptyState(TvUiText.errorMessage("photos"));
                    });
                } finally {
                    response.close();
                }
            }
        });
    }

    private void showLoadingOverlay(String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showLoading(message);
                }
            });
        }
    }

    private void hideLoadingOverlay() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).hideLoading();
                }
            });
        }
    }

    private void displayGeoPhotos(String placeName, List<FnHttpApi.GalleryPhoto> photos) {
        isPhotoListView = true;
        timelineItems = null;
        mRowsAdapter.clear();

        currentMediaList = new ArrayList<>();

        int itemsPerRow = 6;
        int totalRows = (int) Math.ceil((double) photos.size() / itemsPerRow);

        for (int row = 0; row < totalRows; row++) {
            int start = row * itemsPerRow;
            int end = Math.min(start + itemsPerRow, photos.size());

            HeaderItem header = row == 0 ? new HeaderItem(placeName + " · " + photos.size() + "张") : null;
            ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(mCardPresenter);

            for (int i = start; i < end; i++) {
                FnHttpApi.GalleryPhoto photo = photos.get(i);

                String thumbUrl = null;
                String mediaUrl = null;

                if (photo.additional != null && photo.additional.thumbnail != null) {
                    FnHttpApi.GalleryThumbnail thumbnail = photo.additional.thumbnail;
                    thumbUrl = thumbnail.mUrl != null ? baseUrl + thumbnail.mUrl : (thumbnail.sUrl != null ? baseUrl + thumbnail.sUrl : null);
                    mediaUrl = thumbnail.originalUrl != null ? baseUrl + thumbnail.originalUrl : null;
                }

                MediaItem item = new MediaItem(
                    String.valueOf(photo.id),
                    photo.fileName,
                    photo.category,
                    thumbUrl,
                    mediaUrl
                );
                currentMediaList.add(item);
                rowAdapter.add(item);
            }

            mRowsAdapter.add(new ListRow(header, rowAdapter));
        }
    }

    public void loadPeople() {
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        showLoadingOverlay("正在加载人物...");
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/ai-person/list", "GET", "getAll=true&limit=-1&orderBy=0");

        api.getPersonList(token, authx, true, -1, 0).enqueue(new Callback<FnHttpApi.PersonListResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.PersonListResponse> call,
                                   Response<FnHttpApi.PersonListResponse> response) {
                hideLoadingOverlay();
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.PersonListResponse result = response.body();
                    if (result.code == 0 && result.data != null && result.data.list != null) {
                        displayPeople(result.data.list);
                    } else {
                        showEmptyState(TvUiText.emptyMessage("people"));
                    }
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.PersonListResponse> call, Throwable t) {
                Log.e(TAG, "加载人物失败", t);
                hideLoadingOverlay();
                showEmptyState(TvUiText.errorMessage("photos"));
            }
        });
    }

    private void displayPeople(List<FnHttpApi.PersonItem> persons) {
        isPhotoListView = false;
        isHomeView = false;
        timelineItems = null;
        savedPersonList = new ArrayList<>(persons);
        mRowsAdapter.clear();

        int itemsPerRow = 6;
        int totalRows = (int) Math.ceil((double) persons.size() / itemsPerRow);

        for (int row = 0; row < totalRows; row++) {
            int start = row * itemsPerRow;
            int end = Math.min(start + itemsPerRow, persons.size());

            HeaderItem header = row == 0 ? new HeaderItem("人物 · " + persons.size()) : null;
            ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(mCardPresenter);

            for (int i = start; i < end; i++) {
                FnHttpApi.PersonItem person = persons.get(i);

                String thumbUrl = person.faceId > 0 ? baseUrl + "/p/api/v1/stream/face/" + person.faceId : null;

                MediaItem item = new MediaItem(
                    String.valueOf(person.id),
                    person.name,
                    "person",
                    thumbUrl,
                    null
                );
                item.setPhotoCount(person.itemCount);
                rowAdapter.add(item);
            }

            mRowsAdapter.add(new ListRow(header, rowAdapter));
        }
    }

    public void loadPersonTimeline(int personId, String personName) {
        if (api == null || token == null || token.isEmpty()) {
            Log.e(TAG, "API未初始化");
            return;
        }

        currentPersonId = personId;
        currentPersonName = personName;
        savedPersonTimelineItems = null;

        showLoadingOverlay("正在加载 " + personName + " 的时间线...");
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/ai-person/photoLibrary/timeLine", "GET", "id=" + personId);

        api.getPersonTimeline(token, authx, personId).enqueue(new Callback<FnHttpApi.PersonTimelineResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.PersonTimelineResponse> call,
                                   Response<FnHttpApi.PersonTimelineResponse> response) {
                hideLoadingOverlay();
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.PersonTimelineResponse result = response.body();
                    if (result.code == 0 && result.data != null && result.data.list != null) {
                        displayPersonTimeline(result.data.list);
                    } else {
                        showEmptyState(personName + " 还没有照片");
                    }
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.PersonTimelineResponse> call, Throwable t) {
                Log.e(TAG, "加载人物时间线失败", t);
                hideLoadingOverlay();
                showEmptyState(TvUiText.errorMessage("photos"));
            }
        });
    }

    private void displayPersonTimeline(List<FnHttpApi.PersonTimelineItem> items) {
        isPhotoListView = false;
        isHomeView = false;
        mRowsAdapter.clear();
        savedPersonTimelineItems = new ArrayList<>(items);
        personDateItems.clear();
        personTimelineItemsList.clear();
        personLoadedIdx.clear();

        String currentYearMonth = "";
        ArrayObjectAdapter currentRowAdapter = null;

        for (FnHttpApi.PersonTimelineItem item : items) {
            String yearMonth = item.year + "年" + item.month + "月";
            String dateStr = item.year + "-" + String.format("%02d", item.month) + "-" + String.format("%02d", item.day);
            String title = item.month + "月" + item.day + "日";
            String suffix = " · " + item.itemCount + "张";

            if (!yearMonth.equals(currentYearMonth)) {
                currentYearMonth = yearMonth;
                String headerTitle = currentPersonName != null ? currentPersonName + " · " + yearMonth : yearMonth;
                HeaderItem header = new HeaderItem(headerTitle);
                currentRowAdapter = new ArrayObjectAdapter(mCardPresenter);
                mRowsAdapter.add(new ListRow(header, currentRowAdapter));
            }

            MediaItem mediaItem = new MediaItem(dateStr, title + suffix, item.itemCount);
            mediaItem.setType("person_date");
            currentRowAdapter.add(mediaItem);
            personDateItems.add(mediaItem);
            personTimelineItemsList.add(item);
        }

        // 触发第一个可见区域的预览加载
        lazyLoadHandler.postDelayed(() -> {
            loadVisiblePersonPreviews(0);
        }, 300);
    }

    public void loadPersonPhotosByDate(String dateStr, int itemCount) {
        if (api == null || token == null || token.isEmpty() || currentPersonId < 0) {
            Log.e(TAG, "API未初始化或未选择人物");
            return;
        }

        showLoadingOverlay("正在加载照片...");

        String dateTime = dateStr.replace("-", ":");
        String startTime = dateTime + " 00:00:00";
        String endTime = dateTime + " 23:59:59";
        int limit = itemCount;
        int offset = 0;

        String params = "album_id=" + currentPersonId
            + "&endTime=" + endTime
            + "&end_time=" + endTime
            + "&limit=" + limit
            + "&offset=" + offset
            + "&personId=" + currentPersonId
            + "&startTime=" + startTime
            + "&start_time=" + startTime;
        String path = "/p/api/v1/ai-person/photoLibrary/list";
        String authx = FnAuthUtils.generateAuthX(path, "GET", params);

        api.getPersonPhotos(token, authx, currentPersonId, startTime, endTime, limit, offset, currentPersonId, startTime, endTime)
            .enqueue(new Callback<FnHttpApi.GalleryListResponse>() {
                @Override
                public void onResponse(Call<FnHttpApi.GalleryListResponse> call,
                                       Response<FnHttpApi.GalleryListResponse> response) {
                    hideLoadingOverlay();
                    if (response.isSuccessful() && response.body() != null) {
                        FnHttpApi.GalleryListResponse result = response.body();
                        if (result.code == 0 && result.data != null && result.data.list != null) {
                            displayPersonPhotos(dateStr, result.data.list);
                        }
                    }
                }

                @Override
                public void onFailure(Call<FnHttpApi.GalleryListResponse> call, Throwable t) {
                    Log.e(TAG, "加载人物照片失败", t);
                    hideLoadingOverlay();
                }
            });
    }

    private void displayPersonPhotos(String dateStr, List<FnHttpApi.GalleryPhoto> photos) {
        isPhotoListView = true;
        mRowsAdapter.clear();

        String title = (currentPersonName != null ? currentPersonName : "") + " · " + dateStr + " · " + photos.size() + "张";
        HeaderItem header = new HeaderItem(title);
        ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(mCardPresenter);

        currentMediaList = new ArrayList<>();

        for (FnHttpApi.GalleryPhoto photo : photos) {
            String thumbUrl = null;
            String mediaUrl = null;

            if (photo.additional != null && photo.additional.thumbnail != null) {
                FnHttpApi.GalleryThumbnail thumbnail = photo.additional.thumbnail;
                thumbUrl = thumbnail.mUrl != null ? baseUrl + thumbnail.mUrl : (thumbnail.sUrl != null ? baseUrl + thumbnail.sUrl : null);
                mediaUrl = thumbnail.originalUrl != null ? baseUrl + thumbnail.originalUrl : null;
            }

            MediaItem item = new MediaItem(
                String.valueOf(photo.id),
                photo.fileName,
                photo.category,
                thumbUrl,
                mediaUrl
            );
            currentMediaList.add(item);
            rowAdapter.add(item);
        }

        mRowsAdapter.add(new ListRow(header, rowAdapter));
    }

    private void openMediaDetail(MediaItem mediaItem) {
        if (currentMediaList == null || currentMediaList.isEmpty()) {
            currentMediaList = new ArrayList<>();
            currentMediaList.add(mediaItem);
        }

        int index = 0;
        for (int i = 0; i < currentMediaList.size(); i++) {
            if (currentMediaList.get(i).getId().equals(mediaItem.getId())) {
                index = i;
                break;
            }
        }

        MediaListHolder.set(currentMediaList);
        Intent intent = new Intent(getActivity(), MediaDetailActivity.class);
        intent.putExtra("CURRENT_INDEX", index);
        startActivity(intent);
    }

    private void openFolderBrowse(MediaItem folderItem) {
        // 获取文件夹路径
        String folderPath = folderItem.getMediaUrl(); // 我们之前将路径保存在 mediaUrl 中
        if (folderPath == null || folderPath.isEmpty()) {
            Log.e(TAG, "文件夹路径为空");
            return;
        }

        Intent intent = new Intent(getActivity(), FolderBrowseActivity.class);
        intent.putExtra("FOLDER_PATH", folderPath);
        intent.putExtra("FOLDER_NAME", folderItem.getTitle());
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        lazyLoadHandler.removeCallbacksAndMessages(null);
        positionHandler.removeCallbacksAndMessages(null);
        if (tokenChangeListener != null && getActivity() != null) {
            getActivity().getSharedPreferences("fn_photo_prefs", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(tokenChangeListener);
        }
    }
}
