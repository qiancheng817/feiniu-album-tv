package com.fnphoto.tv.api;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FnWebSocketClientTest {

    @Test
    public void loginResponseWithToken_isFinalSession() throws Exception {
        JSONObject response = new JSONObject()
                .put("result", "succ")
                .put("token", "session-token")
                .put("secret", "session-secret")
                .put("backId", "6a41189600000968");

        assertTrue(FnWebSocketClient.isFinalLoginSuccess(response));
        assertFalse(FnWebSocketClient.isTwoFactorChallenge(response));
    }

    @Test
    public void loginResponseWithAccessToken_isTwoFactorChallenge() throws Exception {
        JSONObject response = new JSONObject()
                .put("result", "succ")
                .put("isBindTwofaSecret", true)
                .put("isTrustedDevice", false)
                .put("accessToken", "temporary-login-token")
                .put("secureEmail", "u***@example.com");

        assertFalse(FnWebSocketClient.isFinalLoginSuccess(response));
        assertTrue(FnWebSocketClient.isTwoFactorChallenge(response));

        FnWebSocketClient.TwoFactorChallenge challenge =
                FnWebSocketClient.TwoFactorChallenge.from(response);
        assertEquals("temporary-login-token", challenge.accessToken);
        assertEquals("u***@example.com", challenge.secureEmail);
        assertTrue(challenge.isBindTwofaSecret);
        assertFalse(challenge.isTrustedDevice);
    }

    @Test
    public void totpVerifyPayload_usesFnOsLoginVerifyFields() throws Exception {
        JSONObject payload = FnWebSocketClient.buildTotpVerifyPayload(
                "123456",
                "temporary-login-token",
                true,
                0,
                "AndroidTV",
                "Android-TV-Box",
                "device-id",
                "si-value"
        );

        assertEquals("user.2fa.loginVerify", payload.getString("req"));
        assertEquals("123456", payload.getString("code"));
        assertEquals("temporary-login-token", payload.getString("accessToken"));
        assertTrue(payload.getBoolean("isTrustedDevice"));
        assertEquals(0, payload.getInt("stay"));
        assertEquals("AndroidTV", payload.getString("deviceType"));
        assertEquals("Android-TV-Box", payload.getString("deviceName"));
        assertEquals("device-id", payload.getString("did"));
        assertEquals("si-value", payload.getString("si"));
    }
}
