package com.fnphoto.tv;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.fnphoto.tv.api.FnAuthUtils;
import com.fnphoto.tv.api.FnHttpApi;
import com.fnphoto.tv.api.HttpClientProvider;
import com.fnphoto.tv.cache.CachedImageLoader;
import com.fnphoto.tv.cache.ImageCacheManager;
import com.fnphoto.tv.player.AuthenticatedHttpDataSourceFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MediaDetailActivity extends FragmentActivity {
    private static final String TAG = "MediaDetailActivity";
    private static final long DEBOUNCE_TIME = 300;
    private static final long SLIDESHOW_INTERVAL = 5000;
    private static final float DEFAULT_ZOOM_SCALE = 2.35f;
    private static final float PAN_STEP = 120.0f;

    private FrameLayout container;
    private ImageView backgroundImageView;
    private View backgroundScrim;
    private ImageView imageView;
    private PlayerView playerView;
    private SimpleExoPlayer player;
    private View infoOverlay;
    private TextView tvInfoContent;
    private TextView tvSlideshowIndicator;
    private TextView tvActionHint;
    private ProgressBar loadingIndicator;

    private List<MediaItem> mediaList;
    private int currentIndex;
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Handler slideshowHandler = new Handler(Looper.getMainLooper());
    private Handler actionHintHandler = new Handler(Looper.getMainLooper());
    private boolean canSwitch = true;
    private boolean isVideoPlaying = false;
    private MediaItem currentVideoItem;
    private boolean slideshowActive = false;
    private boolean infoVisible = false;
    private float currentScale = 1.0f;
    private float translateX = 0.0f;
    private float translateY = 0.0f;
    private boolean isZoomed = false;
    private FnHttpApi api;
    private String baseUrl;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(container);

        mediaList = (ArrayList<MediaItem>) MediaListHolder.get();
        MediaListHolder.clear();
        currentIndex = getIntent().getIntExtra("CURRENT_INDEX", 0);

        if (mediaList == null || mediaList.isEmpty()) {
            Toast.makeText(this, "没有可显示的媒体", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("fn_photo_prefs", Context.MODE_PRIVATE);
        baseUrl = prefs.getString("nas_url", "");
        token = prefs.getString("api_token", "");

        if (baseUrl != null && !baseUrl.isEmpty()) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl + "/")
                    .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                    .client(HttpClientProvider.getClient(this))
                    .build();
            api = retrofit.create(FnHttpApi.class);
        }

        showCurrentMedia();
    }

    private void showCurrentMedia() {
        if (currentIndex < 0 || currentIndex >= mediaList.size()) return;

        MediaItem item = mediaList.get(currentIndex);
        Log.d(TAG, "Showing media: " + item.getTitle() + " type: " + item.getType());

        isVideoPlaying = false;
        currentVideoItem = null;
        currentScale = 1.0f;
        translateX = 0.0f;
        translateY = 0.0f;
        isZoomed = false;

        container.removeAllViews();
        backgroundImageView = null;
        backgroundScrim = null;
        imageView = null;
        tvActionHint = null;
        loadingIndicator = null;
        actionHintHandler.removeCallbacksAndMessages(null);
        hideInfoOverlay();
        showLoading();

        if (player != null) {
            player.release();
            player = null;
        }

        if ("video".equals(item.getType())) {
            showVideoPreview(item);
        } else {
            showPhoto(item);
        }
    }

    private void showLoading() {
        View panel = container.findViewById(R.id.detail_loading_panel);
        if (panel == null) {
            FrameLayout loadingPanel = new FrameLayout(this);
            loadingPanel.setId(R.id.detail_loading_panel);
            loadingPanel.setBackgroundResource(R.drawable.bg_overlay_panel);
            FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(112, 112);
            panelParams.gravity = Gravity.CENTER;
            loadingPanel.setLayoutParams(panelParams);

            loadingIndicator = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
            loadingIndicator.setIndeterminate(true);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            loadingIndicator.setLayoutParams(params);
            loadingPanel.addView(loadingIndicator);
            container.addView(loadingPanel);
            panel = loadingPanel;
        }
        panel.setVisibility(View.VISIBLE);
        panel.bringToFront();
    }

    private void hideLoading() {
        View panel = container.findViewById(R.id.detail_loading_panel);
        if (panel != null) {
            panel.setVisibility(View.GONE);
        }
    }

    private void bringLoadingToFront() {
        View panel = container.findViewById(R.id.detail_loading_panel);
        if (panel != null) {
            panel.bringToFront();
        }
    }

    private void showActionHint(String text) {
        actionHintHandler.removeCallbacksAndMessages(null);
        if (tvActionHint == null) {
            tvActionHint = new TextView(this);
            tvActionHint.setTextColor(Color.parseColor("#EDE5D2"));
            tvActionHint.setTextSize(15);
            tvActionHint.setBackgroundResource(R.drawable.bg_overlay_panel);
            tvActionHint.setPadding(20, 10, 20, 10);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.setMargins(0, 0, 0, 36);
            container.addView(tvActionHint, params);
        }
        if (tvActionHint.getParent() == null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.setMargins(0, 0, 0, 36);
            container.addView(tvActionHint, params);
        }
        tvActionHint.setText(text);
        tvActionHint.animate().cancel();
        tvActionHint.setAlpha(0.0f);
        tvActionHint.setVisibility(View.VISIBLE);
        tvActionHint.bringToFront();
        tvActionHint.animate().alpha(1.0f).setDuration(120).start();
        actionHintHandler.postDelayed(() -> {
            if (tvActionHint != null) {
                tvActionHint.animate()
                        .alpha(0.0f)
                        .setDuration(240)
                        .withEndAction(() -> {
                            if (tvActionHint != null) {
                                tvActionHint.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        }, 4200);
    }

    private void addImageStage() {
        backgroundImageView = new ImageView(this);
        backgroundImageView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        backgroundImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundImageView.setAlpha(0.56f);
        container.addView(backgroundImageView);

        backgroundScrim = new View(this);
        backgroundScrim.setBackgroundColor(Color.parseColor("#B810100E"));
        backgroundScrim.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        container.addView(backgroundScrim);

        imageView = new ImageView(this);
        imageView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        container.addView(imageView);
        bringLoadingToFront();
    }

    private void setStageBitmap(Bitmap bitmap) {
        if (backgroundImageView != null) {
            backgroundImageView.setImageBitmap(bitmap);
        }
        if (imageView != null) {
            imageView.setImageBitmap(bitmap);
            if (isZoomed) {
                applyZoom();
            }
        }
    }

    private void showPhoto(MediaItem item) {
        addImageStage();

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        // 保持加载动画在最上层
        if (loadingIndicator != null) {
            loadingIndicator.bringToFront();
        }

        String mediaUrl = item.getMediaUrl();

        // 检查原图是否已有缓存
        ImageCacheManager cacheManager = ImageCacheManager.getInstance(this);
        boolean originalCached = mediaUrl != null && !mediaUrl.isEmpty() && cacheManager.isCacheValid(mediaUrl);

        if (originalCached) {
            // 原图已缓存：直接加载，不要缩略图占位
            CachedImageLoader.loadImage(this, mediaUrl, token, screenWidth, screenHeight,
                    new CachedImageLoader.ImageLoadCallback() {
                        @Override
                        public void onBitmapLoaded(Bitmap bitmap) {
                            hideLoading();
                            setStageBitmap(bitmap);
                        }
                        @Override
                        public void onLoadFailed() {
                            hideLoading();
                            Toast.makeText(MediaDetailActivity.this, "图片加载失败", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            // 原图未缓存：先显示缩略图占位，加载动画保持在最上层
            String thumbUrl = item.getThumbnailUrl();
            if (thumbUrl != null && !thumbUrl.isEmpty()) {
                CachedImageLoader.loadImage(this, thumbUrl, token, screenWidth, screenHeight,
                        new CachedImageLoader.ImageLoadCallback() {
                            @Override
                            public void onBitmapLoaded(Bitmap bitmap) {
                                setStageBitmap(bitmap);
                                // 缩略图设完后，重新把加载动画置于最上层
                                if (loadingIndicator != null) {
                                    loadingIndicator.bringToFront();
                                }
                            }
                            @Override
                            public void onLoadFailed() {}
                        });
            }

            if (mediaUrl != null && !mediaUrl.isEmpty()) {
                CachedImageLoader.loadImage(this, mediaUrl, token, screenWidth, screenHeight,
                        new CachedImageLoader.ImageLoadCallback() {
                            @Override
                            public void onBitmapLoaded(Bitmap bitmap) {
                                hideLoading();
                                setStageBitmap(bitmap);
                            }
                            @Override
                            public void onLoadFailed() {
                                hideLoading();
                                Toast.makeText(MediaDetailActivity.this, "图片加载失败", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        }

        imageView.setOnClickListener(v -> enterZoomMode());

        // 预加载前后照片
        preloadAdjacentPhotos();
    }

    private void preloadAdjacentPhotos() {
        if (mediaList == null) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int preloadRange = 2;

        int start = Math.max(0, currentIndex - 1);
        int end = Math.min(mediaList.size(), currentIndex + preloadRange + 1);
        for (int idx = start; idx < end; idx++) {
            if (idx == currentIndex) continue;
            final int preloadIdx = idx;
            MediaItem item = mediaList.get(preloadIdx);
            String url = item.getMediaUrl();
            if (url != null && !url.isEmpty()) {
                ImageCacheManager cacheManager = ImageCacheManager.getInstance(this);
                if (!cacheManager.isCacheValid(url)) {
                    CachedImageLoader.loadImage(this, url, token, screenWidth, screenHeight,
                            new CachedImageLoader.ImageLoadCallback() {
                                @Override
                                public void onBitmapLoaded(Bitmap bitmap) {
                                    Log.d(TAG, "Preloaded photo " + preloadIdx + ": " + url);
                                }
                                @Override
                                public void onLoadFailed() {}
                            });
                }
            }
        }
    }

    private void showVideoPreview(MediaItem item) {
        currentVideoItem = item;
        String previewUrl = item.getThumbnailUrl();

        if (previewUrl == null || previewUrl.isEmpty()) {
            startVideoPlayback(item);
            return;
        }

        addImageStage();

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        CachedImageLoader.loadImage(this, previewUrl, token, screenWidth, screenHeight,
                new CachedImageLoader.ImageLoadCallback() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap) {
                        hideLoading();
                        Bitmap composite = createVideoPreviewWithPlayButton(bitmap, screenWidth, screenHeight);
                        if (backgroundImageView != null) {
                            backgroundImageView.setImageBitmap(bitmap);
                        }
                        imageView.setImageBitmap(composite);
                    }

                    @Override
                    public void onLoadFailed() {
                        hideLoading();
                        startVideoPlayback(item);
                    }
                });

        imageView.setOnClickListener(v -> {
            if (!isVideoPlaying) {
                startVideoPlayback(item);
            }
        });
        showActionHint("OK 播放视频 · 左/右切换 · 菜单查看详情");
    }

    private Bitmap createVideoPreviewWithPlayButton(Bitmap thumbnail, int width, int height) {
        Bitmap composite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(composite);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);

        int thumbWidth = thumbnail.getWidth();
        int thumbHeight = thumbnail.getHeight();

        float scale = Math.max((float) width / thumbWidth, (float) height / thumbHeight);
        float srcLeft = Math.max(0, (thumbWidth - width / scale) / 2);
        float srcTop = Math.max(0, (thumbHeight - height / scale) / 2);
        float srcRight = Math.min(thumbWidth, srcLeft + width / scale);
        float srcBottom = Math.min(thumbHeight, srcTop + height / scale);

        android.graphics.Rect srcRect = new android.graphics.Rect((int) srcLeft, (int) srcTop, (int) srcRight, (int) srcBottom);
        android.graphics.Rect dstRect = new android.graphics.Rect(0, 0, width, height);
        canvas.drawBitmap(thumbnail, srcRect, dstRect, paint);

        paint.setColor(Color.parseColor("#60000000"));
        canvas.drawRect(0, 0, width, height, paint);

        int playButtonRadius = Math.min(width, height) / 16;
        int centerX = width / 2;
        int centerY = height / 2;

        paint.setColor(Color.parseColor("#CCFFFFFF"));
        paint.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, playButtonRadius, paint);

        paint.setColor(Color.parseColor("#FF0000"));
        int triangleSize = playButtonRadius / 2;
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(centerX - triangleSize / 2, centerY - triangleSize);
        path.lineTo(centerX - triangleSize / 2, centerY + triangleSize);
        path.lineTo(centerX + triangleSize, centerY);
        path.close();
        canvas.drawPath(path, paint);

        return composite;
    }

    private void startVideoPlayback(MediaItem item) {
        isVideoPlaying = true;
        currentVideoItem = item;

        container.removeAllViews();
        backgroundImageView = null;
        backgroundScrim = null;
        imageView = null;
        tvActionHint = null;
        actionHintHandler.removeCallbacksAndMessages(null);

        String videoId = item.getId();
        String videoUrl = baseUrl + "/p/api/v1/stream/v/" + videoId;
        Log.d(TAG, "Playing video: " + videoUrl);

        if (!tryExoPlayer(videoUrl)) {
            setupMediaPlayer(videoUrl);
        }
    }

    private boolean tryExoPlayer(String videoUrl) {
        try {
            playerView = new PlayerView(this);
            playerView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            container.addView(playerView);

            player = new SimpleExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            AuthenticatedHttpDataSourceFactory dataSourceFactory =
                    new AuthenticatedHttpDataSourceFactory(this, "ExoPlayer");

            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                    Log.e(TAG, "ExoPlayer error, falling back to MediaPlayer", error);
                    hideLoading();
                    if (player != null) {
                        player.release();
                        player = null;
                    }
                    if (playerView != null) {
                        container.removeView(playerView);
                        playerView = null;
                    }
                    setupMediaPlayer(videoUrl);
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        if (slideshowActive) {
                            switchToNext();
                        }
                    }
                }
            });

            com.google.android.exoplayer2.MediaItem exoMediaItem =
                    com.google.android.exoplayer2.MediaItem.fromUri(videoUrl);
            ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(exoMediaItem);
            player.prepare(mediaSource);
            player.setPlayWhenReady(true);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "ExoPlayer init failed, falling back to MediaPlayer", e);
            if (player != null) {
                player.release();
                player = null;
            }
            if (playerView != null) {
                container.removeView(playerView);
                playerView = null;
            }
            return false;
        }
    }

    private void setupMediaPlayer(String videoUrl) {
        try {
            android.view.SurfaceView sv = new android.view.SurfaceView(this);
            sv.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            container.addView(sv);

            final android.media.MediaPlayer mp = new android.media.MediaPlayer();
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("accesstoken", token != null ? token : "");
            mp.setDataSource(this, android.net.Uri.parse(videoUrl), headers);

            sv.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(android.view.SurfaceHolder holder) {
                    mp.setDisplay(holder);
                    mp.prepareAsync();
                }
                @Override
                public void surfaceChanged(android.view.SurfaceHolder holder, int format, int w, int h) {}
                @Override
                public void surfaceDestroyed(android.view.SurfaceHolder holder) {}
            });

            mp.setOnPreparedListener(mp2 -> {
                hideLoading();
                mp2.start();
            });
            mp.setOnErrorListener((mp2, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + " extra=" + extra);
                hideLoading();
                Toast.makeText(MediaDetailActivity.this,
                        "视频播放失败", Toast.LENGTH_LONG).show();
                return true;
            });
            mp.setOnCompletionListener(mp2 -> {
                if (slideshowActive) {
                    switchToNext();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "MediaPlayer init failed", e);
            hideLoading();
            Toast.makeText(MediaDetailActivity.this,
                    "视频播放失败", Toast.LENGTH_LONG).show();
        }
    }

    private void applyZoom() {
        if (imageView != null && !isVideoPlaying) {
            if (imageView.getWidth() == 0 || imageView.getHeight() == 0) {
                imageView.post(this::applyZoom);
                return;
            }
            Drawable drawable = imageView.getDrawable();
            if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
                return;
            }
            float viewWidth = imageView.getWidth();
            float viewHeight = imageView.getHeight();
            float drawableWidth = drawable.getIntrinsicWidth();
            float drawableHeight = drawable.getIntrinsicHeight();
            float fitScale = Math.min(viewWidth / drawableWidth, viewHeight / drawableHeight);
            float scale = fitScale * currentScale;
            float left = (viewWidth - drawableWidth * scale) / 2.0f + translateX;
            float top = (viewHeight - drawableHeight * scale) / 2.0f + translateY;

            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            matrix.postTranslate(left, top);
            imageView.setScaleType(ImageView.ScaleType.MATRIX);
            imageView.setImageMatrix(matrix);
        }
    }

    private void enterZoomMode() {
        if (imageView == null || isVideoPlaying) {
            return;
        }
        boolean wasZoomed = isZoomed;
        currentScale = MediaDetailViewport.nextOkZoomScale(isZoomed, currentScale, calculateOkZoomScale());
        if (!wasZoomed) {
            translateX = 0.0f;
            translateY = 0.0f;
        }
        isZoomed = true;
        clampZoomTranslation();
        applyZoom();
    }

    private float calculateOkZoomScale() {
        if (imageView == null || imageView.getWidth() == 0 || imageView.getHeight() == 0) {
            return DEFAULT_ZOOM_SCALE;
        }
        Drawable drawable = imageView.getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return DEFAULT_ZOOM_SCALE;
        }
        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();
        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();
        float fitScale = Math.min(viewWidth / drawableWidth, viewHeight / drawableHeight);
        float fittedWidth = drawableWidth * fitScale;
        float fittedHeight = drawableHeight * fitScale;
        float coverScale = Math.max(viewWidth / fittedWidth, viewHeight / fittedHeight) + 0.25f;
        return Math.max(DEFAULT_ZOOM_SCALE, coverScale);
    }

    private void exitZoomMode() {
        currentScale = 1.0f;
        translateX = 0.0f;
        translateY = 0.0f;
        isZoomed = false;
        if (imageView != null) {
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setImageMatrix(null);
        }
    }

    private void panZoomedImage(float dx, float dy) {
        if (!isZoomed || imageView == null) {
            return;
        }
        Drawable drawable = imageView.getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return;
        }
        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();
        float fitScale = Math.min(viewWidth / drawable.getIntrinsicWidth(), viewHeight / drawable.getIntrinsicHeight());
        float scaledWidth = drawable.getIntrinsicWidth() * fitScale * currentScale;
        float scaledHeight = drawable.getIntrinsicHeight() * fitScale * currentScale;
        float maxX = Math.max(0.0f, (scaledWidth - viewWidth) / 2.0f);
        float maxY = Math.max(0.0f, (scaledHeight - viewHeight) / 2.0f);
        translateX = clamp(translateX + dx, -maxX, maxX);
        translateY = clamp(translateY + dy, -maxY, maxY);
        applyZoom();
    }

    private void clampZoomTranslation() {
        if (imageView == null) {
            return;
        }
        Drawable drawable = imageView.getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return;
        }
        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();
        float fitScale = Math.min(viewWidth / drawable.getIntrinsicWidth(), viewHeight / drawable.getIntrinsicHeight());
        float scaledWidth = drawable.getIntrinsicWidth() * fitScale * currentScale;
        float scaledHeight = drawable.getIntrinsicHeight() * fitScale * currentScale;
        float maxX = Math.max(0.0f, (scaledWidth - viewWidth) / 2.0f);
        float maxY = Math.max(0.0f, (scaledHeight - viewHeight) / 2.0f);
        translateX = clamp(translateX, -maxX, maxX);
        translateY = clamp(translateY, -maxY, maxY);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void switchToPrevious() {
        if (!canSwitch || slideshowActive) return;
        if (currentIndex > 0) {
            currentIndex--;
            showCurrentMedia();
            debounceSwitch();
        } else {
            Toast.makeText(this, "已经是第一个", Toast.LENGTH_SHORT).show();
        }
    }

    private void switchToNext() {
        if (!canSwitch) return;
        if (currentIndex < mediaList.size() - 1) {
            currentIndex++;
            showCurrentMedia();
            if (!slideshowActive) debounceSwitch();
        } else {
            if (slideshowActive) {
                stopSlideshow();
                Toast.makeText(this, "幻灯片播放结束", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已经是最后一个", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void debounceSwitch() {
        canSwitch = false;
        debounceHandler.postDelayed(() -> canSwitch = true, DEBOUNCE_TIME);
    }

    private void toggleSlideshow() {
        if (slideshowActive) {
            stopSlideshow();
        } else {
            startSlideshow();
        }
    }

    private void startSlideshow() {
        if (mediaList.size() <= 1) {
            Toast.makeText(this, "需要至少2张照片才能播放幻灯片", Toast.LENGTH_SHORT).show();
            return;
        }
        slideshowActive = true;
        showSlideshowIndicator(true);
        Toast.makeText(this, "幻灯片已开始", Toast.LENGTH_SHORT).show();
        slideshowHandler.postDelayed(slideshowRunnable, SLIDESHOW_INTERVAL);
    }

    private void stopSlideshow() {
        slideshowActive = false;
        slideshowHandler.removeCallbacks(slideshowRunnable);
        showSlideshowIndicator(false);
        Toast.makeText(this, "幻灯片已停止", Toast.LENGTH_SHORT).show();
    }

    private final Runnable slideshowRunnable = new Runnable() {
        @Override
        public void run() {
            if (slideshowActive) {
                switchToNext();
                if (slideshowActive) {
                    slideshowHandler.postDelayed(this, SLIDESHOW_INTERVAL);
                }
            }
        }
    };

    private void showSlideshowIndicator(boolean show) {
        if (show) {
            if (tvSlideshowIndicator == null) {
                tvSlideshowIndicator = new TextView(this);
                tvSlideshowIndicator.setText("幻灯片播放中");
                tvSlideshowIndicator.setTextColor(Color.parseColor("#EDE5D2"));
                tvSlideshowIndicator.setTextSize(16);
                tvSlideshowIndicator.setBackgroundResource(R.drawable.bg_overlay_panel);
                tvSlideshowIndicator.setPadding(24, 12, 24, 12);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                params.topMargin = 24;
                tvSlideshowIndicator.setLayoutParams(params);
            }
            if (tvSlideshowIndicator.getParent() == null) {
                container.addView(tvSlideshowIndicator);
            }
            tvSlideshowIndicator.setVisibility(View.VISIBLE);
        } else {
            if (tvSlideshowIndicator != null) {
                tvSlideshowIndicator.setVisibility(View.GONE);
            }
        }
    }

    private void showInfoOverlay() {
        if (infoOverlay != null && infoOverlay.getVisibility() == View.VISIBLE) {
            hideInfoOverlay();
            return;
        }

        if (isVideoPlaying) {
            Toast.makeText(this, "视频播放中无法查看信息", Toast.LENGTH_SHORT).show();
            return;
        }

        MediaItem item = mediaList.get(currentIndex);
        int photoId;
        try {
            photoId = Integer.parseInt(item.getId());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "无法获取照片信息", Toast.LENGTH_SHORT).show();
            return;
        }

        if (infoOverlay == null) {
            infoOverlay = getLayoutInflater().inflate(R.layout.photo_info_overlay, null);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    (int) (420 * getResources().getDisplayMetrics().density),
                    FrameLayout.LayoutParams.MATCH_PARENT);
            params.gravity = Gravity.END;
            infoOverlay.setLayoutParams(params);
            tvInfoContent = infoOverlay.findViewById(R.id.tv_info_content);
        }

        if (infoOverlay.getParent() == null) {
            container.addView(infoOverlay);
        }

        tvInfoContent.setText("加载中...");
        infoOverlay.setVisibility(View.VISIBLE);
        infoOverlay.bringToFront();
        infoVisible = true;

        loadPhotoDetail(photoId);
    }

    private void loadPhotoDetail(int photoId) {
        if (api == null || token == null) {
            tvInfoContent.setText("API未初始化");
            return;
        }

        String params = "id=" + photoId;
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/gallery/getOne", "GET", params);

        api.getGalleryPhotoDetail(token, authx, photoId).enqueue(new Callback<FnHttpApi.GalleryOneResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.GalleryOneResponse> call,
                                   Response<FnHttpApi.GalleryOneResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.GalleryOneResponse result = response.body();
                    if (result.code == 0 && result.data != null) {
                        displayPhotoInfo(result.data);
                    } else {
                        tvInfoContent.setText("无法获取照片信息: " + nullToStr(result.msg));
                    }
                } else {
                    tvInfoContent.setText("请求失败: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.GalleryOneResponse> call, Throwable t) {
                tvInfoContent.setText("网络错误: " + t.getMessage());
            }
        });
    }

    private void displayPhotoInfo(FnHttpApi.PhotoDetailInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("📷 照片信息\n\n");
        sb.append("文件名: ").append(nullToStr(info.fileName)).append("\n");
        sb.append("类型: ").append(nullToStr(info.category)).append("\n");
        sb.append("尺寸: ").append(info.width).append(" × ").append(info.height).append("px").append("\n");

        if (info.fileSize > 0) {
            String sizeStr;
            if (info.fileSize > 1024 * 1024) {
                sizeStr = String.format(Locale.US, "%.1f MB", info.fileSize / (1024f * 1024f));
            } else if (info.fileSize > 1024) {
                sizeStr = String.format(Locale.US, "%.1f KB", info.fileSize / 1024f);
            } else {
                sizeStr = info.fileSize + " B";
            }
            sb.append("文件大小: ").append(sizeStr).append("\n");
        }

        if (info.photoDateTime != null && !info.photoDateTime.isEmpty()) {
            sb.append("拍摄时间: ").append(info.photoDateTime).append("\n");
        } else if (info.dateTime != null && !info.dateTime.isEmpty()) {
            sb.append("上传时间: ").append(info.dateTime).append("\n");
        }

        if (info.make != null && !info.make.isEmpty()) {
            sb.append("相机: ").append(info.make);
            if (info.model != null && !info.model.isEmpty()) {
                sb.append(" ").append(info.model);
            }
            sb.append("\n");
        }

        if (info.fNumber != null && !info.fNumber.isEmpty()) {
            sb.append("光圈: F/").append(info.fNumber).append("\n");
        }
        if (info.exposureTime != null && !info.exposureTime.isEmpty()) {
            sb.append("快门: ").append(info.exposureTime).append("\n");
        }
        if (info.isoSpeedRatings != null && !info.isoSpeedRatings.isEmpty()) {
            sb.append("ISO: ").append(info.isoSpeedRatings).append("\n");
        }
        if (info.focalLength != null && !info.focalLength.isEmpty()) {
            sb.append("焦距: ").append(info.focalLength).append("mm\n");
        }
        if (info.mp != null && !info.mp.isEmpty()) {
            sb.append("像素: ").append(info.mp).append("\n");
        }
        if (info.geo != null && !info.geo.isEmpty()) {
            sb.append("地理位置: ").append(info.geo).append("\n");
        }

        sb.append("\n 按 INFO 键关闭");
        tvInfoContent.setText(sb.toString());
    }

    private void hideInfoOverlay() {
        if (infoOverlay != null) {
            infoOverlay.setVisibility(View.GONE);
        }
        infoVisible = false;
    }

    private void toggleCollect() {
        MediaItem item = mediaList.get(currentIndex);
        if (!"photo".equals(item.getType()) && !"video".equals(item.getType())) {
            Toast.makeText(this, "只能收藏照片或视频", Toast.LENGTH_SHORT).show();
            return;
        }

        if (api == null || token == null || baseUrl == null) {
            Toast.makeText(this, "API未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        int photoId;
        try {
            photoId = Integer.parseInt(item.getId());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "无法获取媒体ID", Toast.LENGTH_SHORT).show();
            return;
        }

        String authx = FnAuthUtils.generateAuthX("/p/api/v1/photo/collect", "POST", "id=" + photoId + "&collect=1");
        String body = "id=" + photoId + "&collect=1";

        api.toggleCollect(token, authx, photoId, 1).enqueue(new Callback<FnHttpApi.BaseResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.BaseResponse> call,
                                   Response<FnHttpApi.BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    FnHttpApi.BaseResponse result = response.body();
                    if (result.errno == 0) {
                        Toast.makeText(MediaDetailActivity.this,
                                "已收藏 ❤️", Toast.LENGTH_SHORT).show();
                    } else {
                        // Try to un-collect
                        toggleUnCollect(photoId);
                    }
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.BaseResponse> call, Throwable t) {
                Log.e(TAG, "收藏失败", t);
                Toast.makeText(MediaDetailActivity.this, "收藏失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleUnCollect(int photoId) {
        String authx = FnAuthUtils.generateAuthX("/p/api/v1/photo/collect", "POST", "id=" + photoId + "&collect=0");

        api.toggleCollect(token, authx, photoId, 0).enqueue(new Callback<FnHttpApi.BaseResponse>() {
            @Override
            public void onResponse(Call<FnHttpApi.BaseResponse> call,
                                   Response<FnHttpApi.BaseResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MediaDetailActivity.this,
                            "已取消收藏 ♡", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<FnHttpApi.BaseResponse> call, Throwable t) {
                Log.e(TAG, "取消收藏失败", t);
            }
        });
    }

    private String nullToStr(String str) {
        return str != null && !str.isEmpty() && !"null".equals(str) ? str : "-";
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (infoVisible) {
            if (keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_BACK) {
                hideInfoOverlay();
                return true;
            }
            return true;
        }

        if (slideshowActive) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                stopSlideshow();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                stopSlideshow();
                finish();
                return true;
            }
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (isZoomed) {
                    panZoomedImage(MediaDetailViewport.panDeltaX(keyCode, PAN_STEP), 0.0f);
                    return true;
                }
                switchToPrevious();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (isZoomed) {
                    panZoomedImage(MediaDetailViewport.panDeltaX(keyCode, PAN_STEP), 0.0f);
                    return true;
                }
                switchToNext();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (isZoomed) {
                    panZoomedImage(0.0f, MediaDetailViewport.panDeltaY(keyCode, PAN_STEP));
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (isZoomed) {
                    panZoomedImage(0.0f, MediaDetailViewport.panDeltaY(keyCode, PAN_STEP));
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (isZoomed) {
                    exitZoomMode();
                    return true;
                }
                finish();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                handleOkKey();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (player != null) {
                    player.setPlayWhenReady(!player.getPlayWhenReady());
                }
                return true;
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_MENU:
                showInfoOverlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                startSlideshow();
                return true;
            case KeyEvent.KEYCODE_F1:
            case KeyEvent.KEYCODE_F2:
                toggleCollect();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void handleOkKey() {
        if (player != null && isVideoPlaying) {
            boolean isPlaying = player.getPlayWhenReady();
            player.setPlayWhenReady(!isPlaying);
            String message = isPlaying ? "已暂停" : "继续播放";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } else if (currentVideoItem != null && !isVideoPlaying) {
            startVideoPlayback(currentVideoItem);
        } else if (!isVideoPlaying) {
            enterZoomMode();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        slideshowHandler.removeCallbacksAndMessages(null);
        debounceHandler.removeCallbacksAndMessages(null);
        actionHintHandler.removeCallbacksAndMessages(null);
    }
}
