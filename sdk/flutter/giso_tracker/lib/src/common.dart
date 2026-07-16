import 'dart:math';

import 'package:shared_preferences/shared_preferences.dart';

import 'config.dart';
import 'device_collector.dart';
import 'types.dart';

const sdkVersion = '1.0.8';
const _didKey = 'giso_did';
const _sessionKey = 'giso_session';
const _activatedKey = 'giso_activated';
const _sessionGapMs = 30 * 60 * 1000;

String newLogId() {
  final r = Random();
  return '${_hex(r, 8)}-${_hex(r, 4)}-4${_hex(r, 3)}-${(8 + r.nextInt(4)).toRadixString(16)}${_hex(r, 3)}-${_hex(r, 12)}';
}

String _hex(Random r, int len) =>
    List.generate(len, (_) => r.nextInt(16).toRadixString(16)).join();

Future<String> getOrCreateDid(SharedPreferences prefs) async {
  var did = prefs.getString(_didKey);
  if (did == null || did.isEmpty) {
    did = newLogId();
    await prefs.setString(_didKey, did);
  }
  return did;
}

Future<String> touchSession(SharedPreferences prefs) async {
  final now = DateTime.now().millisecondsSinceEpoch;
  final raw = prefs.getString(_sessionKey);
  if (raw != null) {
    final parts = raw.split('|');
    if (parts.length == 2) {
      final id = parts[0];
      final ts = int.tryParse(parts[1]) ?? 0;
      if (now - ts < _sessionGapMs) {
        await prefs.setString(_sessionKey, '$id|$now');
        return id;
      }
    }
  }
  final id = 's-${newLogId()}';
  await prefs.setString(_sessionKey, '$id|$now');
  return id;
}

Future<bool> markActivated(SharedPreferences prefs) async {
  if (prefs.getBool(_activatedKey) == true) return false;
  await prefs.setBool(_activatedKey, true);
  return true;
}

String tzOffset() {
  final offset = DateTime.now().timeZoneOffset;
  final sign = offset.isNegative ? '-' : '+';
  final total = offset.inMinutes.abs();
  final h = (total ~/ 60).toString().padLeft(2, '0');
  final m = (total % 60).toString().padLeft(2, '0');
  return '$sign$h:$m';
}

Future<CommonParams> buildCommon({
  required GisoConfig config,
  required SharedPreferences prefs,
  required String uid,
  String bizDid = '',
  required String platform,
  DeviceCollector? device,
}) async {
  final net = device != null
      ? await device.netType()
      : (config.netType != 'unknown' ? config.netType : 'unknown');
  return CommonParams(
    appId: config.appId,
    platform: platform,
    appPkg: pickCommonField(config.appPkg, device?.appPkg ?? ''),
    appVersion: config.appVersion,
    sdkVersion: sdkVersion,
    did: await getOrCreateDid(prefs),
    uid: uid,
    bizDid: bizDid,
    sessionId: await touchSession(prefs),
    channel: config.channel,
    env: config.resolvedEnv(),
    osVersion: pickCommonField(config.osVersion, device?.osVersion ?? ''),
    devBrand: pickCommonField(config.devBrand, device?.devBrand ?? ''),
    devModel: pickCommonField(config.devModel, device?.devModel ?? ''),
    screenRes: pickCommonField(config.screenRes, device?.screenRes() ?? ''),
    netType: pickCommonField(
      config.netType != 'unknown' ? config.netType : '',
      net,
    ),
    lang: pickCommonField(config.lang, device?.lang() ?? ''),
    tz: config.tz.isNotEmpty ? config.tz : tzOffset(),
  );
}
