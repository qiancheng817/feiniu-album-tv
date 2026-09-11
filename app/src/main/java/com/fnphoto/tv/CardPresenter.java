package com.fnphoto.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.fnphoto.tv.cache.CachedImageLoader;
import com.fnphoto.tv.ui.TvUiText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CardPresenter extends Presenter {
    private static final String TAG = "CardPresenter";
    private static final int CARD_WIDTH = 352;
    private static final int CARD_HEIGHT = 208;
    private static final int DATE_CARD_WIDTH = 316;
    private static final int DATE_CARD_HEIGHT = 214;
    private static final int PERSON_CARD_WIDTH = 252;
    private static final int PERSON_CARD_HEIGHT = 252;
    private static final int PADDING = 6;
    private static final int MAX_PREVIEW = 8;
    private static final int[] FOLDER_COLORS = {
        0xFF24231F, 0xFF2F3431, 0xFF3A3327, 0xFF263734,
        0xFF3A2C29, 0xFF30322C, 0xFF322F3A, 0xFF293638
    };
    private static Drawable[] folderCardPlaceholders;
    private static int lastFolderColorIndex = -1;

    private String baseUrl;

    public CardPresenter(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();
        FrameLayout cardView = new FrameLayout(context);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setBackgroundResource(R.drawable.bg_card_focus);
        cardView.setElevation(2f);
        cardView.setPadding(5, 5, 5, 5);

        ImageView imageView = new ImageView(context);
        imageView.setId(R.id.card_image);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cardView.addView(imageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View gradient = new View(context);
        gradient.setId(R.id.card_gradient);
        gradient.setBackgroundResource(R.drawable.bg_card_overlay_gradient);
        FrameLayout.LayoutParams gradientParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                96,
                Gravity.BOTTOM);
        cardView.addView(gradient, gradientParams);

        TextView titleView = new TextView(context);
        titleView.setId(R.id.card_title);
        titleView.setTextColor(Color.parseColor("#FFFFFF"));
        titleView.setTextSize(18);
        titleView.setSingleLine(true);
        titleView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        titleView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        titleParams.setMargins(18, 0, 18, 26);
        cardView.addView(titleView, titleParams);

        TextView metaView = new TextView(context);
        metaView.setId(R.id.card_meta);
        metaView.setTextColor(Color.parseColor("#D8D2C6"));
        metaView.setTextSize(13);
        metaView.setSingleLine(true);
        metaView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams metaParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        metaParams.setMargins(18, 0, 18, 10);
        cardView.addView(metaView, metaParams);

        TextView badgeView = new TextView(context);
        badgeView.setId(R.id.card_badge);
        badgeView.setTextColor(Color.parseColor("#FFFFFF"));
        badgeView.setTextSize(13);
        badgeView.setGravity(Gravity.CENTER);
        badgeView.setBackgroundResource(R.drawable.bg_overlay_panel);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                56,
                34,
                Gravity.TOP | Gravity.RIGHT);
        badgeParams.setMargins(0, 12, 12, 0);
        cardView.addView(badgeView, badgeParams);

        cardView.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.10f : 1.0f;
            v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .translationZ(hasFocus ? 18f : 2f)
                    .setDuration(140)
                    .start();
        });
        
        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        MediaItem mediaItem = (MediaItem) item;
        FrameLayout cardView = (FrameLayout) viewHolder.view;
        ImageView imageView = cardView.findViewById(R.id.card_image);
        View gradient = cardView.findViewById(R.id.card_gradient);
        TextView titleView = cardView.findViewById(R.id.card_title);
        TextView metaView = cardView.findViewById(R.id.card_meta);
        TextView badgeView = cardView.findViewById(R.id.card_badge);

        cardView.setContentDescription(buildContentDescription(mediaItem));
        configureCardSize(cardView, mediaItem);
        imageView.setImageDrawable(null);
        badgeView.setVisibility(View.GONE);
        
        // 存储当前item的ID和类型，用于检查视图是否已被重用
        cardView.setTag(R.id.media_item_id, mediaItem.getId());
        cardView.setTag(R.id.media_item_type, mediaItem.getType());
        bindOverlay(mediaItem, gradient, titleView, metaView);

        if ("date".equals(mediaItem.getType()) || "person_date".equals(mediaItem.getType())) {
            List<String> previewUrls = mediaItem.getPreviewThumbUrls();
            if (previewUrls != null && !previewUrls.isEmpty()) {
                loadPreviewImages(cardView, imageView, previewUrls, mediaItem.getId());
            } else {
                imageView.setImageDrawable(createPlaceholderDrawable(cardView.getContext()));
            }
        } else if ("video".equals(mediaItem.getType())) {
            badgeView.setText("▶");
            badgeView.setVisibility(View.VISIBLE);
            loadVideoThumbnail(cardView, imageView, mediaItem);
        } else if ("folder".equals(mediaItem.getType())) {
            imageView.setImageDrawable(getFolderCardDrawable(cardView.getContext()));
        } else if ("album".equals(mediaItem.getType()) || "place".equals(mediaItem.getType())) {
            loadSingleImage(imageView, mediaItem);
        } else if ("person".equals(mediaItem.getType())) {
            loadSingleImage(imageView, mediaItem);
        } else {
            loadSingleImage(imageView, mediaItem);
        }
    }

    private void configureCardSize(FrameLayout cardView, MediaItem mediaItem) {
        int width = CARD_WIDTH;
        int height = CARD_HEIGHT;
        if ("person".equals(mediaItem.getType())) {
            width = PERSON_CARD_WIDTH;
            height = PERSON_CARD_HEIGHT;
        } else if ("date".equals(mediaItem.getType()) || "person_date".equals(mediaItem.getType())) {
            width = DATE_CARD_WIDTH;
            height = DATE_CARD_HEIGHT;
        }

        ViewGroup.LayoutParams params = cardView.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(width, height);
        } else {
            params.width = width;
            params.height = height;
        }
        cardView.setLayoutParams(params);
    }

    private void bindOverlay(MediaItem mediaItem, View gradient, TextView titleView, TextView metaView) {
        String title = TvUiText.cardOverlayTitle(mediaItem.getType(), mediaItem.getTitle());
        String meta = overlayMeta(mediaItem);
        boolean showOverlay = !title.isEmpty() || !meta.isEmpty();

        gradient.setVisibility(showOverlay ? View.VISIBLE : View.GONE);
        titleView.setVisibility(!title.isEmpty() ? View.VISIBLE : View.GONE);
        titleView.setText(title);
        metaView.setVisibility(!meta.isEmpty() ? View.VISIBLE : View.GONE);
        metaView.setText(meta);
    }

    private String overlayMeta(MediaItem mediaItem) {
        if ("person".equals(mediaItem.getType()) && mediaItem.getPhotoCount() > 0) {
            return mediaItem.getPhotoCount() + "张照片";
        }
        if (!"photo".equals(mediaItem.getType()) && !"video".equals(mediaItem.getType())) {
            return mediaItem.getDateStr() != null ? mediaItem.getDateStr() : "";
        }
        return "";
    }

    private String buildContentDescription(MediaItem mediaItem) {
        StringBuilder builder = new StringBuilder();
        if (mediaItem.getTitle() != null && !mediaItem.getTitle().isEmpty()) {
            builder.append(mediaItem.getTitle());
        }
        if (mediaItem.getDateStr() != null && !mediaItem.getDateStr().isEmpty()) {
            if (builder.length() > 0) {
                builder.append("，");
            }
            builder.append(mediaItem.getDateStr());
        }
        if (mediaItem.getPhotoCount() > 0) {
            if (builder.length() > 0) {
                builder.append("，");
            }
            builder.append(mediaItem.getPhotoCount()).append("张照片");
        }
        return builder.toString();
    }

    private void loadVideoThumbnail(FrameLayout cardView, ImageView imageView, MediaItem mediaItem) {
        Context context = cardView.getContext();
        SharedPreferences prefs = context.getSharedPreferences("fn_photo_prefs", Context.MODE_PRIVATE);
        String token = prefs.getString("api_token", "");
        
        // 使用缩略图URL（mUrl）
        String thumbUrl = mediaItem.getThumbnailUrl();
        
        if (thumbUrl == null || thumbUrl.isEmpty()) {
            // 如果没有缩略图，使用默认图标
            Drawable drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_media_play);
            imageView.setImageDrawable(drawable);
            return;
        }
        
        if (baseUrl != null && !thumbUrl.startsWith("http")) {
            thumbUrl = baseUrl + thumbUrl;
        }
        
        final String finalUrl = thumbUrl;
        final String itemId = mediaItem.getId();
        
        // 加载缩略图并添加播放图标
        CachedImageLoader.loadImage(context, finalUrl, token, CARD_WIDTH, CARD_HEIGHT,
                new CachedImageLoader.ImageLoadCallback() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap) {
                        // 检查视图是否还是原来的
                        String currentId = (String) cardView.getTag(R.id.media_item_id);
                        String currentType = (String) cardView.getTag(R.id.media_item_type);
                        
                        if (!itemId.equals(currentId) || !"video".equals(currentType)) {
                            Log.w(TAG, "View reused, skipping video thumbnail");
                            return;
                        }
                        
                        // 创建带播放图标的复合图片
                        Bitmap composite = createVideoThumbnailWithPlayIcon(bitmap, CARD_WIDTH, CARD_HEIGHT);
                        imageView.setImageBitmap(composite);
                        Log.d(TAG, "Video thumbnail with play icon set for " + itemId);
                    }
                    
                    @Override
                    public void onLoadFailed() {
                        // 加载失败，使用默认播放图标
                        Drawable drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_media_play);
                        imageView.setImageDrawable(drawable);
                    }
                });
    }
    
    /**
     * 创建带播放图标的视频缩略图
     */
    private Bitmap createVideoThumbnailWithPlayIcon(Bitmap thumbnail, int width, int height) {
        // 创建新的Bitmap
        Bitmap composite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(composite);
        
        // 绘制缩略图（居中裁剪填充）
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        
        int thumbWidth = thumbnail.getWidth();
        int thumbHeight = thumbnail.getHeight();
        
        // 计算缩放和裁剪区域
        float scale = Math.max((float) width / thumbWidth, (float) height / thumbHeight);
        float scaledWidth = thumbWidth * scale;
        float scaledHeight = thumbHeight * scale;
        
        float srcLeft = Math.max(0, (thumbWidth - width / scale) / 2);
        float srcTop = Math.max(0, (thumbHeight - height / scale) / 2);
        float srcRight = Math.min(thumbWidth, srcLeft + width / scale);
        float srcBottom = Math.min(thumbHeight, srcTop + height / scale);
        
        Rect srcRect = new Rect((int) srcLeft, (int) srcTop, (int) srcRight, (int) srcBottom);
        Rect dstRect = new Rect(0, 0, width, height);
        
        canvas.drawBitmap(thumbnail, srcRect, dstRect, paint);
        
        // 绘制半透明黑色遮罩（让播放按钮更明显）
        paint.setColor(Color.parseColor("#5510100E"));
        canvas.drawRect(0, 0, width, height, paint);
        
        // 绘制播放按钮（三角形）
        paint.setColor(Color.parseColor("#D7B56D"));
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        
        int playButtonSize = Math.min(width, height) / 8;
        int centerX = width / 2;
        int centerY = height / 2;
        
        // 绘制圆形背景
        paint.setColor(Color.parseColor("#CC1B1A16"));
        canvas.drawCircle(centerX, centerY, playButtonSize, paint);
        
        // 绘制播放三角形
        paint.setColor(Color.parseColor("#EDE5D2"));
        int triangleSize = playButtonSize / 2;
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(centerX - triangleSize / 2, centerY - triangleSize);
        path.lineTo(centerX - triangleSize / 2, centerY + triangleSize);
        path.lineTo(centerX + triangleSize, centerY);
        path.close();
        canvas.drawPath(path, paint);
        
        return composite;
    }

    private void loadPreviewImages(FrameLayout cardView, ImageView imageView, List<String> urls, String itemId) {
        Context context = cardView.getContext();
        SharedPreferences prefs = context.getSharedPreferences("fn_photo_prefs", Context.MODE_PRIVATE);
        String token = prefs.getString("api_token", "");
        
        int count = Math.min(urls.size(), MAX_PREVIEW);
        Bitmap[] bitmaps = new Bitmap[count];
        AtomicInteger loadedCount = new AtomicInteger(0);
        
        Log.d(TAG, "Loading " + count + " preview images with cache support");
        
        for (int i = 0; i < count; i++) {
            String url = urls.get(i);
            if (baseUrl != null && !url.startsWith("http")) {
                url = baseUrl + url;
            }
            
            final int index = i;
            
            // 使用带缓存的加载器
            CachedImageLoader.loadImage(context, url, token, 150, 150, 
                    new CachedImageLoader.ImageLoadCallback() {
                        @Override
                        public void onBitmapLoaded(Bitmap bitmap) {
                            bitmaps[index] = bitmap;
                            int current = loadedCount.incrementAndGet();
                            Log.d(TAG, "Preview loaded " + current + "/" + count + " for " + itemId);
                            
                            if (current >= count) {
                                // 所有图片加载完成，检查视图是否可用
                                String currentId = (String) cardView.getTag(R.id.media_item_id);
                                if (itemId.equals(currentId)) {
                                    Bitmap composite = createCompositeBitmap(bitmaps, DATE_CARD_WIDTH, DATE_CARD_HEIGHT);
                                    imageView.setImageBitmap(composite);
                                    Log.d(TAG, "Composite image set for " + itemId);
                                }
                            }
                        }
                        
                        @Override
                        public void onLoadFailed() {
                            int current = loadedCount.incrementAndGet();
                            Log.w(TAG, "Preview load failed " + current + "/" + count + " for " + itemId);
                            
                            if (current >= count) {
                                String currentId = (String) cardView.getTag(R.id.media_item_id);
                                if (itemId.equals(currentId)) {
                                    Bitmap composite = createCompositeBitmap(bitmaps, DATE_CARD_WIDTH, DATE_CARD_HEIGHT);
                                    imageView.setImageBitmap(composite);
                                }
                            }
                        }
                    });
        }
    }

    private Bitmap createCompositeBitmap(Bitmap[] bitmaps, int width, int height) {
        Bitmap composite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(composite);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        
        canvas.drawColor(Color.parseColor("#24231F"));
        
        // 计算有效bitmap数量
        List<Bitmap> validBitmaps = new ArrayList<>();
        for (Bitmap b : bitmaps) {
            if (b != null && !b.isRecycled()) {
                validBitmaps.add(b);
            }
        }
        
        if (validBitmaps.isEmpty()) {
            return composite;
        }

        int count = validBitmaps.size();

        if (count == 1) {
            drawBitmap(canvas, validBitmaps.get(0), 0, 0, width, height, paint);
        } else if (count == 2) {
            int halfWidth = (width - PADDING) / 2;
            // 左半部分
            drawBitmap(canvas, validBitmaps.get(0), 0, 0, halfWidth, height, paint);
            // 右半部分：从 halfWidth+PADDING 开始，到 width 结束
            drawBitmap(canvas, validBitmaps.get(1), halfWidth + PADDING, 0, width, height, paint);
        } else if (count == 3) {
            int halfWidth = (width - PADDING) / 2;
            int halfHeight = (height - PADDING) / 2;
            // 左边大图
            drawBitmap(canvas, validBitmaps.get(0), 0, 0, halfWidth, height, paint);
            // 右上：从 y=0 到 y=halfHeight
            drawBitmap(canvas, validBitmaps.get(1), halfWidth + PADDING, 0, width, halfHeight, paint);
            // 右下：从 y=halfHeight+PADDING 到 y=height
            drawBitmap(canvas, validBitmaps.get(2), halfWidth + PADDING, halfHeight + PADDING, width, height, paint);
        } else {
            int halfWidth = (width - PADDING) / 2;
            int halfHeight = (height - PADDING) / 2;
            // 左上
            drawBitmap(canvas, validBitmaps.get(0), 0, 0, halfWidth, halfHeight, paint);
            // 右上
            drawBitmap(canvas, validBitmaps.get(1), halfWidth + PADDING, 0, width, halfHeight, paint);
            // 左下：从 y=halfHeight+PADDING 到 y=height
            drawBitmap(canvas, validBitmaps.get(2), 0, halfHeight + PADDING, halfWidth, height, paint);
            // 右下
            drawBitmap(canvas, validBitmaps.get(3), halfWidth + PADDING, halfHeight + PADDING, width, height, paint);
        }
        
        return composite;
    }

    private void drawBitmap(Canvas canvas, Bitmap bitmap, float left, float top, float right, float bottom, Paint paint) {
        int bitmapWidth = bitmap.getWidth();
        int bitmapHeight = bitmap.getHeight();
        float targetWidth = right - left;
        float targetHeight = bottom - top;
        
        float scale = Math.max(targetWidth / bitmapWidth, targetHeight / bitmapHeight);
        
        float srcLeft = Math.max(0, (bitmapWidth - targetWidth / scale) / 2);
        float srcTop = Math.max(0, (bitmapHeight - targetHeight / scale) / 2);
        float srcRight = Math.min(bitmapWidth, srcLeft + targetWidth / scale);
        float srcBottom = Math.min(bitmapHeight, srcTop + targetHeight / scale);
        
        Rect srcRect = new Rect((int) srcLeft, (int) srcTop, (int) srcRight, (int) srcBottom);
        Rect dstRect = new Rect((int) left, (int) top, (int) right, (int) bottom);
        
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);
    }

    private Drawable createPlaceholderDrawable(Context context) {
        Bitmap bitmap = Bitmap.createBitmap(DATE_CARD_WIDTH, DATE_CARD_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.parseColor("#24231F"));
        
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    private Drawable getFolderCardDrawable(Context context) {
        if (folderCardPlaceholders == null) {
            folderCardPlaceholders = new Drawable[FOLDER_COLORS.length];
            int bw = CARD_WIDTH * 3;
            int bh = CARD_HEIGHT * 3;
            for (int i = 0; i < FOLDER_COLORS.length; i++) {
                Bitmap bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(FOLDER_COLORS[i]);

                Drawable folderDrawable = ContextCompat.getDrawable(context, R.drawable.folder_icon);
                if (folderDrawable != null) {
                    int iconSize = CARD_HEIGHT / 2;
                    int cx = bw / 2;
                    int cy = bh / 2;
                    folderDrawable.setBounds(cx - iconSize, cy - iconSize, cx + iconSize, cy + iconSize);
                    folderDrawable.draw(canvas);
                }

                folderCardPlaceholders[i] = new BitmapDrawable(context.getResources(), bitmap);
            }
        }
        lastFolderColorIndex = (lastFolderColorIndex + 1) % FOLDER_COLORS.length;
        return folderCardPlaceholders[lastFolderColorIndex];
    }

    private void loadSingleImage(ImageView imageView, MediaItem mediaItem) {
        Context context = imageView.getContext();
        SharedPreferences prefs = context.getSharedPreferences("fn_photo_prefs", Context.MODE_PRIVATE);
        String token = prefs.getString("api_token", "");
        
        String imageUrl = mediaItem.getThumbnailUrl() != null ?
                mediaItem.getThumbnailUrl() : mediaItem.getMediaUrl();

        if (imageUrl != null && !imageUrl.isEmpty()) {
            // 使用带缓存的加载
            CachedImageLoader.loadIntoImageView(imageView, imageUrl, token);
        } else {
            Drawable drawable = ContextCompat.getDrawable(imageView.getContext(),
                    android.R.drawable.ic_menu_gallery);
            imageView.setImageDrawable(drawable);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        FrameLayout cardView = (FrameLayout) viewHolder.view;
        ImageView imageView = cardView.findViewById(R.id.card_image);
        TextView titleView = cardView.findViewById(R.id.card_title);
        TextView metaView = cardView.findViewById(R.id.card_meta);
        TextView badgeView = cardView.findViewById(R.id.card_badge);
        if (imageView != null) {
            imageView.setImageDrawable(null);
        }
        if (titleView != null) {
            titleView.setText("");
        }
        if (metaView != null) {
            metaView.setText("");
        }
        if (badgeView != null) {
            badgeView.setVisibility(View.GONE);
        }
        cardView.setTag(R.id.media_item_id, null);
        cardView.setTag(R.id.media_item_type, null);
    }
}
