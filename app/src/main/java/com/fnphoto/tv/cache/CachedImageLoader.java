package com.fnphoto.tv.cache;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;

public class CachedImageLoader {
    private static final String TAG = "CachedImageLoader";
    private static final int DEFAULT_THUMBNAIL_SIZE = 640;

    private static RequestOptions thumbnailOptions(int width, int height) {
        return new RequestOptions()
                .override(width, height)
                .centerCrop()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate();
    }

    private static String getFreshToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("fn_photo_prefs", Context.MODE_PRIVATE);
        return prefs.getString("api_token", "");
    }
    
    public interface ImageLoadCallback {
        void onBitmapLoaded(Bitmap bitmap);
        void onLoadFailed();
    }
    
    /**
     * 加载图片（带缓存）
     * @param context 上下文
     * @param url 图片URL
     * @param token 认证token
     * @param width 目标宽度
     * @param height 目标高度
     * @param callback 回调
     */
    public static void loadImage(Context context, String url, String token, 
                                  int width, int height, ImageLoadCallback callback) {
        ImageCacheManager cacheManager = ImageCacheManager.getInstance(context);
        
        // 1. 先检查内存/磁盘缓存
        Bitmap cachedBitmap = cacheManager.getCachedBitmap(url);
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            Log.d(TAG, "Using cached image for: " + url);
            callback.onBitmapLoaded(cachedBitmap);
            return;
        }
        
        // 2. 检查缓存文件（用于Glide直接从文件加载）
        File cacheFile = cacheManager.getCacheFile(url);
        if (cacheFile != null) {
            // 从缓存文件加载
            Log.d(TAG, "Loading from cache file: " + url);
            Glide.with(context)
                    .asBitmap()
                    .load(cacheFile)
                    .override(width, height)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            callback.onBitmapLoaded(resource);
                        }
                        
                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                        
                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            // 缓存文件损坏，从网络加载
                            loadFromNetwork(context, url, token, width, height, callback);
                        }
                    });
            return;
        }
        
        // 3. 从网络加载
        loadFromNetwork(context, url, token, width, height, callback);
    }
    
    /**
     * 从网络加载并缓存
     */
    private static void loadFromNetwork(Context context, String url, String token,
                                        int width, int height, ImageLoadCallback callback) {
        Log.d(TAG, "Loading from network: " + url);
        
        String freshToken = getFreshToken(context);
        ImageCacheManager cacheManager = ImageCacheManager.getInstance(context);
        
        GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                .addHeader("accesstoken", freshToken)
                .build());
        
        Glide.with(context)
                .asBitmap()
                .load(glideUrl)
                .override(width, height)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        // 保存到缓存
                        cacheManager.saveBitmapToCache(url, resource);
                        callback.onBitmapLoaded(resource);
                    }
                    
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                    
                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        Log.e(TAG, "Failed to load image: " + url);
                        callback.onLoadFailed();
                    }
                });
    }
    
    /**
     * 加载单张图片到ImageView（使用Glide的标准方式，但会先检查缓存）
     */
    public static void loadIntoImageView(android.widget.ImageView imageView, String url, String token) {
        loadIntoImageView(imageView, url, token, DEFAULT_THUMBNAIL_SIZE, DEFAULT_THUMBNAIL_SIZE);
    }

    /**
     * 加载单张缩略图到 ImageView，并限制解码尺寸，避免长时间滚动时占用过多 bitmap 内存。
     */
    public static void loadIntoImageView(android.widget.ImageView imageView, String url, String token,
                                         int width, int height) {
        Context context = imageView.getContext();
        String storedToken = token == null || token.isEmpty() ? getFreshToken(context) : "";
        String freshToken = ImageViewLoadPolicy.resolveToken(token, storedToken);
        RequestOptions options = thumbnailOptions(width, height);

        File cacheFile = null;
        if (ImageViewLoadPolicy.shouldProbeLegacyCacheBeforeGlide()) {
            cacheFile = ImageCacheManager.getInstance(context).getCacheFile(url);
        }
        if (cacheFile != null) {
            // 从缓存加载
            Glide.with(context)
                    .load(cacheFile)
                    .apply(options)
                    .into(imageView);
        } else {
            // 从网络加载
            GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                    .addHeader("accesstoken", freshToken)
                    .build());
            
            Glide.with(context)
                    .load(glideUrl)
                    .apply(options)
                    .into(imageView);
        }
    }

    public static void clearImageView(android.widget.ImageView imageView) {
        try {
            Glide.with(imageView.getContext()).clear(imageView);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to clear image view", e);
        }
    }
    
    /**
     * 预加载图片到缓存
     */
    public static void preloadImage(Context context, String url, String token) {
        String freshToken = getFreshToken(context);
        ImageCacheManager cacheManager = ImageCacheManager.getInstance(context);
        
        // 如果缓存不存在，从网络加载并缓存
        if (!cacheManager.isCacheValid(url)) {
            GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                    .addHeader("accesstoken", freshToken)
                    .build());
            
            Glide.with(context)
                    .asBitmap()
                    .load(glideUrl)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            cacheManager.saveBitmapToCache(url, resource);
                        }
                        
                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        }
    }
    
    /**
     * 清空所有图片缓存
     */
    public static void clearAllCache(Context context) {
        ImageCacheManager.getInstance(context).clearCache();
    }
    
    /**
     * 获取缓存大小（MB）
     */
    public static float getCacheSizeMB(Context context) {
        long bytes = ImageCacheManager.getInstance(context).getCacheSize();
        return bytes / (1024f * 1024f);
    }
}
