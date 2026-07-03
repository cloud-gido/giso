import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:giso_tracker/giso_tracker.dart';

import 'detail_page.dart';

class FeedPage extends StatefulWidget {
  const FeedPage({super.key});

  @override
  State<FeedPage> createState() => _FeedPageState();
}

class _FeedPageState extends State<FeedPage> {
  static const _videos = [
    ('v001', 'Ocean Documentary'),
    ('v002', 'City Night Walk'),
    ('v003', 'Cooking Basics'),
  ];

  String? _did;

  @override
  void initState() {
    super.initState();
    _loadDid();
  }

  Future<void> _loadDid() async {
    // did is internal; demo shows tracker is alive via page events in admin SSE.
    setState(() => _did = 'see admin SSE filter');
  }

  @override
  Widget build(BuildContext context) {
    return TrackedPage(
      pgid: Pages.videoFeed,
      pgParams: {Params.tabName: 'recommend'},
      child: Scaffold(
        appBar: AppBar(title: const Text('GISO Flutter · 推荐流')),
        body: Column(
          children: [
            if (_did != null)
              Material(
                color: Colors.black87,
                child: Padding(
                  padding: const EdgeInsets.all(8),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          '联调：管理台实时联调按 did 过滤（init 后自动上报 page_enter）',
                          style: TextStyle(color: Colors.grey.shade300, fontSize: 12),
                        ),
                      ),
                      TextButton(
                        onPressed: () {
                          Clipboard.setData(const ClipboardData(text: 'flutter-demo'));
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('提示已复制')),
                          );
                        },
                        child: const Text('复制提示', style: TextStyle(color: Colors.white70)),
                      ),
                    ],
                  ),
                ),
              ),
            Expanded(
              child: ListView.builder(
                itemCount: _videos.length,
                itemBuilder: (context, index) {
                  final (vid, title) = _videos[index];
                  return ListTile(
                    leading: CircleAvatar(child: Text('${index + 1}')),
                    title: Text(title),
                    subtitle: Text(vid),
                    onTap: () {
                      GisoTracker.instance.elementClick(
                        eid: Elements.videoCard,
                        pos: index,
                        params: {Params.vid: vid},
                      );
                      Navigator.pushNamed(
                        context,
                        DetailPage.routeName,
                        arguments: vid,
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
