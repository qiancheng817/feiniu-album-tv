package com.fnphoto.tv.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FnConnectApi {
    private static final String TAG = "FnConnectApi";
    private static final String[] FN_CONNECT_API_URLS = {
        "https://fnos.net/api/v1/fn/con"
    };
    private static final String SECRET_KEY = "anna";
    private static final String AUTHX_PREFIX = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    private static final String AUTHX_API_KEY = "zIGtkc3dqZnJpd29qZXJqa2w7c";
    private static final int PROBE_TIMEOUT_MS = 3000;

    private OkHttpClient client;

    public FnConnectApi() {
        this.client = TlsUtils.enableTlsOnApi19(new OkHttpClient.Builder())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    }

    public interface NasListCallback {
        void onSuccess(NasListResponse response);
        void onError(String error);
    }

    public static class NasAddr {
        public String address;
        public String type;
        public int port;

        public NasAddr(String address, String type, int defaultPort) {
            this.type = type;
            // Parse port from address if present.
            // Skip IPv6 addresses - they contain colons
            if (!type.equals("ipv6") && address.contains(":") && !address.startsWith("[")) {
                int colonIdx = address.lastIndexOf(":");
                try {
                    this.port = Integer.parseInt(address.substring(colonIdx + 1));
                    this.address = address.substring(0, colonIdx);
                } catch (NumberFormatException e) {
                    // Not a port number, use default
                    this.address = address;
                    this.port = defaultPort;
                }
            } else {
                this.address = address;
                this.port = defaultPort;
            }
        }

        public String toHttpUrl() {
            if (type.equals("ipv6")) {
                return "http://[" + address + "]:" + port;
            }
            return "http://" + address + ":" + port;
        }

        public String toWsUrl() {
            if (type.equals("ipv6")) {
                return "ws://[" + address + "]:" + port + "/websocket?type=main";
            }
            return "ws://" + address + ":" + port + "/websocket?type=main";
        }
    }

    public static class NasListResponse {
        public List<NasAddr> addresses = new ArrayList<>();
        public String ver;
        public String checkSum;
    }

    public void fetchNasList(String fnId, NasListCallback callback) {
        tryApiUrl(fnId, 0, callback);
    }

    private void tryApiUrl(final String fnId, final int urlIndex, final NasListCallback callback) {
        if (urlIndex >= FN_CONNECT_API_URLS.length) {
            callback.onError("All API endpoints unreachable");
            return;
        }

        long timestamp = System.currentTimeMillis();
        String sign = generateSign(fnId, timestamp);

        try {
            JSONObject body = new JSONObject();
            body.put("fnId", fnId);
            String bodyStr = body.toString();

            // Generate authx header (matching axios interceptor)
            String nonce = String.format("%06d", (int)(Math.random() * 900000) + 100000);
            String bodyMd5 = md5(bodyStr);
            String rawAuth = AUTHX_PREFIX + "_" + "/api/v1/fn/con" + "_" + nonce + "_" + timestamp + "_" + bodyMd5 + "_" + AUTHX_API_KEY;
            String authxSign = md5(rawAuth);
            String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + authxSign;

            Request request = new Request.Builder()
                .url(FN_CONNECT_API_URLS[urlIndex])
                .post(RequestBody.create(MediaType.parse("application/json"), bodyStr))
                .header("fn-sign", sign)
                .header("authx", authx)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        String respBody = response.body() != null ? response.body().string() : "";
                        Log.d(TAG, "NAS list response from " + FN_CONNECT_API_URLS[urlIndex] + ": " + respBody);

                        if (!response.isSuccessful()) {
                            Log.w(TAG, "HTTP " + response.code() + ", trying next URL");
                            tryApiUrl(fnId, urlIndex + 1, callback);
                            return;
                        }

                        JSONObject rootJson = new JSONObject(respBody);
                        int code = rootJson.optInt("code", -1);
                        if (code != 0) {
                            Log.w(TAG, "API error code " + code + ": " + rootJson.optString("msg"));
                            tryApiUrl(fnId, urlIndex + 1, callback);
                            return;
                        }

                        JSONObject json = rootJson.optJSONObject("data");
                        if (json == null) {
                            Log.w(TAG, "No data in response, trying next URL");
                            tryApiUrl(fnId, urlIndex + 1, callback);
                            return;
                        }

                        NasListResponse nasResp = new NasListResponse();
                        nasResp.ver = json.optString("ver");
                        nasResp.checkSum = json.optString("checkSum");

                        JSONObject portObj = json.optJSONObject("port");
                        int httpPort = portObj != null ? portObj.optInt("httpPort", 8000) : 8000;

                        addAddresses(json.optJSONArray("ipv4"), "ipv4", httpPort, nasResp);
                        addAddresses(json.optJSONArray("ipv6"), "ipv6", httpPort, nasResp);
                        addAddresses(json.optJSONArray("ddns"), "ddns", httpPort, nasResp);
                        addAddresses(json.optJSONArray("fn"), "relay", httpPort, nasResp);
                        addAddresses(json.optJSONArray("publicIpv4"), "publicIpv4", httpPort, nasResp);
                        addAddresses(json.optJSONArray("publicIpv6"), "publicIpv6", httpPort, nasResp);

                        callback.onSuccess(nasResp);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error", e);
                        tryApiUrl(fnId, urlIndex + 1, callback);
                    }
                }

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Network error on " + FN_CONNECT_API_URLS[urlIndex] + ": " + e.getMessage());
                    tryApiUrl(fnId, urlIndex + 1, callback);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Request error", e);
            tryApiUrl(fnId, urlIndex + 1, callback);
        }
    }

    private void addAddresses(JSONArray arr, String type, int port, NasListResponse resp) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            resp.addresses.add(new NasAddr(arr.optString(i), type, port));
        }
    }

    public static String findReachableAddrSync(List<NasAddr> addresses) {
        if (addresses == null || addresses.isEmpty()) return null;

        // Sort by priority (local first)
        java.util.Collections.sort(addresses, (a, b) -> Integer.compare(priority(a), priority(b)));

        Log.d(TAG, "Probing addresses...");
        for (NasAddr addr : addresses) {
            Log.d(TAG, "  " + addr.type + " -> " + addr.address + ":" + addr.port);
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>(null);

        for (final NasAddr addr : addresses) {
            final String httpUrl = addr.toHttpUrl();
            final int p = priority(addr);
            final long delayMs = p <= 3 ? 0 : 1500;  // local start immediately, others delayed
            final int timeoutMs = p <= 3 ? 2000 : 4000;

            new Thread(() -> {
                if (result.get() != null) return;
                if (delayMs > 0) {
                    try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                }
                if (result.get() != null) return;
                try {
                    Socket socket = new Socket();
                    String host = addr.address;
                    if (addr.type.equals("ipv6")) {
                        socket.connect(new InetSocketAddress(java.net.InetAddress.getByName(host), addr.port), timeoutMs);
                    } else {
                        socket.connect(new InetSocketAddress(host, addr.port), timeoutMs);
                    }
                    socket.close();
                    Log.d(TAG, "Reachable: " + httpUrl);
                    if (result.compareAndSet(null, httpUrl)) {
                        latch.countDown();
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Unreachable: " + httpUrl + " - " + e.getMessage());
                }
            }).start();
        }

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Probe interrupted", e);
        }
        return result.get();
    }

    private static int priority(NasAddr addr) {
        // Highest priority: local IPv4 (fast, same network)
        String a = addr.address;
        if (addr.type.equals("ipv4") || addr.type.equals("ipv6")) {
            if (addr.type.equals("ipv4") && a.startsWith("192.168.")) return 0;
            if (addr.type.equals("ipv4") && a.startsWith("10.")) return 1;
            if (addr.type.equals("ipv4") && a.startsWith("172.")) return 2;
            if (addr.type.equals("ipv4")) return 3; // other private or local
            if (addr.type.equals("ipv6")) return 4;
        }
        if (addr.type.equals("ddns")) return 5;
        if (addr.type.equals("relay")) return 6;
        if (addr.type.equals("publicIpv4")) return 7;
        if (addr.type.equals("publicIpv6")) return 8;
        return 9;
    }

    public static boolean isFnId(String input) {
        if (input == null || input.isEmpty()) return false;
        if (input.contains(".") || input.contains(":") || input.contains("/")) return false;
        if (input.equalsIgnoreCase("localhost")) return false;
        return input.matches("^[a-zA-Z][a-zA-Z0-9-]{4,31}$") && !input.endsWith("-");
    }

    private String generateSign(String fnId, long timestamp) {
        try {
            String raw = "trim_connect`" + fnId + "`" + timestamp + "`" + SECRET_KEY;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (Exception e) {
            Log.e(TAG, "Sign generation error", e);
            return "";
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (Exception e) {
            Log.e(TAG, "MD5 error", e);
            return "";
        }
    }
}
