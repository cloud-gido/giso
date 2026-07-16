import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:giso_tracker/giso_tracker.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'pages/detail_page.dart';
import 'pages/feed_page.dart';
import 'pages/series_page.dart';

const gisoEndpoint = String.fromEnvironment(
  'GISO_ENDPOINT',
  defaultValue: 'https://gamelinelab-giso.envir.dev/v1/track',
);
const gisoAppKey = String.fromEnvironment(
  'GISO_APP_KEY',
  defaultValue: 'video-android-beta',
);
const gisoChannel = String.fromEnvironment(
  'GISO_CHANNEL',
  defaultValue: 'demo',
);

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await GisoTracker.instance.init(GisoConfig(
    appId: gisoAppKey,
    appKey: gisoAppKey,
    appVersion: '1.0.10-demo',
    endpoint: gisoEndpoint,
    channel: gisoChannel,
    debug: kDebugMode,
    // Demo 用 15s 便于验证 app_heartbeat；生产默认 60s，也可由 /v1/config 下发
    heartbeatIntervalMs: 15000,
  ));
  // 历史无账号体系：业务自管设备 ID → common.biz_did
  final prefs = await SharedPreferences.getInstance();
  var bizDid = prefs.getString('demo_biz_did');
  if (bizDid == null || bizDid.isEmpty) {
    bizDid = 'biz-${DateTime.now().millisecondsSinceEpoch}';
    await prefs.setString('demo_biz_did', bizDid);
  }
  GisoTracker.instance.setBizDid(bizDid);
  GisoLifecycleBinding.attach();
  runApp(const VideoDemoApp());
}

class VideoDemoApp extends StatelessWidget {
  const VideoDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'GISO Flutter 长视频',
      theme: ThemeData(colorSchemeSeed: Colors.deepPurple, useMaterial3: true),
      home: FeedPage(endpoint: gisoEndpoint, appKey: gisoAppKey),
      onGenerateRoute: (settings) {
        switch (settings.name) {
          case DetailPage.routeName:
            final vid = settings.arguments as String? ?? 'vid_journey_01';
            return MaterialPageRoute<void>(
              builder: (_) => DetailPage(
                vid: vid,
                endpoint: gisoEndpoint,
                appKey: gisoAppKey,
              ),
              settings: settings,
            );
          case SeriesPage.routeName:
            final seriesId = settings.arguments as String? ?? 'series_journey';
            return MaterialPageRoute<void>(
              builder: (_) => SeriesPage(
                seriesId: seriesId,
                endpoint: gisoEndpoint,
                appKey: gisoAppKey,
              ),
              settings: settings,
            );
        }
        return null;
      },
    );
  }
}
