class VideoEpisode {
  const VideoEpisode({
    required this.vid,
    required this.seriesId,
    required this.epNum,
    required this.title,
    required this.cpId,
    required this.cpName,
    required this.streamUrl,
    required this.durationMs,
    required this.definition,
  });

  final String vid;
  final String seriesId;
  final int epNum;
  final String title;
  final String cpId;
  final String cpName;
  final String streamUrl;
  final int durationMs;
  final String definition;

  bool get isSeries => seriesId.isNotEmpty && epNum > 0;

  String durationLabel() {
    final totalSec = durationMs ~/ 1000;
    final min = totalSec ~/ 60;
    final sec = totalSec % 60;
    return '$min分${sec.toString().padLeft(2, '0')}秒';
  }
}
