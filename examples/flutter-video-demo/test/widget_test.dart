import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_video_demo/model/demo_catalog.dart';

void main() {
  test('demo catalog exposes feed and series items', () {
    expect(DemoCatalog.feedItems(), isNotEmpty);
    expect(DemoCatalog.seriesList(), isNotEmpty);
    expect(DemoCatalog.findByVid('vid_journey_01'), isNotNull);
  });
}
