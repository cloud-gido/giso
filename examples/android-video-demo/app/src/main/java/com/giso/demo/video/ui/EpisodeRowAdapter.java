package com.giso.demo.video.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.giso.demo.video.R;
import com.giso.demo.video.model.VideoEpisode;
import com.giso.tracker.ElementMeta;
import com.giso.tracker.Elements;
import com.giso.tracker.Params;
import com.giso.tracker.Tracker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 剧集页纵向选集列表。 */
public final class EpisodeRowAdapter extends RecyclerView.Adapter<EpisodeRowAdapter.Holder> {
    public interface Listener {
        void onPlay(VideoEpisode episode);
    }

    private final List<VideoEpisode> episodes;
    private final Listener listener;

    public EpisodeRowAdapter(List<VideoEpisode> episodes, Listener listener) {
        this.episodes = episodes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode_row, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        VideoEpisode ep = episodes.get(position);
        h.epNum.setText(String.valueOf(ep.epNum));
        h.epTitle.setText(ep.title);
        h.epDuration.setText(ep.durationLabel());

        Map<String, Object> params = new HashMap<>();
        params.put(Params.EP_NUM, ep.epNum);
        params.put(Params.VID, ep.vid);

        Tracker t = Tracker.get();
        t.bind(h.card, ElementMeta.of(Elements.EPISODE_ITEM, position, params));
        t.bind(h.playBtn, ElementMeta.of(Elements.PLAY_BTN));

        View.OnClickListener play = v -> listener.onPlay(ep);
        h.card.setOnClickListener(play);
        h.playBtn.setOnClickListener(play);
    }

    @Override
    public int getItemCount() {
        return episodes.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final TextView epNum;
        final TextView epTitle;
        final TextView epDuration;
        final ImageButton playBtn;

        Holder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.episodeCard);
            epNum = itemView.findViewById(R.id.epNum);
            epTitle = itemView.findViewById(R.id.epTitle);
            epDuration = itemView.findViewById(R.id.epDuration);
            playBtn = itemView.findViewById(R.id.playBtn);
        }
    }
}
