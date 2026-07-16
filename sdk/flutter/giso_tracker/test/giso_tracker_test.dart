import 'package:flutter_test/flutter_test.dart';
import 'package:giso_tracker/giso_tracker.dart';
import 'package:giso_tracker/src/device_collector.dart';

void main() {
  test('PageContext serializes optional fields', () {
    const ctx = PageContext(
      pgid: 'video_feed',
      pgParams: {'tab_name': 'recommend'},
      refPgid: 'home',
      pgStay: 1200,
    );
    final json = ctx.toJson();
    expect(json['pgid'], 'video_feed');
    expect(json['pg_params'], {'tab_name': 'recommend'});
    expect(json['ref_pgid'], 'home');
    expect(json['pg_stay'], 1200);
    expect(json.containsKey('ref_eid'), isFalse);
  });

  test('ElementContext includes exposure metrics', () {
    const ctx = ElementContext(
      eid: 'video_card',
      pos: 2,
      expDur: 800,
      expRatio: 0.72,
    );
    final json = ctx.toJson();
    expect(json['eid'], 'video_card');
    expect(json['pos'], 2);
    expect(json['exp_dur'], 800);
    expect(json['exp_ratio'], 0.72);
  });

  test('pickCommonField prefers explicit config overrides', () {
    expect(pickCommonField('custom', 'auto'), 'custom');
    expect(pickCommonField('', 'auto'), 'auto');
  });

  test('CommonParams includes app_pkg', () {
    const common = CommonParams(
      appId: 'demo',
      platform: 'android',
      appPkg: 'com.example.demo',
      appVersion: '1.0.0',
      sdkVersion: '1.0.10',
      did: 'did-1',
      uid: '',
      bizDid: 'legacy-device-1',
      sessionId: 's-1',
      channel: 'google_play',
      env: 'test',
      osVersion: '14',
      devBrand: 'Google',
      devModel: 'Pixel',
      screenRes: '1080x2400',
      netType: 'wifi',
      lang: 'zh-CN',
      tz: '+08:00',
    );
    expect(common.toJson()['app_pkg'], 'com.example.demo');
    expect(common.toJson()['sdk_runtime'], 'flutter');
    expect(common.toJson()['biz_did'], 'legacy-device-1');
  });

  test('GisoConfig resolves env from debug flag', () {
    const cfg = GisoConfig(
      appId: 'demo',
      appKey: 'demo',
      appVersion: '1.0.0',
      endpoint: 'https://example.com/v1/track',
      debug: true,
    );
    expect(cfg.resolvedEnv(), 'test');
    expect(
      const GisoConfig(
        appId: 'demo',
        appKey: 'demo',
        appVersion: '1.0.0',
        endpoint: 'https://example.com/v1/track',
        env: 'prod',
      ).resolvedEnv(),
      'prod',
    );
  });
}
