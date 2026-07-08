package com.giso.gateway;

import com.giso.gateway.screenshot.S3ScreenshotBackend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3ScreenshotBackendTest {

    @TempDir
    Path tmp;

    @Test
    void defaultObjectKeyUsesS3Prefix() {
        GatewayConfig c = new GatewayConfig();
        c.s3Prefix = "giso/";
        assertEquals("giso/screenshots/default/abc.png",
                S3ScreenshotBackend.objectKey(c, "default/abc.png"));
    }

    @Test
    void customScreenshotsPrefix() {
        GatewayConfig c = new GatewayConfig();
        c.screenshotsS3Prefix = "assets/registry/";
        assertEquals("assets/registry/default/abc.png",
                S3ScreenshotBackend.objectKey(c, "default/abc.png"));
    }

    @Test
    void autoBackendUsesLocalWithoutBucket() throws Exception {
        GatewayConfig c = new GatewayConfig();
        c.s3Bucket = "";
        assertTrue(ScreenshotStore.resolveBackend(c).name().startsWith("local("));
    }

    @Test
    void autoBackendUsesS3WhenBucketSet() throws Exception {
        GatewayConfig c = new GatewayConfig();
        c.s3Bucket = "test-bucket";
        c.screenshotsCacheDir = tmp.toString();
        var backend = ScreenshotStore.resolveBackend(c);
        assertTrue(backend.name().startsWith("s3(test-bucket/"));
    }
}
