package com.giso.demo.video;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.giso.demo.video.tracking.TrackerHelper;
import com.giso.tracker.Tracker;

import java.util.Collections;
import java.util.Map;

/** 页面进出埋点基类：onResume enterPage，onPause exitPage。 */
public abstract class BaseTrackedActivity extends AppCompatActivity {
    protected abstract String pageId();

    protected Map<String, Object> pageParams() {
        return Collections.emptyMap();
    }

    @Nullable
    protected Map<String, Object> pagePt() {
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Tracker.get().enterPage(pageId(), pageParams(), pagePt());
        View panel = findViewById(R.id.debugPanel);
        if (panel != null) {
            TrackerHelper.bindDebugPanel(this, panel, pageId());
        }
    }

    @Override
    protected void onPause() {
        Tracker.get().exitPage();
        super.onPause();
    }
}
