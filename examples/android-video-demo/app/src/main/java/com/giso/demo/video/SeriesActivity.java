package com.giso.demo.video;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giso.demo.video.model.DemoCatalog;
import com.giso.demo.video.model.VideoEpisode;
import com.giso.demo.video.ui.EpisodeRowAdapter;
import com.giso.tracker.Pages;
import com.giso.tracker.Params;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.HashMap;
import java.util.Map;

/** 剧集合集页：选集元素曝光/点击。 */
public final class SeriesActivity extends BaseTrackedActivity {
    private static final String EXTRA_SERIES = "series_id";

    private String seriesId;

    public static Intent intent(Context ctx, String seriesId) {
        return new Intent(ctx, SeriesActivity.class).putExtra(EXTRA_SERIES, seriesId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series);

        seriesId = getIntent().getStringExtra(EXTRA_SERIES);
        if (seriesId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(DemoCatalog.seriesTitle(seriesId));
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView grid = findViewById(R.id.episodeGrid);
        grid.setLayoutManager(new LinearLayoutManager(this));
        grid.setAdapter(new EpisodeRowAdapter(DemoCatalog.episodesOf(seriesId),
                ep -> startActivity(DetailActivity.intent(this, ep.vid))));
    }

    @Override
    protected String pageId() {
        return Pages.VIDEO_SERIES;
    }

    @Override
    protected Map<String, Object> pageParams() {
        Map<String, Object> p = new HashMap<>();
        p.put(Params.SERIES_ID, seriesId);
        return p;
    }
}
