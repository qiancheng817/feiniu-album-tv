package com.fnphoto.tv.login;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SavedServerHistory {
    public static final String PREF_KEY = "saved_servers";
    private static final int MAX_SERVERS = 12;

    private SavedServerHistory() {
    }

    public static List<String> decode(String encoded) {
        Set<String> urls = new LinkedHashSet<>();
        String raw = encoded == null ? "" : encoded;
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            String url = line == null ? "" : line.trim();
            if (!url.isEmpty()) {
                urls.add(url);
            }
        }
        return new ArrayList<>(urls);
    }

    public static String encode(List<String> urls) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        if (urls != null) {
            for (String value : urls) {
                String url = value == null ? "" : value.trim();
                if (url.isEmpty()) continue;
                if (count > 0) builder.append('\n');
                builder.append(url);
                count++;
                if (count >= MAX_SERVERS) break;
            }
        }
        return builder.toString();
    }

    public static String addOrPromote(String encoded, String serverUrl) {
        String normalized = serverUrl == null ? "" : serverUrl.trim();
        if (normalized.isEmpty()) {
            return encode(decode(encoded));
        }

        List<String> urls = new ArrayList<>();
        urls.add(normalized);
        for (String existing : decode(encoded)) {
            if (!normalized.equals(existing)) {
                urls.add(existing);
            }
        }
        return encode(urls);
    }
}
