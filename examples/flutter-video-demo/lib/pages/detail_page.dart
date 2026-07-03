import 'package:flutter/material.dart';
import 'package:giso_tracker/giso_tracker.dart';
import 'package:video_player/video_player.dart';

import '../model/demo_catalog.dart';
import '../model/video_episode.dart';
import '../tracking/playback_tracker.dart';
import '../widgets/debug_panel.dart';

class DetailPage extends StatefulWidget {
  const DetailPage({
    super.key,
    required this.vid,
    required this.endpoint,
    required this.appKey,
  });

  static const routeName = '/detail';

  final String vid;
  final String endpoint;
  final String appKey;

  @override
  State<DetailPage> createState() => _DetailPageState();
}

class _DetailPageState extends State<DetailPage> {
  VideoEpisode? _episode;
  VideoPlayerController? _controller;
  final _playback = PlaybackTracker();
  bool _playStartReported = false;

  @override
  void initState() {
    super.initState();
    _episode = DemoCatalog.findByVid(widget.vid);
    if (_episode != null) {
      _initPlayer(_episode!);
    }
  }

  Future<void> _initPlayer(VideoEpisode ep) async {
    final controller = VideoPlayerController.networkUrl(Uri.parse(ep.streamUrl));
    _controller = controller;
    controller.addListener(_onPlayerUpdate);
    await controller.initialize();
    if (!mounted) return;
    setState(() {});
    await controller.play();
  }

  void _onPlayerUpdate() {
    final controller = _controller;
    final ep = _episode;
    if (controller == null || ep == null || !controller.value.isInitialized) {
      return;
    }
    if (!_playStartReported && controller.value.isPlaying) {
      _playStartReported = true;
      _playback.onEpisodeReady(ep, autoPlay: true);
    }
    if (controller.value.isPlaying) {
      _playback.onPlaying();
    }
    if (controller.value.hasError) {
      _playback.onError(controller.value.errorDescription ?? 'unknown');
    }
    if (controller.value.position >= controller.value.duration &&
        controller.value.duration > Duration.zero) {
      _playback.onEnded(controller.value.position.inMilliseconds);
      _playStartReported = false;
    }
  }

  Map<String, Object?> _pageParams() {
    final ep = _episode!;
    return {
      Params.vid: ep.vid,
      if (ep.seriesId.isNotEmpty) Params.seriesId: ep.seriesId,
      if (ep.epNum > 0) Params.epNum: ep.epNum,
    };
  }

  @override
  void dispose() {
    final controller = _controller;
    if (controller != null) {
      if (controller.value.isInitialized) {
        _playback.onStopped(controller.value.position.inMilliseconds);
      }
      controller.removeListener(_onPlayerUpdate);
      controller.dispose();
    }
    _playback.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ep = _episode;
    if (ep == null) {
      return const Scaffold(body: Center(child: Text('视频不存在')));
    }

    final controller = _controller;
    final ready = controller != null && controller.value.isInitialized;

    return TrackedPage(
      pgid: Pages.videoDetail,
      pgParams: _pageParams(),
      child: Scaffold(
        appBar: AppBar(title: Text(ep.title)),
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            DebugPanel(
              pgid: Pages.videoDetail,
              endpoint: widget.endpoint,
              appKey: widget.appKey,
            ),
            AspectRatio(
              aspectRatio: ready ? controller.value.aspectRatio : 16 / 9,
              child: ready
                  ? Stack(
                      alignment: Alignment.center,
                      children: [
                        VideoPlayer(controller),
                        if (!controller.value.isPlaying)
                          IconButton(
                            iconSize: 64,
                            icon: const Icon(Icons.play_circle, color: Colors.white),
                            onPressed: () async {
                              GisoTracker.instance.elementClick(
                                eid: Elements.playBtn,
                                params: {Params.vid: ep.vid},
                              );
                              await controller.play();
                            },
                          ),
                      ],
                    )
                  : const ColoredBox(
                      color: Colors.black,
                      child: Center(child: CircularProgressIndicator()),
                    ),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    ep.title,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 4),
                  Text('${ep.cpName} · ${ep.definition} · ${ep.durationLabel()}'),
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 8,
                    children: [
                      FilledButton.icon(
                        onPressed: ready
                            ? () async {
                                GisoTracker.instance.elementClick(
                                  eid: Elements.likeBtn,
                                  params: {Params.vid: ep.vid},
                                );
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text('已上报 element_click · like_btn'),
                                  ),
                                );
                              }
                            : null,
                        icon: const Icon(Icons.favorite_border),
                        label: const Text('点赞'),
                      ),
                      OutlinedButton.icon(
                        onPressed: () {
                          GisoTracker.instance.elementClick(
                            eid: Elements.shareBtn,
                            params: {Params.vid: ep.vid},
                          );
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text('已上报 element_click · share_btn'),
                            ),
                          );
                        },
                        icon: const Icon(Icons.share),
                        label: const Text('分享'),
                      ),
                      if (ep.seriesId.isNotEmpty)
                        OutlinedButton.icon(
                          onPressed: () {
                            GisoTracker.instance.elementClick(
                              eid: Elements.fullscreenBtn,
                              params: {Params.vid: ep.vid},
                            );
                            Navigator.pushNamed(
                              context,
                              '/series',
                              arguments: ep.seriesId,
                            );
                          },
                          icon: const Icon(Icons.playlist_play),
                          label: const Text('选集'),
                        ),
                    ],
                  ),
                  if (ep.seriesId.isNotEmpty) ...[
                    const SizedBox(height: 16),
                    Text('同系列', style: Theme.of(context).textTheme.titleSmall),
                    const SizedBox(height: 8),
                    SizedBox(
                      height: 40,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: DemoCatalog.episodesOf(ep.seriesId).length,
                        separatorBuilder: (_, __) => const SizedBox(width: 8),
                        itemBuilder: (context, index) {
                          final chipEp = DemoCatalog.episodesOf(ep.seriesId)[index];
                          final selected = chipEp.vid == ep.vid;
                          return ChoiceChip(
                            label: Text('第${chipEp.epNum}集'),
                            selected: selected,
                            onSelected: (_) {
                              GisoTracker.instance.elementClick(
                                eid: Elements.episodeItem,
                                pos: index,
                                params: {
                                  Params.vid: chipEp.vid,
                                  Params.seriesId: chipEp.seriesId,
                                  Params.epNum: chipEp.epNum,
                                },
                              );
                              if (!selected) {
                                Navigator.pushReplacementNamed(
                                  context,
                                  DetailPage.routeName,
                                  arguments: chipEp.vid,
                                );
                              }
                            },
                          );
                        },
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
