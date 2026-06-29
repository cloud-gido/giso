package com.giso.demo.video.model;

/** 单集视频元数据。 */
public final class VideoEpisode {
    public final String vid;
    public final String seriesId;
    public final int epNum;
    public final String title;
    public final String cpId;
    public final String cpName;
    public final String streamUrl;
    public final long durationMs;
    public final String definition;

    public VideoEpisode(String vid, String seriesId, int epNum, String title,
                        String cpId, String cpName, String streamUrl,
                        long durationMs, String definition) {
        this.vid = vid;
        this.seriesId = seriesId;
        this.epNum = epNum;
        this.title = title;
        this.cpId = cpId;
        this.cpName = cpName;
        this.streamUrl = streamUrl;
        this.durationMs = durationMs;
        this.definition = definition;
    }

    public String durationLabel() {
        long sec = durationMs / 1000;
        long min = sec / 60;
        long rem = sec % 60;
        if (min >= 60) {
            long h = min / 60;
            min = min % 60;
            return h + ":" + pad(min) + ":" + pad(rem);
        }
        return min + ":" + pad(rem);
    }

    private static String pad(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
