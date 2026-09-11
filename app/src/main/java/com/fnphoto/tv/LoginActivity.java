package com.fnphoto.tv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.fnphoto.tv.api.FnWebSocketClient;
import com.fnphoto.tv.login.LanServerDiscovery;
import com.fnphoto.tv.login.LoginDeviceIdentity;
import com.fnphoto.tv.login.LoginCodeClient;
import com.fnphoto.tv.login.LoginCodeParser;
import com.fnphoto.tv.login.LoginQrPayload;
import com.fnphoto.tv.login.QrCodeRenderer;
import com.fnphoto.tv.login.SavedServerHistory;
import com.fnphoto.tv.settings.UserProfileStore;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LoginActivity extends FragmentActivity {
    private static final String TAG = "LoginActivity";
    private static final String PREFS = "fn_photo_prefs";
    private static final long QR_POLL_INTERVAL_MS = 2200L;
    public static final String EXTRA_ADD_PROFILE = "com.fnphoto.tv.extra.ADD_PROFILE";

    private View panelServerSelect;
    private View panelConnecting;
    private View panelLogin;
    private LinearLayout serverList;
    private View manualAddPanel;
    private EditText editUrl;
    private EditText editUser;
    private EditText editPass;
    private EditText editTotp;
    private CheckBox cbRemember;
    private CheckBox cbTrustDevice;
    private CheckBox cbDisclaimerAgree;
    private ProgressBar progressBar;
    private ProgressBar progressQr;
    private TextView tvStatus;
    private TextView tvQrStatus;
    private TextView tvConnectServerName;
    private TextView tvConnectServerHost;
    private TextView tvCurrentServerName;
    private TextView tvCurrentServerHost;
    private TextView tabQrLogin;
    private TextView tabAccountLogin;
    private TextView tvTotpHint;
    private TextView tvDisclaimerAgreement;
    private View qrLoginBody;
    private View accountLoginBody;
    private View accountOptionsRow;
    private View disclaimerDialog;
    private ImageView imgQrCode;
    private Button btnLogin;
    private Button btnSwitchServer;
    private Button btnManualConnect;
    private Button btnCancelManual;
    private Button btnCloseDisclaimer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LoginCodeClient loginCodeClient = new LoginCodeClient();
    private final List<LanServerDiscovery.ServerCandidate> servers = new ArrayList<>();
    private final Set<String> knownServerUrls = new LinkedHashSet<>();
    private int pendingDiscoveryTasks = 0;
    private LanServerDiscovery.ServerCandidate selectedServer;
    private FnWebSocketClient wsClient;
    private Runnable qrPollRunnable;
    private String currentLoginCode;
    private FnWebSocketClient.TwoFactorChallenge pendingTwoFactorChallenge;
    private String pendingLoginUrl;
    private String pendingLoginUser;
    private String pendingLoginPass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean addProfile = getIntent() != null && getIntent().getBooleanExtra(EXTRA_ADD_PROFILE, false);
        if (!addProfile && !prefs.getString("api_token", "").isEmpty()) {
            startMain();
            return;
        }

        showLoginUI();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void showLoginUI() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_login);
        bindViews();
        bindActions();
        loadSavedInputs();
        renderServerCards();
        startSavedServerDiscovery();
        startLanDiscovery();
    }

    private void bindViews() {
        panelServerSelect = findViewById(R.id.panel_server_select);
        panelConnecting = findViewById(R.id.panel_connecting);
        panelLogin = findViewById(R.id.panel_login);
        serverList = findViewById(R.id.server_list);
        manualAddPanel = findViewById(R.id.manual_add_panel);
        editUrl = findViewById(R.id.edit_nas_url);
        editUser = findViewById(R.id.edit_username);
        editPass = findViewById(R.id.edit_api_token);
        editTotp = findViewById(R.id.edit_totp_code);
        cbRemember = findViewById(R.id.cb_remember);
        cbTrustDevice = findViewById(R.id.cb_trust_device);
        cbDisclaimerAgree = findViewById(R.id.cb_disclaimer_agree);
        progressBar = findViewById(R.id.progress_bar);
        progressQr = findViewById(R.id.progress_qr);
        tvStatus = findViewById(R.id.tv_status);
        tvQrStatus = findViewById(R.id.tv_qr_status);
        tvConnectServerName = findViewById(R.id.tv_connect_server_name);
        tvConnectServerHost = findViewById(R.id.tv_connect_server_host);
        tvCurrentServerName = findViewById(R.id.tv_current_server_name);
        tvCurrentServerHost = findViewById(R.id.tv_current_server_host);
        tabQrLogin = findViewById(R.id.tab_qr_login);
        tabAccountLogin = findViewById(R.id.tab_account_login);
        tvTotpHint = findViewById(R.id.tv_totp_hint);
        tvDisclaimerAgreement = findViewById(R.id.tv_disclaimer_agreement);
        qrLoginBody = findViewById(R.id.qr_login_body);
        accountLoginBody = findViewById(R.id.account_login_body);
        accountOptionsRow = findViewById(R.id.account_options_row);
        disclaimerDialog = findViewById(R.id.disclaimer_dialog);
        imgQrCode = findViewById(R.id.img_qr_code);
        btnLogin = findViewById(R.id.btn_login);
        btnSwitchServer = findViewById(R.id.btn_switch_server);
        btnManualConnect = findViewById(R.id.btn_manual_connect);
        btnCancelManual = findViewById(R.id.btn_cancel_manual);
        btnCloseDisclaimer = findViewById(R.id.btn_close_disclaimer);
    }

    private void bindActions() {
        btnManualConnect.setOnClickListener(v -> connectManualServer());
        btnCancelManual.setOnClickListener(v -> hideManualAddPanel());
        btnSwitchServer.setOnClickListener(v -> showServerSelection());
        tabQrLogin.setOnClickListener(v -> showQrTab());
        tabAccountLogin.setOnClickListener(v -> showAccountTab());
        btnLogin.setOnClickListener(v -> performAccountLogin());
        btnCloseDisclaimer.setOnClickListener(v -> hideDisclaimerDialog());
        tvDisclaimerAgreement.setText(buildDisclaimerAgreementText());
        tvDisclaimerAgreement.setMovementMethod(LinkMovementMethod.getInstance());
        tvDisclaimerAgreement.setHighlightColor(0x00000000);
        tvDisclaimerAgreement.setOnClickListener(v -> showDisclaimerDialog());
        cbDisclaimerAgree.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (isChecked) {
                prefs.edit()
                        .putBoolean("disclaimer_agreed", true)
                        .apply();
            } else if (prefs.getBoolean("disclaimer_agreed", false)) {
                buttonView.setChecked(true);
            }
        });

        editUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || isEnterDown(event)) {
                connectManualServer();
                return true;
            }
            return false;
        });
        editPass.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || isEnterDown(event)) {
                performAccountLogin();
                return true;
            }
            return false;
        });
        editTotp.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || isEnterDown(event)) {
                performAccountLogin();
                return true;
            }
            return false;
        });

        TextWatcher clearTwoFactorOnCredentialChange = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingTwoFactorChallenge != null) {
                    clearTwoFactorChallenge();
                }
            }
        };
        editUser.addTextChangedListener(clearTwoFactorOnCredentialChange);
        editPass.addTextChangedListener(clearTwoFactorOnCredentialChange);
    }

    @Override
    public void onBackPressed() {
        if (disclaimerDialog != null && disclaimerDialog.getVisibility() == View.VISIBLE) {
            hideDisclaimerDialog();
            return;
        }
        super.onBackPressed();
    }

    private void loadSavedInputs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean addProfile = getIntent() != null && getIntent().getBooleanExtra(EXTRA_ADD_PROFILE, false);
        UserProfileStore.RememberedCredentials credentials =
                addProfile ? null : new UserProfileStore(this).getGlobalRememberedCredentials();
        editUrl.setText(prefs.getString("saved_url", prefs.getString("nas_url", "")));
        if (credentials != null) {
            editUrl.setText(credentials.url);
            editUser.setText(credentials.user);
            editPass.setText(credentials.pass);
        } else {
            editUser.setText("");
            editPass.setText("");
        }
        cbRemember.setChecked(prefs.getBoolean("has_credentials", true));
        cbDisclaimerAgree.setChecked(prefs.getBoolean("disclaimer_agreed", false));
    }

    private void startSavedServerDiscovery() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> seededUrls = new LinkedHashSet<>();
        String lastConnected = firstNonBlank(prefs.getString("nas_url", ""), prefs.getString("saved_url", ""));
        if (!lastConnected.isEmpty()) {
            seededUrls.add(LanServerDiscovery.normalizeServerInput(lastConnected));
        }
        for (String savedUrl : SavedServerHistory.decode(prefs.getString(SavedServerHistory.PREF_KEY, ""))) {
            String baseUrl = LanServerDiscovery.normalizeServerInput(savedUrl);
            if (!baseUrl.isEmpty()) {
                seededUrls.add(baseUrl);
            }
        }
        if (seededUrls.isEmpty()) {
            return;
        }

        beginDiscoveryTask();
        LanServerDiscovery.discoverSavedAsync(new ArrayList<>(seededUrls), found -> runOnUiThread(() -> {
            for (LanServerDiscovery.ServerCandidate candidate : found) {
                addServer(candidate);
            }
            finishDiscoveryTask();
        }));
    }

    private void startLanDiscovery() {
        beginDiscoveryTask();
        LanServerDiscovery.discoverAsync(found -> runOnUiThread(() -> {
            for (LanServerDiscovery.ServerCandidate candidate : found) {
                addServer(candidate);
            }
            finishDiscoveryTask();
        }));
    }

    private void beginDiscoveryTask() {
        pendingDiscoveryTasks++;
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("正在搜索局域网内的飞牛服务器");
    }

    private void finishDiscoveryTask() {
        pendingDiscoveryTasks = Math.max(0, pendingDiscoveryTasks - 1);
        renderServerCards();
        if (pendingDiscoveryTasks > 0) {
            return;
        }
        progressBar.setVisibility(View.GONE);
        tvStatus.setText(servers.isEmpty()
                ? "没有自动发现服务器，可以手动添加地址"
                : "选择一个服务器继续");
    }

    private void renderServerCards() {
        serverList.removeAllViews();
        serverList.addView(createAddServerCard());
        for (LanServerDiscovery.ServerCandidate server : servers) {
            serverList.addView(createServerCard(server));
        }
        if (serverList.getChildCount() > 0 && manualAddPanel.getVisibility() != View.VISIBLE) {
            handler.postDelayed(() -> serverList.getChildAt(0).requestFocus(), 150);
        }
    }

    private View createAddServerCard() {
        LinearLayout card = baseServerCard();
        card.setOnClickListener(v -> {
            if (manualAddPanel.getVisibility() == View.VISIBLE) {
                hideManualAddPanel();
            } else {
                showManualAddPanel();
            }
        });

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(0xFFE8ECF4);
        plus.setTextSize(46);
        plus.setGravity(Gravity.CENTER);
        plus.setBackgroundResource(R.drawable.bg_login_card_soft);
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(dp(86), dp(86));
        card.addView(plus, plusParams);

        TextView title = cardTitle("添加服务器");
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(26);
        card.addView(title, titleParams);
        return card;
    }

    private View createServerCard(LanServerDiscovery.ServerCandidate server) {
        LinearLayout card = baseServerCard();
        card.setOnClickListener(v -> connectServer(server));

        TextView icon = new TextView(this);
        icon.setText("NAS");
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(0xFFDDE8FF);
        icon.setTextSize(22);
        icon.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        icon.setBackgroundResource(R.drawable.bg_login_card_soft);
        card.addView(icon, new LinearLayout.LayoutParams(dp(104), dp(72)));

        TextView title = cardTitle(server.name);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(24);
        card.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText(server.host + ":" + server.port);
        subtitle.setTextColor(0xFFB6BEC9);
        subtitle.setTextSize(17);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(6);
        card.addView(subtitle, subtitleParams);
        return card;
    }

    private LinearLayout baseServerCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackgroundResource(R.drawable.bg_login_card);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(220), dp(220));
        params.setMarginEnd(dp(28));
        card.setLayoutParams(params);
        return card;
    }

    private TextView cardTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(false);
        title.setMaxLines(2);
        return title;
    }

    private void connectManualServer() {
        String baseUrl = LanServerDiscovery.normalizeServerInput(editUrl.getText().toString());
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        LanServerDiscovery.ServerCandidate server =
                new LanServerDiscovery.ServerCandidate("手动服务器", baseUrl, hostLabel(baseUrl), portOf(baseUrl), false);
        addServer(server);
        persistSavedServer(baseUrl);
        connectServer(server);
    }

    private void showManualAddPanel() {
        manualAddPanel.setVisibility(View.VISIBLE);
        manualAddPanel.bringToFront();
        editUrl.requestFocus();
        editUrl.setSelection(editUrl.getText().length());
    }

    private void hideManualAddPanel() {
        manualAddPanel.setVisibility(View.GONE);
        if (serverList.getChildCount() > 0) {
            serverList.getChildAt(0).requestFocus();
        }
    }

    private void connectServer(LanServerDiscovery.ServerCandidate server) {
        clearTwoFactorChallenge();
        selectedServer = server;
        manualAddPanel.setVisibility(View.GONE);
        stopQrPolling();
        showConnecting(server);
        new Thread(() -> {
            String serverName = loginCodeClient.fetchServerName(server.baseUrl);
            if (serverName == null || serverName.trim().isEmpty()) {
                serverName = server.name;
            }
            final String finalServerName = serverName;
            runOnUiThread(() -> {
                selectedServer = new LanServerDiscovery.ServerCandidate(
                        finalServerName,
                        server.baseUrl,
                        server.host,
                        server.port,
                        server.discovered
                );
                tvConnectServerName.setText(finalServerName);
                tvCurrentServerName.setText(finalServerName);
                tvCurrentServerHost.setText(server.host);
                showLoginWithAccount();
            });
        }, "fnphoto-connect-server").start();
    }

    private void showConnecting(LanServerDiscovery.ServerCandidate server) {
        panelServerSelect.setVisibility(View.GONE);
        panelLogin.setVisibility(View.GONE);
        panelConnecting.setVisibility(View.VISIBLE);
        tvConnectServerName.setText(server.name);
        tvConnectServerHost.setText(server.host);
    }

    private void showLoginWithAccount() {
        panelServerSelect.setVisibility(View.GONE);
        panelConnecting.setVisibility(View.GONE);
        panelLogin.setVisibility(View.VISIBLE);
        tvCurrentServerName.setText(selectedServer != null ? selectedServer.name : "飞牛服务器");
        tvCurrentServerHost.setText(selectedServer != null ? selectedServer.host : "");
        showAccountTab();
    }

    private void prepareQrLogin(String serverName) {
        if (selectedServer == null) return;
        new Thread(() -> {
            try {
                LoginCodeParser.GenerateResult result = loginCodeClient.generateLoginCode(selectedServer.baseUrl);
                currentLoginCode = result.code;
                String qrPayload = LoginQrPayload.buildEncodedPayload(
                        result.code,
                        Build.VERSION.SDK_INT,
                        serverName
                );
                Bitmap qr = QrCodeRenderer.render(qrPayload, dp(304));
                runOnUiThread(() -> showLoginWithQr(qr, result.code));
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "扫码登录暂不可用: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }, "fnphoto-qr-prepare").start();
    }

    private void showLoginWithQr(Bitmap qr, String code) {
        panelServerSelect.setVisibility(View.GONE);
        panelConnecting.setVisibility(View.GONE);
        panelLogin.setVisibility(View.VISIBLE);
        tvCurrentServerName.setText(selectedServer != null ? selectedServer.name : "飞牛服务器");
        tvCurrentServerHost.setText(selectedServer != null ? selectedServer.host : "");
        imgQrCode.setImageBitmap(qr);
        progressQr.setVisibility(View.GONE);
        tvQrStatus.setText("等待扫码确认");
        showQrTab();
        startQrPolling(code);
        tabQrLogin.requestFocus();
    }

    private void showServerSelection() {
        clearTwoFactorChallenge();
        stopQrPolling();
        panelConnecting.setVisibility(View.GONE);
        panelLogin.setVisibility(View.GONE);
        panelServerSelect.setVisibility(View.VISIBLE);
        manualAddPanel.setVisibility(View.GONE);
        imgQrCode.setImageDrawable(null);
        progressQr.setVisibility(View.VISIBLE);
        if (serverList.getChildCount() > 0) {
            serverList.getChildAt(Math.min(1, serverList.getChildCount() - 1)).requestFocus();
        }
    }

    private void showQrTab() {
        tabQrLogin.setSelected(true);
        tabAccountLogin.setSelected(false);
        tabQrLogin.setTextColor(0xFF111318);
        tabAccountLogin.setTextColor(0xFFC8D0DC);
        qrLoginBody.setVisibility(View.VISIBLE);
        accountLoginBody.setVisibility(View.GONE);
        if (currentLoginCode != null) startQrPolling(currentLoginCode);
    }

    private void showAccountTab() {
        stopQrPolling();
        tabQrLogin.setSelected(false);
        tabAccountLogin.setSelected(true);
        tabQrLogin.setTextColor(0xFFC8D0DC);
        tabAccountLogin.setTextColor(0xFF111318);
        qrLoginBody.setVisibility(View.GONE);
        accountLoginBody.setVisibility(View.VISIBLE);
        editUser.requestFocus();
    }

    private void startQrPolling(String code) {
        stopQrPolling();
        if (selectedServer == null || code == null || code.isEmpty()) return;
        qrPollRunnable = new Runnable() {
            @Override
            public void run() {
                if (selectedServer == null || code == null || code.isEmpty()) return;
                new Thread(() -> {
                    try {
                        LoginCodeParser.StatusResult status = loginCodeClient.checkLoginCode(selectedServer.baseUrl, code);
                        Log.d(TAG, "QR poll parsed status=" + status.status
                                + " authenticated=" + status.isAuthenticated()
                                + " tokenPresent=" + hasValue(status.token)
                                + " secretPresent=" + hasValue(status.secret)
                                + " backIdPresent=" + hasValue(status.backId));
                        if (status.isAuthenticated()) {
                            runOnUiThread(() -> saveQrSession(status));
                        } else {
                            runOnUiThread(() -> {
                                tvQrStatus.setText("等待扫码确认");
                                handler.postDelayed(this, QR_POLL_INTERVAL_MS);
                            });
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "QR poll failed; codePresent=" + hasValue(code), e);
                        runOnUiThread(() -> {
                            tvQrStatus.setText("等待扫码确认");
                            handler.postDelayed(this, QR_POLL_INTERVAL_MS);
                        });
                    }
                }, "fnphoto-qr-poll").start();
            }
        };
        handler.post(qrPollRunnable);
    }

    private void stopQrPolling() {
        if (qrPollRunnable != null) {
            handler.removeCallbacks(qrPollRunnable);
            qrPollRunnable = null;
        }
    }

    private static boolean hasValue(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void performAccountLogin() {
        if (pendingTwoFactorChallenge != null) {
            submitTwoFactorCode();
            return;
        }
        if (selectedServer == null) {
            connectManualServer();
            return;
        }
        String user = editUser.getText().toString().trim();
        String pass = editPass.getText().toString().trim();
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!cbDisclaimerAgree.isChecked()) {
            Toast.makeText(this, "请先阅读并同意免责声明", Toast.LENGTH_SHORT).show();
            tvDisclaimerAgreement.requestFocus();
            return;
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("disclaimer_agreed", true)
                .apply();
        clearTwoFactorChallenge();
        setAccountLoginInProgress(true);
        doWebSocketLogin(selectedServer.baseUrl, user, pass);
    }

    private CharSequence buildDisclaimerAgreementText() {
        String text = getString(R.string.disclaimer_agreement);
        String link = "《免责声明》";
        int start = text.indexOf(link);
        if (start < 0) return text;

        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                showDisclaimerDialog();
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(0xFF6ECFC1);
                ds.setUnderlineText(false);
            }
        }, start, start + link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private void showDisclaimerDialog() {
        disclaimerDialog.setVisibility(View.VISIBLE);
        disclaimerDialog.bringToFront();
        btnCloseDisclaimer.requestFocus();
    }

    private void hideDisclaimerDialog() {
        disclaimerDialog.setVisibility(View.GONE);
        tvDisclaimerAgreement.requestFocus();
    }

    private void doWebSocketLogin(String httpUrl, String user, String pass) {
        wsClient = new FnWebSocketClient();
        wsClient.startLogin(httpUrl, user, pass, LoginDeviceIdentity.getOrCreate(this), new FnWebSocketClient.LoginCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    setAccountLoginInProgress(false);
                    saveAccountSession(httpUrl, user, pass, response);
                });
            }

            @Override
            public void onTwoFactorRequired(FnWebSocketClient.TwoFactorChallenge challenge) {
                runOnUiThread(() -> {
                    setAccountLoginInProgress(false);
                    if (!challenge.canVerifyWithTotp()) {
                        clearTwoFactorChallenge();
                        Toast.makeText(LoginActivity.this,
                                "该账号需要先绑定双重验证，请先在飞牛 App 或网页完成设置",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    pendingLoginUrl = httpUrl;
                    pendingLoginUser = user;
                    pendingLoginPass = pass;
                    showTwoFactorPrompt(challenge);
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    setAccountLoginInProgress(false);
                    Toast.makeText(LoginActivity.this, "登录失败: " + msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void submitTwoFactorCode() {
        String code = editTotp.getText().toString().trim();
        if (!code.matches("\\d{6}")) {
            Toast.makeText(this, "请输入 6 位动态验证码", Toast.LENGTH_SHORT).show();
            editTotp.requestFocus();
            return;
        }
        if (wsClient == null || pendingLoginUrl == null || pendingLoginUser == null || pendingLoginPass == null) {
            clearTwoFactorChallenge();
            Toast.makeText(this, "二次验证会话已失效，请重新输入密码登录", Toast.LENGTH_LONG).show();
            return;
        }

        setAccountLoginInProgress(true);
        final String loginUrl = pendingLoginUrl;
        final String loginUser = pendingLoginUser;
        final String loginPass = pendingLoginPass;
        wsClient.submitTotp(code, cbTrustDevice.isChecked(), new FnWebSocketClient.LoginCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    setAccountLoginInProgress(false);
                    saveAccountSession(loginUrl, loginUser, loginPass, response);
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    setAccountLoginInProgress(false);
                    editTotp.setText("");
                    editTotp.requestFocus();
                    if (msg != null && (msg.contains("过期") || msg.contains("失效"))) {
                        clearTwoFactorChallenge();
                    }
                    Toast.makeText(LoginActivity.this, "验证失败: " + msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showTwoFactorPrompt(FnWebSocketClient.TwoFactorChallenge challenge) {
        pendingTwoFactorChallenge = challenge;
        editPass.setVisibility(View.GONE);
        accountOptionsRow.setVisibility(View.GONE);
        tvTotpHint.setVisibility(View.VISIBLE);
        editTotp.setVisibility(View.VISIBLE);
        cbTrustDevice.setVisibility(View.VISIBLE);
        editTotp.setText("");
        cbTrustDevice.setChecked(false);
        setAccountLoginInProgress(false);
        editTotp.requestFocus();
        Toast.makeText(this, "请输入动态验证码", Toast.LENGTH_SHORT).show();
    }

    private void clearTwoFactorChallenge() {
        pendingTwoFactorChallenge = null;
        pendingLoginUrl = null;
        pendingLoginUser = null;
        pendingLoginPass = null;
        if (editPass != null) editPass.setVisibility(View.VISIBLE);
        if (accountOptionsRow != null) accountOptionsRow.setVisibility(View.VISIBLE);
        if (tvTotpHint != null) tvTotpHint.setVisibility(View.GONE);
        if (editTotp != null) {
            editTotp.setText("");
            editTotp.setVisibility(View.GONE);
        }
        if (cbTrustDevice != null) {
            cbTrustDevice.setChecked(false);
            cbTrustDevice.setVisibility(View.GONE);
        }
        if (btnLogin != null && btnLogin.isEnabled()) {
            btnLogin.setText("登录");
        }
    }

    private void setAccountLoginInProgress(boolean inProgress) {
        btnLogin.setEnabled(!inProgress);
        if (inProgress) {
            btnLogin.setText(pendingTwoFactorChallenge != null ? "验证中..." : "登录中...");
        } else {
            btnLogin.setText(pendingTwoFactorChallenge != null ? "验证并登录" : "登录");
        }
    }

    private void saveQrSession(LoginCodeParser.StatusResult status) {
        if (selectedServer == null) return;
        new UserProfileStore(this).saveQrSession(
                selectedServer.baseUrl,
                status.token,
                status.secret,
                status.backId
        );
        persistSavedServer(selectedServer.baseUrl);
        startMain();
    }

    private void saveAccountSession(String url, String user, String pass, JSONObject response) {
        try {
            String saveUrl = normalizeHttpUrl(url);
            new UserProfileStore(this).saveAccountSession(
                    saveUrl,
                    user,
                    pass,
                    response.getString("token"),
                    response.getString("secret"),
                    response.getString("backId"),
                    cbRemember.isChecked()
            );
            persistSavedServer(saveUrl);
            startMain();
        } catch (Exception e) {
            Toast.makeText(this, "保存会话失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void addServer(LanServerDiscovery.ServerCandidate server) {
        if (server == null || server.baseUrl == null || server.baseUrl.isEmpty()) return;
        if (knownServerUrls.add(server.baseUrl)) {
            servers.add(server);
        }
    }

    private void persistSavedServer(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String encoded = SavedServerHistory.addOrPromote(
                prefs.getString(SavedServerHistory.PREF_KEY, ""),
                baseUrl
        );
        prefs.edit().putString(SavedServerHistory.PREF_KEY, encoded).apply();
    }

    private void startMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private boolean isEnterDown(KeyEvent event) {
        return event != null
                && event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER);
    }

    private String normalizeHttpUrl(String url) {
        String saveUrl = url == null ? "" : url;
        if (saveUrl.startsWith("ws://")) saveUrl = saveUrl.replace("ws://", "http://");
        if (saveUrl.startsWith("wss://")) saveUrl = saveUrl.replace("wss://", "https://");
        if (!saveUrl.startsWith("http://") && !saveUrl.startsWith("https://")) {
            saveUrl = "http://" + saveUrl;
        }
        return saveUrl;
    }

    private String hostLabel(String baseUrl) {
        String raw = baseUrl == null ? "" : baseUrl;
        raw = raw.replace("http://", "").replace("https://", "");
        int slash = raw.indexOf('/');
        if (slash >= 0) raw = raw.substring(0, slash);
        int colon = raw.lastIndexOf(':');
        if (colon > 0 && !raw.startsWith("[")) raw = raw.substring(0, colon);
        return raw;
    }

    private int portOf(String baseUrl) {
        String raw = baseUrl == null ? "" : baseUrl;
        int colon = raw.lastIndexOf(':');
        if (colon < 0) return LanServerDiscovery.DEFAULT_TV_PORT;
        try {
            return Integer.parseInt(raw.substring(colon + 1).replaceAll("[^0-9].*$", ""));
        } catch (Exception ignored) {
            return LanServerDiscovery.DEFAULT_TV_PORT;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return "";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
