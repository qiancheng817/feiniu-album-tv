package com.fnphoto.tv.api;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.fnphoto.tv.LoginActivity;
import com.fnphoto.tv.settings.SessionPreferences;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.json.JSONObject;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public class ApiInterceptor implements Interceptor {
    private static final String TAG = "ApiInterceptor";
    private static final int AUTH_FAILURE_CODE = 401;
    private static final long AUTH_FAILURE_PEEK_BYTES = 4096L;
    private final SessionRefresher sessionRefresher;
    private final AuthFailureRedirector authFailureRedirector;
    private final RequestSigner requestSigner;

    interface SessionRefresher {
        boolean reLoginSync();

        String currentToken();
    }

    interface AuthFailureRedirector {
        void redirectToLogin();
    }

    interface RequestSigner {
        String sign(String path, String method, String data);
    }

    public ApiInterceptor(Context context) {
        Context appContext = context.getApplicationContext();
        this.sessionRefresher = new SharedPreferencesSessionRefresher(appContext);
        this.authFailureRedirector = new LoginAuthFailureRedirector(appContext);
        this.requestSigner = FnAuthUtils::generateAuthX;
    }

    ApiInterceptor(SessionRefresher sessionRefresher, AuthFailureRedirector authFailureRedirector) {
        this(sessionRefresher, authFailureRedirector, FnAuthUtils::generateAuthX);
    }

    ApiInterceptor(
            SessionRefresher sessionRefresher,
            AuthFailureRedirector authFailureRedirector,
            RequestSigner requestSigner
    ) {
        this.sessionRefresher = sessionRefresher;
        this.authFailureRedirector = authFailureRedirector;
        this.requestSigner = requestSigner;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);

        if (isAuthenticationFailure(response)) {
            Log.w(TAG, "Received authentication failure for endpoint: " + sanitizedEndpointForLog(request.url()));

            RetryMaterial retryMaterial = prepareRetryMaterial(request);
            if (retryMaterial == null) {
                Log.e(TAG, "Authentication failure request body cannot be retried safely");
                authFailureRedirector.redirectToLogin();
                return response;
            }

            boolean reloginSuccess = sessionRefresher.reLoginSync();
            if (reloginSuccess) {
                String newToken = sessionRefresher.currentToken();

                if (!newToken.isEmpty()) {
                    String path = request.url().encodedPath();
                    String method = request.method();
                    String newAuthx = requestSigner.sign(path, method, retryMaterial.signingData);

                    Request newRequest = request.newBuilder()
                        .method(method, retryMaterial.requestBody)
                        .header("accesstoken", newToken)
                        .header("authx", newAuthx != null ? newAuthx : "")
                        .build();

                    Log.i(TAG, "Retrying request with new token: " + path);
                    response.close();
                    Response retryResponse = chain.proceed(newRequest);
                    if (isAuthenticationFailure(retryResponse)) {
                        Log.e(TAG, "Retried request still returned authentication failure");
                        authFailureRedirector.redirectToLogin();
                    }
                    return retryResponse;
                }
            } else {
                Log.e(TAG, "Re-login failed, cannot recover from 401");
            }

            authFailureRedirector.redirectToLogin();
        }

        return response;
    }

    static String sanitizedEndpointForLog(HttpUrl url) {
        return url.encodedPath();
    }

    private static RetryMaterial prepareRetryMaterial(Request request) {
        RequestBody body = request.body();
        if (body != null && (body.isOneShot() || body.isDuplex())) {
            return null;
        }

        String method = request.method();
        if (isBodySignedMethod(method) && body != null) {
            try {
                Buffer buffer = new Buffer();
                body.writeTo(buffer);
                byte[] bytes = buffer.readByteArray();
                String signingData = new String(bytes, StandardCharsets.UTF_8);
                RequestBody retryBody = RequestBody.create(body.contentType(), bytes);
                return new RetryMaterial(signingData, retryBody);
            } catch (IOException ignored) {
                return null;
            }
        }
        return new RetryMaterial(request.url().encodedQuery(), body);
    }

    private static boolean isBodySignedMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private static boolean isAuthenticationFailure(Response response) {
        if (response.code() == AUTH_FAILURE_CODE) {
            return true;
        }
        return responseBodyCode(response) == AUTH_FAILURE_CODE;
    }

    private static int responseBodyCode(Response response) {
        ResponseBody responseBody = response.body();
        if (responseBody == null || !isJsonResponse(responseBody)) {
            return Integer.MIN_VALUE;
        }
        try {
            String body = response.peekBody(AUTH_FAILURE_PEEK_BYTES).string();
            if (body.isEmpty()) {
                return Integer.MIN_VALUE;
            }
            return new JSONObject(body).optInt("code", Integer.MIN_VALUE);
        } catch (Exception ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static boolean isJsonResponse(ResponseBody responseBody) {
        MediaType contentType = responseBody.contentType();
        if (contentType == null) {
            return false;
        }
        return contentType.subtype().toLowerCase(Locale.US).contains("json");
    }

    private static final class RetryMaterial {
        private final String signingData;
        private final RequestBody requestBody;

        private RetryMaterial(String signingData, RequestBody requestBody) {
            this.signingData = signingData;
            this.requestBody = requestBody;
        }
    }

    private static final class SharedPreferencesSessionRefresher implements SessionRefresher {
        private final Context context;

        private SharedPreferencesSessionRefresher(Context context) {
            this.context = context;
        }

        @Override
        public boolean reLoginSync() {
            return Reauthenticator.reLoginSync(context);
        }

        @Override
        public String currentToken() {
            SharedPreferences prefs = context.getSharedPreferences(SessionPreferences.PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString("api_token", "");
        }
    }

    private static final class LoginAuthFailureRedirector implements AuthFailureRedirector {
        private final Context context;

        private LoginAuthFailureRedirector(Context context) {
            this.context = context;
        }

        @Override
        public void redirectToLogin() {
            SessionPreferences.removeExpiredSessionKeys(
                    context.getSharedPreferences(SessionPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit()
            ).apply();

            Intent intent = new Intent(context, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        }
    }
}
