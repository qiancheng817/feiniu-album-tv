package com.fnphoto.tv.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TvNavigation {
    private TvNavigation() {
    }

    public static List<TvNavItem> primaryItems() {
        List<TvNavItem> items = new ArrayList<>();
        items.add(new TvNavItem("图库", "gallery", true));
        items.add(new TvNavItem("最近", "recent", true));
        items.add(new TvNavItem("收藏", "favorites", true));
        items.add(new TvNavItem("相册", "albums", true));
        items.add(new TvNavItem("文件夹", "folders", true));
        items.add(new TvNavItem("人物", "people", true));
        items.add(new TvNavItem("地点", "places", true));
        items.add(new TvNavItem("搜索", "search", true));
        items.add(new TvNavItem("设置", "settings", true));
        return Collections.unmodifiableList(items);
    }

    public static List<TvNavItem> homeStreamTopItems() {
        List<TvNavItem> items = new ArrayList<>();
        items.add(new TvNavItem("图库", "gallery", true));
        items.add(new TvNavItem("相册", "albums", true));
        items.add(new TvNavItem("文件夹", "folders", true));
        items.add(new TvNavItem("收藏", "favorites", true));
        items.add(new TvNavItem("最近添加", "recent", true));
        items.add(new TvNavItem("共享", "shared", true));
        items.add(new TvNavItem("人物", "people", true));
        items.add(new TvNavItem("地点", "places", true));
        items.add(new TvNavItem("智能分类", "smart", true));
        items.add(new TvNavItem("标签", "tags", true));
        items.add(new TvNavItem("媒体类型", "media_types", true));
        return Collections.unmodifiableList(items);
    }
}
