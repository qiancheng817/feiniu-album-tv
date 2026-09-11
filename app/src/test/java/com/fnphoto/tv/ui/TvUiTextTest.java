package com.fnphoto.tv.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TvUiTextTest {
    @Test
    public void mediaCountText_joinsPhotoAndVideoCounts() {
        assertEquals("12张照片", TvUiText.mediaCountText(12, 0));
        assertEquals("3个视频", TvUiText.mediaCountText(0, 3));
        assertEquals("12张照片 · 3个视频", TvUiText.mediaCountText(12, 3));
        assertEquals("", TvUiText.mediaCountText(0, 0));
    }

    @Test
    public void emptyMessage_returnsKnownCopy() {
        assertEquals("还没有收藏内容", TvUiText.emptyMessage("favorites"));
        assertEquals("这个文件夹里还没有照片或视频", TvUiText.emptyMessage("folder"));
        assertEquals("照片列表加载失败", TvUiText.errorMessage("photos"));
    }

    @Test
    public void detailActionHint_usesRemoteControlLanguage() {
        assertEquals("左右切换 · OK 暂停提示 · 菜单查看详情", TvUiText.detailActionHint());
    }

    @Test
    public void homeRecentPhotosTitle_usesPhotoStreamLanguage() {
        assertEquals("最近照片", TvUiText.homeRecentPhotosTitle());
    }

    @Test
    public void mediaHeroTitle_hidesRawFileNames() {
        assertEquals("照片", TvUiText.mediaHeroTitle("photo"));
        assertEquals("视频", TvUiText.mediaHeroTitle("video"));
        assertEquals("相册", TvUiText.mediaHeroTitle("album"));
    }

    @Test
    public void photoDateTitle_formatsGalleryDay() {
        assertEquals("6月24日 星期三", TvUiText.photoDateTitle(2026, 6, 24));
    }

    @Test
    public void photoDateTitle_includesYearForPastYears() {
        assertEquals("2025年3月24日 星期一", TvUiText.photoDateTitle(2025, 3, 24));
    }

    @Test
    public void cardOverlayTitle_isHiddenForSingleMedia() {
        assertEquals("", TvUiText.cardOverlayTitle("photo", "IMG_001.jpg"));
        assertEquals("", TvUiText.cardOverlayTitle("video", "VID_001.mp4"));
        assertEquals("Camera", TvUiText.cardOverlayTitle("album", "Camera"));
        assertEquals("家人", TvUiText.cardOverlayTitle("person", "家人"));
    }
}
