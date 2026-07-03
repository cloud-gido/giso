import 'package:flutter/material.dart';
import 'package:giso_tracker/giso_tracker.dart';

class DetailPage extends StatefulWidget {
  const DetailPage({super.key, required this.vid});

  static const routeName = '/detail';

  final String vid;

  @override
  State<DetailPage> createState() => _DetailPageState();
}

class _DetailPageState extends State<DetailPage> {
  bool _playing = false;

  void _togglePlay() {
    setState(() => _playing = !_playing);
    if (_playing) {
      GisoTracker.instance.bizEvent(
        BizEvents.videoPlayStart,
        {Params.vid: widget.vid},
      );
    } else {
      GisoTracker.instance.bizEvent(
        BizEvents.videoPlayEnd,
        {Params.vid: widget.vid, Params.playDur: 3000, Params.playPos: 3000},
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return TrackedPage(
      pgid: Pages.videoDetail,
      pgParams: {Params.vid: widget.vid},
      child: Scaffold(
        appBar: AppBar(title: Text('播放 · ${widget.vid}')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                _playing ? Icons.pause_circle : Icons.play_circle,
                size: 96,
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _togglePlay,
                child: Text(_playing ? '暂停' : '播放'),
              ),
              const SizedBox(height: 24),
              OutlinedButton(
                onPressed: () {
                  GisoTracker.instance.elementClick(
                    eid: Elements.likeBtn,
                    params: {Params.vid: widget.vid},
                  );
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('已上报 element_click · like_btn')),
                  );
                },
                child: const Text('点赞'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
