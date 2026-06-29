package com.giso.demo.video.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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

/** 播放页横向选集条。 */
public final class EpisodeChipAdapter extends RecyclerView.Adapter<EpisodeChipAdapter.Holder> {
    public interface Listener {
        void onSelect(VideoEpisode episode);
    }

    private final List<VideoEpisode> episodes;
    private final String currentVid;
    private final Listener listener;

    public EpisodeChipAdapter(List<VideoEpisode> episodes, String currentVid, Listener listener) {
        this.episodes = episodes;
        this.currentVid = currentVid;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode_chip, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        VideoEpisode ep = episodes.get(position);
        h.label.setText("第" + ep.epNum + "集");
        boolean selected = ep.vid.equals(currentVid);
        int bg = selected ? R.color.primary : R.color.surface_elevated;
        int fg = selected ? R.color.text_primary : R.color.text_secondary;
        h.label.setBackgroundColor(ContextCompat.getColor(h.label.getContext(), bg));
        h.label.setTextColor(ContextCompat.getColor(h.label.getContext(), fg));

        Map<String, Object> params = new HashMap<>();
        params.put(Params.EP_NUM, ep.epNum);
        params.put(Params.VID, ep.vid);
        Tracker.get().bind(h.label, ElementMeta.of(Elements.EPISODE_ITEM, position, params));
        h.label.setOnClickListener(v -> listener.onSelect(ep));
    }

    @Override
    public int getItemCount() {
        return episodes.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView label;

        Holder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.episodeLabel);
        }
    }
}
