package com.giso.demo.video;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giso.demo.video.model.DemoCatalog;
import com.giso.demo.video.model.VideoEpisode;
import com.giso.demo.video.ui.VideoFeedAdapter;
import com.giso.tracker.Pages;
import com.giso.tracker.Params;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 视频推荐流：page_enter/exit + 卡片元素曝光点击。 */
public final class FeedActivity extends BaseTrackedActivity {
    private static final String TAB_RECOMMEND = "recommend";
    private static final String TAB_SERIES = "series";

    private String currentTab = TAB_RECOMMEND;
    private String recTraceId = "rec-" + UUID.randomUUID();

    private RecyclerView recycler;
    private VideoFeedAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);

        recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        TabLayout tabs = findViewById(R.id.tabLayout);
        tabs.addTab(tabs.newTab().setText(R.string.tab_recommend));
        tabs.addTab(tabs.newTab().setText(R.string.tab_series));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition() == 0 ? TAB_RECOMMEND : TAB_SERIES;
                recTraceId = "rec-" + UUID.randomUUID();
                reloadFeed();
                // 切 tab 触发 page 重进（演示 page_enter 带 tab_name）
                onPause();
                onResume();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_series) {
                List<VideoEpisode> series = DemoCatalog.seriesList();
                if (!series.isEmpty() && series.get(0).seriesId != null) {
                    startActivity(SeriesActivity.intent(this, series.get(0).seriesId));
                }
                return true;
            }
            if (id == R.id.nav_mine) {
                Toast.makeText(this, "演示版仅展示长视频埋点链路", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        reloadFeed();
    }

    @Override
    protected String pageId() {
        return Pages.VIDEO_FEED;
    }

    @Override
    protected Map<String, Object> pageParams() {
        Map<String, Object> p = new HashMap<>();
        p.put(Params.TAB_NAME, currentTab);
        return p;
    }

    @Override
    protected Map<String, Object> pagePt() {
        Map<String, Object> pt = new HashMap<>();
        pt.put(Params.REC_TRACE_ID, recTraceId);
        return pt;
    }

    private void reloadFeed() {
        List<VideoEpisode> data = TAB_SERIES.equals(currentTab)
                ? DemoCatalog.seriesList()
                : DemoCatalog.feedItems();
        adapter = new VideoFeedAdapter(data, recTraceId, ep ->
                startActivity(DetailActivity.intent(this, ep.vid)));
        recycler.setAdapter(adapter);
    }
}
