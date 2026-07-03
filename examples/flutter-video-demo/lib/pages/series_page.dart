import 'package:flutter/material.dart';
import 'package:giso_tracker/giso_tracker.dart';

import '../model/demo_catalog.dart';
import '../model/video_episode.dart';
import '../widgets/debug_panel.dart';
import 'detail_page.dart';

class SeriesPage extends StatelessWidget {
  const SeriesPage({
    super.key,
    required this.seriesId,
    required this.endpoint,
    required this.appKey,
  });

  static const routeName = '/series';

  final String seriesId;
  final String endpoint;
  final String appKey;

  @override
  Widget build(BuildContext context) {
    final episodes = DemoCatalog.episodesOf(seriesId);
    return TrackedPage(
      pgid: Pages.videoSeries,
      pgParams: {Params.seriesId: seriesId},
      child: Scaffold(
        appBar: AppBar(title: Text(DemoCatalog.seriesTitle(seriesId))),
        body: Column(
          children: [
            DebugPanel(
              pgid: Pages.videoSeries,
              endpoint: endpoint,
              appKey: appKey,
            ),
            Expanded(
              child: ListView.builder(
                itemCount: episodes.length,
                itemBuilder: (context, index) {
                  final ep = episodes[index];
                  return _EpisodeTile(
                    episode: ep,
                    index: index,
                    onTap: () {
                      GisoTracker.instance.elementClick(
                        eid: Elements.episodeItem,
                        pos: index,
                        params: {
                          Params.vid: ep.vid,
                          Params.seriesId: ep.seriesId,
                          Params.epNum: ep.epNum,
                        },
                      );
                      Navigator.pushNamed(
                        context,
                        DetailPage.routeName,
                        arguments: ep.vid,
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EpisodeTile extends StatefulWidget {
  const _EpisodeTile({
    required this.episode,
    required this.index,
    required this.onTap,
  });

  final VideoEpisode episode;
  final int index;
  final VoidCallback onTap;

  @override
  State<_EpisodeTile> createState() => _EpisodeTileState();
}

class _EpisodeTileState extends State<_EpisodeTile> {
  bool _exposed = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_exposed) return;
      _exposed = true;
      final ep = widget.episode;
      GisoTracker.instance.elementExposure(
        eid: Elements.episodeItem,
        pos: widget.index,
        params: {
          Params.vid: ep.vid,
          Params.seriesId: ep.seriesId,
          Params.epNum: ep.epNum,
        },
        expDur: 0,
        expRatio: 1.0,
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    final ep = widget.episode;
    return ListTile(
      leading: CircleAvatar(child: Text('${ep.epNum}')),
      title: Text(ep.title),
      subtitle: Text('${ep.definition} · ${ep.durationLabel()}'),
      trailing: const Icon(Icons.chevron_right),
      onTap: widget.onTap,
    );
  }
}
