package com.fnphoto.tv.api;

import android.content.Context;
import android.util.Log;

import com.fnphoto.tv.login.LoginDeviceIdentity;
import com.fnphoto.tv.settings.UserProfileStore;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Reauthenticator {
    private static final String TAG = "Reauthenticator";
    private static final long LOGIN_TIMEOUT_SECONDS = 30;

    public static boolean reLoginSync(Context context) {
        UserProfileStore profileStore = new UserProfileStore(context);
        UserProfileStore.RememberedCredentials credentials = profileStore.getActiveRememberedCredentials();

        if (credentials == null || credentials.url.isEmpty() || credentials.user.isEmpty() || credentials.pass.isEmpty()) {
            Log.e(TAG, "No saved credentials for re-login");
            return false;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};
        final JSONObject[] resultJson = {null};

        FnWebSocketClient wsClient = new FnWebSocketClient();
        wsClient.startLogin(credentials.url, credentials.user, credentials.pass, LoginDeviceIdentity.getOrCreate(context), new FnWebSocketClient.LoginCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                resultJson[0] = response;
                success[0] = true;
                latch.countDown();
            }

            @Override
            public void onTwoFactorRequired(FnWebSocketClient.TwoFactorChallenge challenge) {
                Log.w(TAG, "Re-login requires two-factor verification");
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Re-login error: " + error);
                latch.countDown();
            }
        });

        try {
            latch.await(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Re-login interrupted", e);
            return false;
        }

        if (success[0] && resultJson[0] != null) {
            try {
                JSONObject json = resultJson[0];
                String newToken = json.optString("token", "");
                String newSecret = json.optString("secret", "");
                String newBackId = json.optString("backId", "");

                if (!newToken.isEmpty() && !newSecret.isEmpty()) {
                    profileStore.updateActiveSession(newToken, newSecret, newBackId);

                    FnProtocolUtils.setBackId(newBackId);
                    Log.i(TAG, "Re-login successful");
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse re-login result", e);
            }
        }

        return false;
    }
}
