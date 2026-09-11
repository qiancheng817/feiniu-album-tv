package com.fnphoto.tv.ui;

import java.util.Calendar;
import java.util.Locale;

public final class TvUiText {
    private TvUiText() {
    }

    public static String mediaCountText(int photoCount, int videoCount) {
        StringBuilder builder = new StringBuilder();
        if (photoCount > 0) {
            builder.append(photoCount).append("张照片");
        }
        if (videoCount > 0) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(videoCount).append("个视频");
        }
        return builder.toString();
    }

    public static String emptyMessage(String surface) {
        if ("favorites".equals(surface)) {
            return "还没有收藏内容";
        }
        if ("folder".equals(surface)) {
            return "这个文件夹里还没有照片或视频";
        }
        if ("people".equals(surface)) {
            return "还没有识别到人物";
        }
        if ("places".equals(surface)) {
            return "还没有地点信息";
        }
        return "这里还没有照片或视频";
    }

    public static String errorMessage(String surface) {
        if ("photos".equals(surface)) {
            return "照片列表加载失败";
        }
        if ("video".equals(surface)) {
            return "视频播放失败";
        }
        return "内容加载失败";
    }

    public static String detailActionHint() {
        return "左右切换 · OK 暂停提示 · 菜单查看详情";
    }

    public static String homeRecentPhotosTitle() {
        return "最近照片";
    }

    public static String mediaHeroTitle(String type) {
        if ("photo".equals(type)) {
            return "照片";
        }
        if ("video".equals(type)) {
            return "视频";
        }
        if ("album".equals(type)) {
            return "相册";
        }
        return "";
    }

    public static String photoDateTitle(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        calendar.set(year, month - 1, day);
        int currentYear = Calendar.getInstance(Locale.CHINA).get(Calendar.YEAR);
        String[] weekdays = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        String weekday = weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1];
        String dayText = month + "月" + day + "日 " + weekday;
        if (year == currentYear) {
            return dayText;
        }
        return year + "年" + dayText;
    }

    public static String cardOverlayTitle(String type, String title) {
        if ("photo".equals(type) || "video".equals(type)) {
            return "";
        }
        return title != null ? title : "";
    }
}
