package com.giso.demo.video;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giso.demo.video.model.DemoCatalog;
import com.giso.demo.video.model.VideoEpisode;
import com.giso.demo.video.tracking.PlaybackTracker;
import com.giso.demo.video.ui.EpisodeChipAdapter;
import com.giso.tracker.ElementMeta;
import com.giso.tracker.Elements;
import com.giso.tracker.Pages;
import com.giso.tracker.Params;
import com.giso.tracker.Tracker;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 长视频播放页：元素埋点 + 播放业务事件。 */
public final class DetailActivity extends BaseTrackedActivity {
    private static final String EXTRA_VID = "vid";

    private VideoEpisode episode;
    private ExoPlayer player;
    private PlaybackTracker playbackTracker;
    private boolean playStartReported;

    public static Intent intent(Context ctx, String vid) {
        return new Intent(ctx, DetailActivity.class).putExtra(EXTRA_VID, vid);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        String vid = getIntent().getStringExtra(EXTRA_VID);
        episode = DemoCatalog.findByVid(vid);
        if (episode == null) {
            finish();
            return;
        }

        playbackTracker = new PlaybackTracker();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(episode.title);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView title = findViewById(R.id.videoTitle);
        TextView meta = findViewById(R.id.videoMeta);
        title.setText(episode.title);
        meta.setText(episode.cpName + " · " + episode.definition + " · " + episode.durationLabel());

        bindActionElements();
        setupEpisodes();
        setupPlayer();
    }

    @Override
    protected String pageId() {
        return Pages.VIDEO_DETAIL;
    }

    @Override
    protected Map<String, Object> pageParams() {
        Map<String, Object> p = new HashMap<>();
        p.put(Params.VID, episode.vid);
        if (episode.seriesId != null && !episode.seriesId.isEmpty()) {
            p.put(Params.SERIES_ID, episode.seriesId);
        }
        if (episode.epNum > 0) {
            p.put(Params.EP_NUM, episode.epNum);
        }
        return p;
    }

    private void bindActionElements() {
        Tracker t = Tracker.get();
        ImageButton like = findViewById(R.id.likeBtn);
        ImageButton share = findViewById(R.id.shareBtn);
        MaterialButton fullscreen = findViewById(R.id.fullscreenBtn);
        MaterialButton viewSeries = findViewById(R.id.viewSeriesBtn);

        t.bind(like, ElementMeta.of(Elements.LIKE_BTN));
        t.bind(share, ElementMeta.of(Elements.SHARE_BTN));
        t.bind(fullscreen, ElementMeta.of(Elements.FULLSCREEN_BTN));

        like.setOnClickListener(v -> Toast.makeText(this, "点赞", Toast.LENGTH_SHORT).show());
        share.setOnClickListener(v -> Toast.makeText(this, "分享", Toast.LENGTH_SHORT).show());
        fullscreen.setOnClickListener(v -> toggleFullscreen());
        viewSeries.setOnClickListener(v -> {
            if (episode.seriesId != null && !episode.seriesId.isEmpty()) {
                startActivity(SeriesActivity.intent(this, episode.seriesId));
            }
        });
        viewSeries.setVisibility(
                episode.seriesId != null && !episode.seriesId.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupEpisodes() {
        RecyclerView list = findViewById(R.id.episodeList);
        if (episode.seriesId == null || episode.seriesId.isEmpty()) {
            list.setVisibility(View.GONE);
            return;
        }
        list.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        List<VideoEpisode> eps = DemoCatalog.episodesOf(episode.seriesId);
        list.setAdapter(new EpisodeChipAdapter(eps, episode.vid, selected -> {
            if (!selected.vid.equals(episode.vid)) {
                if (player != null) {
                    playbackTracker.onStopped(player.getCurrentPosition());
                }
                startActivity(intent(this, selected.vid));
                finish();
            }
        }));
    }

    private void setupPlayer() {
        PlayerView playerView = findViewById(R.id.playerView);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(episode.streamUrl));
        player.setPlayWhenReady(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && !playStartReported) {
                    playStartReported = true;
                    playbackTracker.onEpisodeReady(episode, true);
                }
                if (state == Player.STATE_ENDED && player != null) {
                    playbackTracker.onEnded(player.getCurrentPosition());
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (player == null) return;
                if (isPlaying) {
                    playbackTracker.onPlaying(player.getCurrentPosition());
                } else if (player.getPlaybackState() != Player.STATE_ENDED) {
                    playbackTracker.onPaused(player.getCurrentPosition());
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                playbackTracker.onError(error.getErrorCodeName());
                Toast.makeText(DetailActivity.this, "播放失败: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        player.prepare();
    }

    private void toggleFullscreen() {
        int ori = getRequestedOrientation();
        if (ori == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    @Override
    protected void onStop() {
        if (player != null && player.isPlaying()) {
            playbackTracker.onPaused(player.getCurrentPosition());
            player.pause();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            if (player.getPlaybackState() != Player.STATE_ENDED) {
                playbackTracker.onStopped(player.getCurrentPosition());
            }
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
