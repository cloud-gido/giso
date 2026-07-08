package com.giso.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenshotStoreTest {

    @TempDir
    Path tmp;

    private ScreenshotStore localStore() throws Exception {
        GatewayConfig c = new GatewayConfig();
        c.screenshotsBackend = "local";
        c.screenshotsDir = tmp.toString();
        return ScreenshotStore.create(c);
    }

    @Test
    void saveAndLoad() throws Exception {
        ScreenshotStore store = localStore();
        byte[] png = new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47 };
        String url = store.save("default", "home.png", png);
        assertTrue(url.startsWith("/admin/screenshots/default/"));
        assertTrue(url.endsWith(".png"));
        ScreenshotStore.Loaded loaded = store.loadRelative("default/" + url.substring(url.lastIndexOf('/') + 1));
        assertNotNull(loaded);
        assertEquals("image/png", loaded.contentType());
        assertEquals(png.length, loaded.bytes().length);
    }

    @Test
    void rejectsOversize() throws Exception {
        ScreenshotStore store = localStore();
        byte[] big = new byte[ScreenshotStore.MAX_BYTES + 1];
        assertThrows(IllegalArgumentException.class, () -> store.save("default", "x.png", big));
    }
}
