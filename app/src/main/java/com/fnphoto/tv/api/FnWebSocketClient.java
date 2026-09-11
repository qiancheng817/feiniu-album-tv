package com.fnphoto.tv.api;

import android.util.Base64;
import okhttp3.*;
import org.json.JSONObject;

public class FnWebSocketClient {
    public static final String DEVICE_TYPE_ANDROID_TV = "AndroidTV";
    public static final String DEVICE_NAME_ANDROID_TV = "Android-TV-Box";

    private OkHttpClient client;
    private WebSocket mainWs;
    private String serverUrl;
    private String currentSi;
    private String currentPubKey;
    private String currentAccessToken;
    private String currentDeviceId;
    private String currentDeviceType = DEVICE_TYPE_ANDROID_TV;
    private String currentDeviceName = DEVICE_NAME_ANDROID_TV;
    private int currentStay = 0;
    
    public FnWebSocketClient() {
        // 延迟初始化 OkHttpClient，捕获可能的异常
        try {
            this.client = TlsUtils.enableTlsOnApi19(new OkHttpClient.Builder()).build();
            android.util.Log.d("FnWebSocket", "OkHttpClient initialized successfully");
        } catch (Exception e) {
            android.util.Log.e("FnWebSocket", "Failed to initialize OkHttpClient: " + e.getMessage(), e);
            throw e;
        }
    }
    
    public interface LoginCallback {
        void onSuccess(JSONObject response);
        default void onTwoFactorRequired(TwoFactorChallenge challenge) {
            onError("需要动态验证码");
        }
        void onError(String msg);
    }

    public void startLogin(String url, String username, String password, LoginCallback callback) {
        startLogin(url, username, password, FnProtocolUtils.generateRandomString(32), callback);
    }

