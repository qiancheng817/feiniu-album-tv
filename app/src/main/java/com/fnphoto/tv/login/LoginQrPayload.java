package com.fnphoto.tv.login;

import java.nio.charset.StandardCharsets;

public final class LoginQrPayload {
    private static final char[] BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final String CLIENT_NAME = "飞牛TV";

    private LoginQrPayload() {
    }

    public static String buildUri(String code, int osVersion, String deviceName) {
        String safeDeviceName = deviceName == null || deviceName.trim().isEmpty()
                ? "Android TV"
                : deviceName.trim();
        return "fn://com.trim.tv/trim.media-center"
                + "?platform=AndroidTV"
                + "&osver=" + osVersion
                + "&clientName=" + CLIENT_NAME
                + "&code=" + code
                + "&event=scanLogin"
                + "&deviceName=" + safeDeviceName;
    }

    public static String buildEncodedPayload(String code, int osVersion, String deviceName) {
        return encodeBase64(buildUri(code, osVersion, deviceName).getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeBase64(byte[] input) {
        StringBuilder out = new StringBuilder(((input.length + 2) / 3) * 4);
        for (int i = 0; i < input.length; i += 3) {
            int b0 = input[i] & 0xFF;
            int b1 = i + 1 < input.length ? input[i + 1] & 0xFF : 0;
            int b2 = i + 2 < input.length ? input[i + 2] & 0xFF : 0;

            out.append(BASE64[b0 >>> 2]);
            out.append(BASE64[((b0 & 0x03) << 4) | (b1 >>> 4)]);
            out.append(i + 1 < input.length ? BASE64[((b1 & 0x0F) << 2) | (b2 >>> 6)] : '=');
            out.append(i + 2 < input.length ? BASE64[b2 & 0x3F] : '=');
        }
        return out.toString();
    }
}
