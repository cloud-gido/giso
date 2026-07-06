package com.giso.gateway.debug;

import com.giso.gateway.space.SpaceService;

final class DebugBufferKeys {
    private final String prefix;

    DebugBufferKeys(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "giso:debug" : prefix.replaceAll("/+$", "");
    }

    String space(String spaceKey) {
        return spaceKey == null || spaceKey.isBlank() ? SpaceService.DEFAULT_SPACE : spaceKey;
    }

    String recentList(String spaceKey) {
        return prefix + ":" + space(spaceKey) + ":recent";
    }

    String pubChannel(String spaceKey) {
        return prefix + ":" + space(spaceKey) + ":pub";
    }

    String recentPattern() {
        return prefix + ":*:recent";
    }

    String pubPattern() {
        return prefix + ":*:pub";
    }

    static String spaceFromPubChannel(String channel, String prefix) {
        String p = prefix + ":";
        if (!channel.startsWith(p) || !channel.endsWith(":pub")) return SpaceService.DEFAULT_SPACE;
        String mid = channel.substring(p.length(), channel.length() - 4);
        return mid.isEmpty() ? SpaceService.DEFAULT_SPACE : mid;
    }
}