    public void startLogin(String url, String username, String password, String deviceId, LoginCallback callback) {
        android.util.Log.d("FnWebSocket", "startLogin called with url: " + url);
        currentAccessToken = null;
        currentDeviceId = hasValue(deviceId) ? deviceId : FnProtocolUtils.generateRandomString(32);
        currentDeviceType = DEVICE_TYPE_ANDROID_TV;
        currentDeviceName = DEVICE_NAME_ANDROID_TV;
        currentStay = 0;
        
        // Ensure URL format is correct for WebSocket
        if (url.startsWith("http://")) {
            this.serverUrl = url.replace("http://", "ws://");
        } else if (url.startsWith("https://")) {
            this.serverUrl = url.replace("https://", "wss://");
        } else {
            this.serverUrl = "ws://" + url;
        }
        this.serverUrl += "/websocket?type=main";
        
        android.util.Log.d("FnWebSocket", "Connecting to: " + serverUrl);
        
        try {
            Request request = new Request.Builder().url(serverUrl).build();
            android.util.Log.d("FnWebSocket", "Request created successfully");
            
            mainWs = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                android.util.Log.d("FnWebSocket", "Connection opened, requesting RSA key");
                // Start keepalive ping every 10 seconds to prevent timeout
                startKeepalive(webSocket);
                requestRsaKey(webSocket);
            }
            
            private void startKeepalive(final WebSocket ws) {
                final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                final Runnable pingRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (ws != null) {
                            ws.send("{\"req\":\"ping\"}");
//                            android.util.Log.d("FnWebSocket", "send ping");
                            handler.postDelayed(this, 10000);
                        }
                    }
                };
                handler.postDelayed(pingRunnable, 10000);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                // android.util.Log.d("FnWebSocket", "Received: " + text);
                try {
                    JSONObject json = new JSONObject(text);
                    
                    // Handle ping from server
                    if (json.has("res") && "pong".equals(json.optString("res"))) {
//                        android.util.Log.d("FnWebSocket", "received pong");
                        return;
                    }
                    
                    if (json.has("pub")) {
                        currentPubKey = json.getString("pub");
                        currentSi = json.optString("si");
                        android.util.Log.d("FnWebSocket", "Got SI: " + currentSi);
                        performEncryptedLogin(webSocket, currentPubKey, currentSi, username, password, callback);
                    } else if ("succ".equals(json.optString("result"))) {
                        if (isFinalLoginSuccess(json)) {
                            FnProtocolUtils.setBackId(json.optString("backId"));
                            callback.onSuccess(json);
                        } else if (isTwoFactorChallenge(json)) {
                            currentAccessToken = json.optString("accessToken");
                            callback.onTwoFactorRequired(TwoFactorChallenge.from(json));
                        } else {
                            android.util.Log.w("FnWebSocket", "Unsupported login success response without session");
                            callback.onError("服务器要求暂不支持的登录验证方式");
                        }
                    } else if (json.has("errno")) {
                        int errno = json.optInt("errno");
                        String result = json.optString("result");
                        android.util.Log.e("FnWebSocket", "Server error: errno=" + errno + ", result=" + result);
                        callback.onError(messageForError(errno, result));
                    }
                } catch (Exception e) {
                    android.util.Log.e("FnWebSocket", "Error processing message: " + e.getMessage(), e);
                    callback.onError(e.getMessage());
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                android.util.Log.e("FnWebSocket", "Connection failed: " + t.getMessage(), t);
                callback.onError("Connection failed: " + t.getMessage());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                android.util.Log.w("FnWebSocket", "Closing: " + code + " - " + reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                android.util.Log.w("FnWebSocket", "Closed: " + code + " - " + reason);
            }
        });
            android.util.Log.d("FnWebSocket", "WebSocket created successfully");
        } catch (Exception e) {
            android.util.Log.e("FnWebSocket", "Failed to create WebSocket: " + e.getMessage(), e);
            callback.onError("Failed to create WebSocket: " + e.getMessage());
        }
    }

    public void submitTotp(String code, boolean trustDevice, LoginCallback callback) {
        if (mainWs == null || !hasValue(currentPubKey) || !hasValue(currentSi) || !hasValue(currentAccessToken)) {
            callback.onError("二次验证会话已失效，请重新输入密码登录");
            return;
        }
        if (!hasValue(code) || !code.trim().matches("\\d{6}")) {
            callback.onError("请输入 6 位动态验证码");
            return;
        }

        try {
            JSONObject rawData = buildTotpVerifyPayload(
                    code.trim(),
                    currentAccessToken,
                    trustDevice,
                    currentStay,
                    currentDeviceType,
                    currentDeviceName,
                    currentDeviceId,
                    currentSi
            );
            sendEncryptedRequest(mainWs, currentPubKey, rawData, callback, "TOTP verification");
        } catch (Exception e) {
            android.util.Log.e("FnWebSocket", "Failed to prepare TOTP verification", e);
            callback.onError("动态验证码验证失败: " + e.getMessage());
        }
    }

    private void requestRsaKey(WebSocket ws) {
        try {
            JSONObject req = new JSONObject();
            req.put("req", "util.crypto.getRSAPub");
            req.put("reqid", FnProtocolUtils.generateReqId());
            String jsonStr = req.toString();
            android.util.Log.d("FnWebSocket", "Sending: " + jsonStr);
            boolean sent = ws.send(jsonStr);
            android.util.Log.d("FnWebSocket", "Send result: " + sent);
        } catch (Exception e) {
            android.util.Log.e("FnWebSocket", "Error sending RSA request", e);
        }
    }

    private void performEncryptedLogin(final WebSocket ws, final String pubKey, final String si, 
                                       final String user, final String pass, final LoginCallback callback) {
        // Run encryption on background thread to avoid blocking WebSocket
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    android.util.Log.d("FnWebSocket", "Starting encrypted login on background thread, si=" + si);
                    
                    // 1. Prepare raw login data - MUST match Python format exactly
                    // Reference: fnnas-api/sdk/encryption.py
                    final JSONObject rawData = new JSONObject();
                    rawData.put("reqid", FnProtocolUtils.generateReqId());
                    rawData.put("user", user);
                    rawData.put("password", pass);
                    rawData.put("deviceType", currentDeviceType);
                    rawData.put("deviceName", currentDeviceName);
                    rawData.put("stay", false);  // Must be lowercase 'false' for JSON
                    rawData.put("did", currentDeviceId);
                    rawData.put("req", "user.login");
                    rawData.put("si", si);  // Must be String, not Long
                    
                    sendEncryptedRequest(ws, pubKey, rawData, callback, "Login");
                    
                } catch (final Exception e) {
                    android.util.Log.e("FnWebSocket", "Encryption error", e);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("Encryption failed: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void sendEncryptedRequest(final WebSocket ws, final String pubKey, final JSONObject rawData,
                                      final LoginCallback callback, final String label) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    android.util.Log.d("FnWebSocket", label + " request prepared (data suppressed for security)");
                    final String rawDataStr = rawData.toString();

                    // Generate AES key (MUST be 32 chars for fnOS) and IV (16 bytes).
                    final String aesKey = FnProtocolUtils.generateRandomString(32);
                    final byte[] iv = FnProtocolUtils.generateIV();

                    final String aesEncrypted = FnProtocolUtils.aesEncrypt(rawDataStr, aesKey, iv);
                    final String rsaEncrypted = FnProtocolUtils.rsaEncrypt(pubKey, aesKey);
                    android.util.Log.d("FnWebSocket", label + " AES encrypted, RSA encrypted");

                    // reqid stays inside the AES payload, matching the fnOS encrypted request format.
                    final JSONObject encryptedReq = new JSONObject();
                    encryptedReq.put("req", "encrypted");
                    encryptedReq.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
                    encryptedReq.put("rsa", rsaEncrypted);
                    encryptedReq.put("aes", aesEncrypted);

                    final String finalPayload = encryptedReq.toString();

                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            boolean sent = ws.send(finalPayload);
                            android.util.Log.d("FnWebSocket", label + " sent: " + sent);
                        }
                    });
                } catch (final Exception e) {
                    android.util.Log.e("FnWebSocket", label + " encryption error", e);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(label + " failed: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    public static boolean isFinalLoginSuccess(JSONObject json) {
        return json != null
                && "succ".equals(json.optString("result"))
                && hasValue(json.optString("token"))
                && hasValue(json.optString("secret"));
    }

    public static boolean isTwoFactorChallenge(JSONObject json) {
        return json != null
                && "succ".equals(json.optString("result"))
                && !hasValue(json.optString("token"))
                && hasValue(json.optString("accessToken"));
    }

    public static JSONObject buildTotpVerifyPayload(String code, String accessToken, boolean trustDevice,
                                                    int stay, String deviceType, String deviceName,
                                                    String deviceId, String si) throws Exception {
        JSONObject rawData = new JSONObject();
        rawData.put("reqid", FnProtocolUtils.generateReqId());
        rawData.put("code", code);
        rawData.put("isTrustedDevice", trustDevice);
        rawData.put("accessToken", accessToken);
        rawData.put("stay", stay);
        rawData.put("deviceName", deviceName);
        rawData.put("deviceType", deviceType);
        rawData.put("did", deviceId);
        rawData.put("req", "user.2fa.loginVerify");
        rawData.put("si", si);
        return rawData;
    }

    private static String messageForError(int errno, String fallback) {
        switch (errno) {
            case 102570104:
            case 102570111:
            case 135168:
                return "动态验证码错误或已过期";
            case 102570109:
                return "二次验证已过期，请重新输入密码登录";
            case 102570110:
                return "账号尚未绑定双重验证";
            case 102570114:
                return "动态验证码验证异常，请稍后重试";
            case 131089:
                return "当前 IP 已被临时封禁";
            default:
                return "Server error " + errno + ": " + fallback;
        }
    }

    private static boolean hasValue(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static final class TwoFactorChallenge {
        public final String accessToken;
        public final String secureEmail;
        public final boolean isTwofaEnforced;
        public final boolean isBindTwofaSecret;
        public final boolean isBindSecureEmail;
        public final boolean isTrustedDevice;
        public final String twofaSecret;
        public final String otpauth;
        public final String hostName;
        public final String username;

        private TwoFactorChallenge(JSONObject json) {
            accessToken = json.optString("accessToken", "");
            secureEmail = json.optString("secureEmail", "");
            isTwofaEnforced = json.optBoolean("isTwofaEnforced", false);
            isBindTwofaSecret = json.optBoolean("isBindTwofaSecret", false);
            isBindSecureEmail = json.optBoolean("isBindSecureEmail", false);
            isTrustedDevice = json.optBoolean("isTrustedDevice", false);
            twofaSecret = json.optString("twofaSecret", "");
            otpauth = json.optString("otpauth", "");
            hostName = json.optString("hostName", "");
            username = json.optString("username", "");
        }

        public static TwoFactorChallenge from(JSONObject json) {
            return new TwoFactorChallenge(json);
        }

        public boolean canVerifyWithTotp() {
            return isBindTwofaSecret && hasValue(accessToken);
        }
    }
}
