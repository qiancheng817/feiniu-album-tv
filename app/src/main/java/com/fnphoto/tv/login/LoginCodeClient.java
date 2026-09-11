package com.fnphoto.tv.login;

import android.util.Log;

import com.fnphoto.tv.api.FnAuthUtils;
import com.fnphoto.tv.api.TlsUtils;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginCodeClient {
    private static final String TAG = "FnLoginCode";
    private static final String CLIENT_VERSION = "12617";
    private static final String USER_AGENT = "1.2.6 (com.trim.tv; build:12617; Android 36) okHttp/4.9.0";
    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient client;

    public LoginCodeClient() {
        client = TlsUtils.enableTlsOnApi19(new OkHttpClient.Builder())
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build();
    }

    public String fetchServerName(String baseUrl) {
        String path = "/v/api/v1/sys/config";
        Request request = baseRequest(baseUrl, path, "GET", null).get().build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", -1) != 0) {
                return "";
            }
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                return "";
            }
            return firstNonBlank(
                    data.optString("name", ""),
                    data.optString("deviceName", ""),
                    data.optString("serverName", ""),
                    data.optString("hostname", "")
            );
        } catch (Exception ignored) {
            return "";
        }
    }

    public LoginCodeParser.GenerateResult generateLoginCode(String baseUrl) throws Exception {
        String path = "/v/api/v1/logincode/generate";
        RequestBody body = RequestBody.create(JSON, new byte[0]);
        Request request = baseRequest(baseUrl, path, "PUT", "").put(body).build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return LoginCodeParser.parseGenerateResponse(responseBody);
        }
    }

    public LoginCodeParser.StatusResult checkLoginCode(String baseUrl, String code) throws Exception {
        String path = statusPath(code);
        Request request = baseRequest(baseUrl, path, "GET", null).get().build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "poll GET " + path + " HTTP " + response.code()
                    + " body=" + sanitizeResponseBody(responseBody));
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return LoginCodeParser.parseStatusResponse(responseBody);
        }
    }

    static String statusPath(String code) {
        return "/v/api/v1/logincode/" + (code == null ? "" : code.trim());
    }

    private Request.Builder baseRequest(String baseUrl, String pathAndQuery, String method, String signData) {
        String path = pathAndQuery;
        int queryStart = path.indexOf('?');
        if (queryStart >= 0) {
            path = path.substring(0, queryStart);
        }
        String authx = FnAuthUtils.generateAuthX(path, method, signData);
        return new Request.Builder()
                .url(baseUrl.replaceAll("/+$", "") + pathAndQuery)
                .header("Content-Type", "application/json")
                .header("Authorization", "")
                .header("cookie", "mode=relay")
                .header("X-Trim-Client-Version", CLIENT_VERSION)
                .header("authx", authx != null ? authx : "")
                .header("User-Agent", USER_AGENT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return "";
    }

    private static String sanitizeResponseBody(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        return body.replaceAll(
                "(\"(?:token|secret|backId|back_id|accessToken|access_token)\"\\s*:\\s*\")([^\"]+)(\")",
                "$1***$3"
        );
    }
}
