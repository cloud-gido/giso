package com.giso.demo.video.ui;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.giso.demo.video.R;
import com.giso.demo.video.SeriesActivity;
import com.giso.demo.video.model.VideoEpisode;
import com.giso.tracker.ElementMeta;
import com.giso.tracker.Elements;
import com.giso.tracker.Params;
import com.giso.tracker.Tracker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 推荐流视频卡片：bind 元素声明，曝光/点击由 SDK 自动上报。 */
public final class VideoFeedAdapter extends RecyclerView.Adapter<VideoFeedAdapter.Holder> {
    public interface Listener {
        void onOpenEpisode(VideoEpisode episode);
    }

    private final List<VideoEpisode> items;
    private final String recTraceId;
    private final Listener listener;

    public VideoFeedAdapter(List<VideoEpisode> items, String recTraceId, Listener listener) {
        this.items = items;
        this.recTraceId = recTraceId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_card, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        VideoEpisode ep = items.get(position);
        h.title.setText(ep.title);
        h.cpName.setText(ep.cpName);
        h.duration.setText(ep.durationLabel());

        Map<String, Object> cardParams = new HashMap<>();
        cardParams.put(Params.VID, ep.vid);
        cardParams.put(Params.POS, position);
        cardParams.put(Params.REC_TRACE_ID, recTraceId);

        Map<String, Object> pt = new HashMap<>();
        pt.put(Params.REC_TRACE_ID, recTraceId);

        Tracker t = Tracker.get();
        t.bind(h.cardRoot, ElementMeta.of(Elements.VIDEO_CARD, position, cardParams, pt));
        t.bind(h.playBtn, ElementMeta.of(Elements.PLAY_BTN));
        t.bind(h.likeBtn, ElementMeta.of(Elements.LIKE_BTN));
        Map<String, Object> cpParams = new HashMap<>();
        cpParams.put(Params.CP_ID, ep.cpId);
        t.bind(h.cpAvatar, ElementMeta.of(Elements.CP_AVATAR, position, cpParams));
        t.bind(h.shareBtn, ElementMeta.of(Elements.SHARE_BTN));

        View.OnClickListener open = v -> listener.onOpenEpisode(ep);
        h.cardRoot.setOnClickListener(open);
        h.playBtn.setOnClickListener(open);

        h.likeBtn.setOnClickListener(v -> {
            v.setSelected(!v.isSelected());
            Toast.makeText(v.getContext(), v.isSelected() ? "已点赞" : "取消点赞", Toast.LENGTH_SHORT).show();
        });
        h.shareBtn.setOnClickListener(v ->
                Toast.makeText(v.getContext(), "分享演示（仅埋点，无真实分享）", Toast.LENGTH_SHORT).show());

        if (ep.seriesId != null && !ep.seriesId.isEmpty()) {
            h.cardRoot.setOnLongClickListener(v -> {
                v.getContext().startActivity(SeriesActivity.intent(v.getContext(), ep.seriesId));
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View cardRoot;
        final TextView title;
        final TextView cpName;
        final TextView duration;
        final View cpAvatar;
        final ImageButton playBtn;
        final ImageButton likeBtn;
        final ImageButton shareBtn;

        Holder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            title = itemView.findViewById(R.id.title);
            cpName = itemView.findViewById(R.id.cpName);
            duration = itemView.findViewById(R.id.duration);
            cpAvatar = itemView.findViewById(R.id.cpAvatar);
            playBtn = itemView.findViewById(R.id.playBtn);
            likeBtn = itemView.findViewById(R.id.likeBtn);
            shareBtn = itemView.findViewById(R.id.shareBtn);
        }
    }
}
