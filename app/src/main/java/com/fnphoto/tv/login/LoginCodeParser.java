package com.fnphoto.tv.login;

import org.json.JSONObject;

public final class LoginCodeParser {
    private LoginCodeParser() {
    }

    public static final class GenerateResult {
        public final String code;

        public GenerateResult(String code) {
            this.code = code;
        }
    }

    public static final class StatusResult {
        public final String status;
        public final String token;
        public final String secret;
        public final String backId;

        public StatusResult(String status, String token, String secret, String backId) {
            this.status = status;
            this.token = token;
            this.secret = secret;
            this.backId = backId;
        }

        public boolean isAuthenticated() {
            return notBlank(token) && "Success".equalsIgnoreCase(status);
        }
    }

    public static GenerateResult parseGenerateResponse(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONObject data = root.optJSONObject("data");
        String code = data != null ? data.optString("code", "") : root.optString("code", "");
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing login code");
        }
        return new GenerateResult(code);
    }

    public static StatusResult parseStatusResponse(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONObject data = root.optJSONObject("data");
        JSONObject source = data != null ? data : root;

        String status = source.optString("status", root.optString("status", ""));
        String token = firstNonBlank(
                source.optString("token", ""),
                source.optString("accessToken", ""),
                source.optString("access_token", ""),
                root.optString("token", "")
        );
        String secret = firstNonBlank(source.optString("secret", ""), root.optString("secret", ""));
        String backId = firstNonBlank(
                source.optString("backId", ""),
                source.optString("back_id", ""),
                root.optString("backId", "")
        );

        JSONObject session = source.optJSONObject("session");
        if (session != null) {
            token = firstNonBlank(token, session.optString("token", ""), session.optString("accessToken", ""));
            secret = firstNonBlank(secret, session.optString("secret", ""));
            backId = firstNonBlank(backId, session.optString("backId", ""), session.optString("back_id", ""));
        }

        return new StatusResult(status, token, secret, backId);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (notBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
