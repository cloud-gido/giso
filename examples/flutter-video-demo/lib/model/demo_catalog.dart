import 'video_episode.dart';

/// Demo catalog mirroring the Android long-video sample (Google public CDN).
class DemoCatalog {
  DemoCatalog._();

  static const _sample =
      'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/';

  static final Map<String, List<VideoEpisode>> _series = {
    'series_journey': [
      _ep(
        vid: 'vid_journey_01',
        seriesId: 'series_journey',
        epNum: 1,
        title: '漫长旅程 · 第1集 启程',
        cpId: 'cp_studio_a',
        cpName: '光影工作室',
        url: '${_sample}BigBuckBunny.mp4',
        dur: 9 * 60 * 1000,
        def: '1080p',
      ),
      _ep(
        vid: 'vid_journey_02',
        seriesId: 'series_journey',
        epNum: 2,
        title: '漫长旅程 · 第2集 迷雾',
        cpId: 'cp_studio_a',
        cpName: '光影工作室',
        url: '${_sample}ElephantsDream.mp4',
        dur: 10 * 60 * 1000,
        def: '1080p',
      ),
      _ep(
        vid: 'vid_journey_03',
        seriesId: 'series_journey',
        epNum: 3,
        title: '漫长旅程 · 第3集 归途',
        cpId: 'cp_studio_a',
        cpName: '光影工作室',
        url: '${_sample}ForBiggerBlazes.mp4',
        dur: 15 * 60 * 1000,
        def: '720p',
      ),
      _ep(
        vid: 'vid_journey_04',
        seriesId: 'series_journey',
        epNum: 4,
        title: '漫长旅程 · 第4集 终章',
        cpId: 'cp_studio_a',
        cpName: '光影工作室',
        url: '${_sample}Sintel.mp4',
        dur: 14 * 60 * 1000,
        def: '1080p',
      ),
    ],
    'series_city': [
      _ep(
        vid: 'vid_city_01',
        seriesId: 'series_city',
        epNum: 1,
        title: '都市物语 · 第1集',
        cpId: 'cp_creator_b',
        cpName: '南城影业',
        url: '${_sample}TearsOfSteel.mp4',
        dur: 12 * 60 * 1000,
        def: '1080p',
      ),
      _ep(
        vid: 'vid_city_02',
        seriesId: 'series_city',
        epNum: 2,
        title: '都市物语 · 第2集',
        cpId: 'cp_creator_b',
        cpName: '南城影业',
        url: '${_sample}ForBiggerEscapes.mp4',
        dur: 15 * 60 * 1000,
        def: '720p',
      ),
      _ep(
        vid: 'vid_city_03',
        seriesId: 'series_city',
        epNum: 3,
        title: '都市物语 · 第3集',
        cpId: 'cp_creator_b',
        cpName: '南城影业',
        url: '${_sample}ForBiggerFun.mp4',
        dur: 60 * 60 * 1000,
        def: '1080p',
      ),
    ],
  };

  static final List<VideoEpisode> _feed = [
    _series['series_journey']!.first,
    _series['series_city']!.first,
    _ep(
      vid: 'vid_doc_ocean',
      seriesId: '',
      epNum: 0,
      title: '纪录片 · 深海探秘 完整版',
      cpId: 'cp_doc_lab',
      cpName: '纪实频道',
      url: '${_sample}SubaruOutbackOnStreetAndDirt.mp4',
      dur: 45 * 60 * 1000,
      def: '1080p',
    ),
    _ep(
      vid: 'vid_movie_night',
      seriesId: '',
      epNum: 0,
      title: '电影 · 午夜列车 导演剪辑版',
      cpId: 'cp_film_c',
      cpName: '独立电影社',
      url: '${_sample}VolkswagenGTIReview.mp4',
      dur: 35 * 60 * 1000,
      def: '720p',
    ),
    _series['series_journey']![1],
    _series['series_city']![2],
  ];

  static VideoEpisode _ep({
    required String vid,
    required String seriesId,
    required int epNum,
    required String title,
    required String cpId,
    required String cpName,
    required String url,
    required int dur,
    required String def,
  }) {
    return VideoEpisode(
      vid: vid,
      seriesId: seriesId,
      epNum: epNum,
      title: title,
      cpId: cpId,
      cpName: cpName,
      streamUrl: url,
      durationMs: dur,
      definition: def,
    );
  }

  static List<VideoEpisode> feedItems() => List.unmodifiable(_feed);

  static List<VideoEpisode> seriesList() => List.unmodifiable([
        _series['series_journey']!.first,
        _series['series_city']!.first,
      ]);

  static List<VideoEpisode> episodesOf(String seriesId) =>
      List.unmodifiable(_series[seriesId] ?? const []);

  static VideoEpisode? findByVid(String vid) {
    for (final e in _feed) {
      if (e.vid == vid) return e;
    }
    for (final list in _series.values) {
      for (final e in list) {
        if (e.vid == vid) return e;
      }
    }
    return null;
  }

  static String seriesTitle(String seriesId) {
    return switch (seriesId) {
      'series_journey' => '漫长旅程',
      'series_city' => '都市物语',
      _ => seriesId,
    };
  }
}
