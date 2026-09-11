package com.fnphoto.tv;

import android.app.Application;
import android.content.Context;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.fnphoto.tv.api.HttpClientProvider;
import java.io.InputStream;

public class FnPhotoApplication extends Application {
    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = this;
        Glide.get(this).getRegistry().replace(GlideUrl.class, InputStream.class,
            new OkHttpUrlLoader.Factory(HttpClientProvider.getClient(this)));
    }

    public static Context getAppContext() {
        return appContext;
    }
}
