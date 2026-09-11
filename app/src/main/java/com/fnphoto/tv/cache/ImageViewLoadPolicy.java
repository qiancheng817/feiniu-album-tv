package com.fnphoto.tv.cache;

final class ImageViewLoadPolicy {
    private ImageViewLoadPolicy() {
    }

    static String resolveToken(String providedToken, String storedToken) {
        if (providedToken != null && !providedToken.isEmpty()) {
            return providedToken;
        }
        return storedToken == null ? "" : storedToken;
    }

    static boolean shouldProbeLegacyCacheBeforeGlide() {
        return false;
    }
}
