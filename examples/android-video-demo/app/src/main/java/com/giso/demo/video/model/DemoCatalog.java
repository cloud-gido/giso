package com.giso.demo.video.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 演示用长视频目录：含单片和剧集，使用 Google 公开样片流。 */
public final class DemoCatalog {
    private static final String SAMPLE = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/";

    private static final Map<String, List<VideoEpisode>> SERIES = new LinkedHashMap<>();
    private static final List<VideoEpisode> FEED = new ArrayList<>();

    static {
        List<VideoEpisode> journey = List.of(
                ep("vid_journey_01", "series_journey", 1, "漫长旅程 · 第1集 启程",
                        "cp_studio_a", "光影工作室", SAMPLE + "BigBuckBunny.mp4", 9 * 60_000L, "1080p"),
                ep("vid_journey_02", "series_journey", 2, "漫长旅程 · 第2集 迷雾",
                        "cp_studio_a", "光影工作室", SAMPLE + "ElephantsDream.mp4", 10 * 60_000L, "1080p"),
                ep("vid_journey_03", "series_journey", 3, "漫长旅程 · 第3集 归途",
                        "cp_studio_a", "光影工作室", SAMPLE + "ForBiggerBlazes.mp4", 15 * 60_000L, "720p"),
                ep("vid_journey_04", "series_journey", 4, "漫长旅程 · 第4集 终章",
                        "cp_studio_a", "光影工作室", SAMPLE + "Sintel.mp4", 14 * 60_000L, "1080p")
        );
        SERIES.put("series_journey", journey);

        List<VideoEpisode> city = List.of(
                ep("vid_city_01", "series_city", 1, "都市物语 · 第1集",
                        "cp_creator_b", "南城影业", SAMPLE + "TearsOfSteel.mp4", 12 * 60_000L, "1080p"),
                ep("vid_city_02", "series_city", 2, "都市物语 · 第2集",
                        "cp_creator_b", "南城影业", SAMPLE + "ForBiggerEscapes.mp4", 15 * 60_000L, "720p"),
                ep("vid_city_03", "series_city", 3, "都市物语 · 第3集",
                        "cp_creator_b", "南城影业", SAMPLE + "ForBiggerFun.mp4", 60 * 60_000L, "1080p")
        );
        SERIES.put("series_city", city);

        FEED.add(journey.get(0));
        FEED.add(city.get(0));
        FEED.add(ep("vid_doc_ocean", "", 0, "纪录片 · 深海探秘 完整版",
                "cp_doc_lab", "纪实频道", SAMPLE + "SubaruOutbackOnStreetAndDirt.mp4", 45 * 60_000L, "1080p"));
        FEED.add(ep("vid_movie_night", "", 0, "电影 · 午夜列车 导演剪辑版",
                "cp_film_c", "独立电影社", SAMPLE + "VolkswagenGTIReview.mp4", 35 * 60_000L, "720p"));
        FEED.add(journey.get(1));
        FEED.add(city.get(2));
    }

    private DemoCatalog() { }

    private static VideoEpisode ep(String vid, String seriesId, int epNum, String title,
                                   String cpId, String cpName, String url,
                                   long dur, String def) {
        return new VideoEpisode(vid, seriesId, epNum, title, cpId, cpName, url, dur, def);
    }

    public static List<VideoEpisode> feedItems() {
        return Collections.unmodifiableList(FEED);
    }

    public static List<VideoEpisode> seriesList() {
        List<VideoEpisode> out = new ArrayList<>();
        out.add(SERIES.get("series_journey").get(0));
        out.add(SERIES.get("series_city").get(0));
        return Collections.unmodifiableList(out);
    }

    public static List<VideoEpisode> episodesOf(String seriesId) {
        List<VideoEpisode> list = SERIES.get(seriesId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public static VideoEpisode findByVid(String vid) {
        for (VideoEpisode e : FEED) {
            if (e.vid.equals(vid)) return e;
        }
        for (List<VideoEpisode> list : SERIES.values()) {
            for (VideoEpisode e : list) {
                if (e.vid.equals(vid)) return e;
            }
        }
        return null;
    }

    public static String seriesTitle(String seriesId) {
        if ("series_journey".equals(seriesId)) return "漫长旅程";
        if ("series_city".equals(seriesId)) return "都市物语";
        return seriesId;
    }
}
