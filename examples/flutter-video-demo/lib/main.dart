import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:giso_tracker/giso_tracker.dart';

import 'pages/detail_page.dart';
import 'pages/feed_page.dart';

const _endpoint = String.fromEnvironment(
  'GISO_ENDPOINT',
  defaultValue: 'https://gamelinelab-giso.envir.dev/v1/track',
);
const _appKey = String.fromEnvironment(
  'GISO_APP_KEY',
  defaultValue: 'video-android-beta',
);

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await GisoTracker.instance.init(GisoConfig(
    appId: _appKey,
    appKey: _appKey,
    appVersion: '1.0.0-demo',
    endpoint: _endpoint,
    debug: kDebugMode,
    devBrand: 'flutter-demo',
    devModel: 'demo',
  ));
  GisoLifecycleBinding.attach();
  runApp(const VideoDemoApp());
}

class VideoDemoApp extends StatelessWidget {
  const VideoDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'GISO Flutter Demo',
      theme: ThemeData(colorSchemeSeed: Colors.deepPurple, useMaterial3: true),
      home: const FeedPage(),
      onGenerateRoute: (settings) {
        if (settings.name == DetailPage.routeName) {
          final vid = settings.arguments as String? ?? 'v001';
          return MaterialPageRoute<void>(
            builder: (_) => DetailPage(vid: vid),
            settings: settings,
          );
        }
        return null;
      },
    );
  }
}
