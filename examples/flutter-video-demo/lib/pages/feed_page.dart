import 'package:flutter/material.dart';
import 'package:giso_tracker/giso_tracker.dart';

import '../model/demo_catalog.dart';
import '../model/video_episode.dart';
import '../widgets/debug_panel.dart';
import 'detail_page.dart';
import 'series_page.dart';

class FeedPage extends StatefulWidget {
  const FeedPage({
    super.key,
    required this.endpoint,
    required this.appKey,
  });

  final String endpoint;
  final String appKey;

  @override
  State<FeedPage> createState() => _FeedPageState();
}

class _FeedPageState extends State<FeedPage> {
  static const _tabRecommend = 'recommend';
  static const _tabSeries = 'series';

  int _tabIndex = 0;
  int _navIndex = 0;
  late String _recTraceId = _newTraceId();

  String get _currentTab => _tabIndex == 0 ? _tabRecommend : _tabSeries;

  String _newTraceId() => 'rec-${DateTime.now().microsecondsSinceEpoch}';

  List<VideoEpisode> get _items => _tabIndex == 0
      ? DemoCatalog.feedItems()
      : DemoCatalog.seriesList();

  void _onTabChanged(int index) {
    setState(() {
      _tabIndex = index;
      _recTraceId = _newTraceId();
    });
  }

  void _openDetail(VideoEpisode ep, int pos) {
    GisoTracker.instance.elementClick(
      eid: Elements.videoCard,
      pos: pos,
      params: {
        Params.vid: ep.vid,
        Params.cpId: ep.cpId,
        Params.seriesId: ep.seriesId.isEmpty ? ep.vid : ep.seriesId,
        if (ep.epNum > 0) Params.epNum: ep.epNum,
      },
      pt: {Params.recTraceId: _recTraceId},
    );
    Navigator.pushNamed(
      context,
      DetailPage.routeName,
      arguments: ep.vid,
    );
  }

  void _openSeries(String seriesId) {
    Navigator.pushNamed(
      context,
      SeriesPage.routeName,
      arguments: seriesId,
    );
  }

  @override
  Widget build(BuildContext context) {
    return TrackedPage(
      key: ValueKey(_currentTab),
      pgid: Pages.videoFeed,
      pgParams: {Params.tabName: _currentTab},
      pt: {Params.recTraceId: _recTraceId},
      child: Scaffold(
        appBar: AppBar(title: const Text('GISO Flutter · 长视频')),
        body: Column(
          children: [
            DebugPanel(
              pgid: Pages.videoFeed,
              endpoint: widget.endpoint,
              appKey: widget.appKey,
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              child: SegmentedButton<int>(
                segments: const [
                  ButtonSegment(value: 0, label: Text('推荐')),
                  ButtonSegment(value: 1, label: Text('追剧')),
                ],
                selected: {_tabIndex},
                onSelectionChanged: (value) => _onTabChanged(value.first),
              ),
            ),
            Expanded(
              child: ListView.builder(
                itemCount: _items.length,
                itemBuilder: (context, index) {
                  final ep = _items[index];
                  return _VideoCardTile(
                    episode: ep,
                    index: index,
                    recTraceId: _recTraceId,
                    onTap: () => _openDetail(ep, index),
                  );
                },
              ),
            ),
          ],
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _navIndex,
          onDestinationSelected: (index) {
            setState(() => _navIndex = index);
            if (index == 1) {
              final series = DemoCatalog.seriesList();
              if (series.isNotEmpty && series.first.seriesId.isNotEmpty) {
                _openSeries(series.first.seriesId);
              }
            } else if (index == 2) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('演示版仅展示长视频埋点链路')),
              );
            }
          },
          destinations: const [
            NavigationDestination(icon: Icon(Icons.home), label: '首页'),
            NavigationDestination(icon: Icon(Icons.video_library), label: '剧集'),
            NavigationDestination(icon: Icon(Icons.person), label: '我的'),
          ],
        ),
      ),
    );
  }
}

class _VideoCardTile extends StatefulWidget {
  const _VideoCardTile({
    required this.episode,
    required this.index,
    required this.recTraceId,
    required this.onTap,
  });

  final VideoEpisode episode;
  final int index;
  final String recTraceId;
  final VoidCallback onTap;

  @override
  State<_VideoCardTile> createState() => _VideoCardTileState();
}

class _VideoCardTileState extends State<_VideoCardTile> {
  bool _exposed = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _reportExposure());
  }

  void _reportExposure() {
    if (_exposed) return;
    _exposed = true;
    final ep = widget.episode;
    GisoTracker.instance.elementExposure(
      eid: Elements.videoCard,
      pos: widget.index,
      params: {
        Params.vid: ep.vid,
        Params.cpId: ep.cpId,
        Params.seriesId: ep.seriesId.isEmpty ? ep.vid : ep.seriesId,
        if (ep.epNum > 0) Params.epNum: ep.epNum,
      },
      expDur: 0,
      expRatio: 1.0,
      pt: {Params.recTraceId: widget.recTraceId},
    );
  }

  @override
  Widget build(BuildContext context) {
    final ep = widget.episode;
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: ListTile(
        leading: CircleAvatar(child: Text('${widget.index + 1}')),
        title: Text(ep.title),
        subtitle: Text('${ep.cpName} · ${ep.definition} · ${ep.durationLabel()}'),
        trailing: const Icon(Icons.play_circle_outline),
        onTap: widget.onTap,
      ),
    );
  }
}
