package com.fnphoto.tv.api;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class HttpClientProvider {
    private static OkHttpClient client;

    public static OkHttpClient getClient(Context context) {
        if (client == null) {
            client = TlsUtils.enableTlsOnApi19(new OkHttpClient.Builder())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new ApiInterceptor(context))
                .build();
        }
        return client;
    }
}
