import 'dart:ui';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:package_info_plus/package_info_plus.dart';

/// Collects device / network fields for [common] — mirrors Android [CommonParams].
class DeviceCollector {
  DeviceCollector({
    DeviceInfoPlugin? deviceInfo,
    Connectivity? connectivity,
  })  : _deviceInfo = deviceInfo ?? DeviceInfoPlugin(),
        _connectivity = connectivity ?? Connectivity();

  final DeviceInfoPlugin _deviceInfo;
  final Connectivity _connectivity;

  String _osVersion = '';
  String _devBrand = '';
  String _devModel = '';
  String _appPkg = '';
  bool _initialized = false;

  Future<void> init(String platform) async {
    if (_initialized) return;
    try {
      switch (platform) {
        case 'android':
          final info = await _deviceInfo.androidInfo;
          _osVersion = info.version.release;
          _devBrand = info.brand;
          _devModel = info.model;
        case 'ios':
          final info = await _deviceInfo.iosInfo;
          _osVersion = info.systemVersion;
          _devBrand = 'Apple';
          _devModel = info.utsname.machine;
        default:
          break;
      }
    } catch (_) {
      /* keep defaults on desktop / test */
    }
    try {
      final pkg = await PackageInfo.fromPlatform();
      _appPkg = pkg.packageName;
    } catch (_) {
      /* keep empty on unsupported platforms */
    }
    _initialized = true;
  }

  String get osVersion => _osVersion;
  String get devBrand => _devBrand;
  String get devModel => _devModel;
  String get appPkg => _appPkg;

  String screenRes() {
    try {
      final views = PlatformDispatcher.instance.views;
      if (views.isEmpty) return '';
      final view = views.first;
      final ratio = view.devicePixelRatio;
      if (ratio <= 0) return '';
      final size = view.physicalSize / ratio;
      return '${size.width.toInt()}x${size.height.toInt()}';
    } catch (_) {
      return '';
    }
  }

  Future<String> netType() async {
    try {
      final results = await _connectivity.checkConnectivity();
      if (results.isEmpty ||
          results.every((r) => r == ConnectivityResult.none)) {
        return 'none';
      }
      if (results.contains(ConnectivityResult.wifi) ||
          results.contains(ConnectivityResult.ethernet)) {
        return 'wifi';
      }
      return 'cellular';
    } catch (_) {
      return 'unknown';
    }
  }

  String lang() {
    try {
      return PlatformDispatcher.instance.locale.toLanguageTag();
    } catch (_) {
      return '';
    }
  }
}

/// Prefer explicit [configValue] when set; otherwise use auto-collected value.
String pickCommonField(String configValue, String autoValue) =>
    configValue.isNotEmpty ? configValue : autoValue;
