package com.giso.gateway.screenshot;

import com.giso.gateway.ScreenshotStore;

import java.io.IOException;

/** 注册表预览图读写后端（本地磁盘或 S3）。 */
public interface ScreenshotBackend {
    String save(String spaceKey, String originalName, byte[] data) throws IOException;

    ScreenshotStore.Loaded loadRelative(String rel) throws IOException;

    String name();
}
