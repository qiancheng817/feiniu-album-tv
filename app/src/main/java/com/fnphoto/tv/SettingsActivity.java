package com.fnphoto.tv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.fnphoto.tv.cache.CachedImageLoader;

public class SettingsActivity extends FragmentActivity {

    private LinearLayout contentLayout;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        prefs = getSharedPreferences("fn_photo_prefs", Context.MODE_PRIVATE);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setBackgroundColor(Color.parseColor("#10100E"));

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("设置");
        tvTitle.setTextColor(Color.parseColor("#EDE5D2"));
        tvTitle.setTextSize(30);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
        tvTitle.setPadding(48, 32, 48, 16);
        rootLayout.addView(tvTitle);

        // ScrollView for content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(48, 8, 48, 48);

        addSectionHeader("连接信息");
        addInfoItem("NAS地址", prefs.getString("nas_url", "未设置"));
        addInfoItem("登录状态", prefs.getString("api_token", "").isEmpty() ? "未登录" : "已登录");

        addSectionHeader("缓存管理");
        addCacheInfoItem();
        addActionItem("清空图片缓存", v -> {
            CachedImageLoader.clearAllCache(this);
            Toast.makeText(this, "缓存已清空", Toast.LENGTH_SHORT).show();
            refreshCacheSize();
        });

        addSectionHeader("关于");
        addInfoItem("应用名称", "飞牛相册");
        addInfoItem("版本", "1.0.0");
        addInfoItem("开发", "基于飞牛NAS相册系统");

        scrollView.addView(contentLayout);
        rootLayout.addView(scrollView);

        setContentView(rootLayout);
    }

    private void addSectionHeader(String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(Color.parseColor("#D7B56D"));
        header.setTextSize(20);
        header.setPadding(0, 32, 0, 12);
        contentLayout.addView(header);
    }

    private void addInfoItem(String label, String value) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, 12, 0, 12);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#8A8276"));
        tvLabel.setTextSize(18);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(300, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(Color.parseColor("#EDE5D2"));
        tvValue.setTextSize(18);

        itemLayout.addView(tvLabel);
        itemLayout.addView(tvValue);
        contentLayout.addView(itemLayout);
    }

    private TextView tvCacheSize;

    private void addCacheInfoItem() {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, 12, 0, 12);

        TextView tvLabel = new TextView(this);
        tvLabel.setText("缓存大小");
        tvLabel.setTextColor(Color.parseColor("#8A8276"));
        tvLabel.setTextSize(18);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(300, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvCacheSize = new TextView(this);
        tvCacheSize.setTextColor(Color.parseColor("#EDE5D2"));
        tvCacheSize.setTextSize(18);

        itemLayout.addView(tvLabel);
        itemLayout.addView(tvCacheSize);
        contentLayout.addView(itemLayout);

        refreshCacheSize();
    }

    private void refreshCacheSize() {
        if (tvCacheSize != null) {
            float sizeMB = CachedImageLoader.getCacheSizeMB(this);
            if (sizeMB > 0.1f) {
                tvCacheSize.setText(String.format("%.1f MB", sizeMB));
            } else {
                tvCacheSize.setText("无缓存");
            }
        }
    }

    private void addActionItem(String title, View.OnClickListener listener) {
        TextView action = new TextView(this);
        action.setText(title);
        action.setTextColor(Color.parseColor("#ef4444"));
        action.setTextSize(18);
        action.setPadding(300, 16, 0, 16);
        action.setFocusable(true);
        action.setFocusableInTouchMode(true);
        action.setOnClickListener(listener);
        contentLayout.addView(action);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
