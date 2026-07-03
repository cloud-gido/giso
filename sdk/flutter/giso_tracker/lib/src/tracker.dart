import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import 'common.dart';
import 'config.dart';
import 'event_queue.dart';
import 'types.dart';

/// GISO tracker facade for Flutter — manual page/element/biz events.
class GisoTracker with WidgetsBindingObserver {
  GisoTracker._();

  static final GisoTracker instance = GisoTracker._();

  GisoConfig? _config;
  SharedPreferences? _prefs;
  EventQueue? _queue;
  String _uid = '';
  String _platform = 'android';
  bool _lifecycleHooked = false;
  int _foregroundTs = 0;

  _PageState? _curPage;
  String _refPgid = '';
  String _refEid = '';

  bool get isInitialized => _config != null;

  /// Initialize once at app start. Call [GisoLifecycleBinding.attach] for lifecycle events.
  Future<void> init(GisoConfig config) async {
    _config = config;
    _prefs = await SharedPreferences.getInstance();
    _platform = config.platform ?? _detectPlatform();
    _queue = EventQueue(
      endpoint: config.endpoint,
      appKey: config.appKey,
      debug: config.debug,
      prefs: _prefs!,
      batchSize: config.batchSize,
      flushIntervalMs: config.flushIntervalMs,
    );
    await _fetchRemoteConfig();
    if (await markActivated(_prefs!)) {
      await _emit('app_install');
    }
    await _emit('app_launch');
  }

  void attachLifecycle() {
    if (_lifecycleHooked) return;
    WidgetsBinding.instance.addObserver(this);
    _lifecycleHooked = true;
  }

  void detachLifecycle() {
    if (!_lifecycleHooked) return;
    WidgetsBinding.instance.removeObserver(this);
    _lifecycleHooked = false;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.resumed:
        _foregroundTs = DateTime.now().millisecondsSinceEpoch;
        unawaited(_emit('app_foreground'));
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
        final fgDur = _foregroundTs > 0
            ? DateTime.now().millisecondsSinceEpoch - _foregroundTs
            : 0;
        unawaited(_emit('app_background', pageExtras: {'fg_dur': fgDur}));
        unawaited(flush());
      case AppLifecycleState.inactive:
      case AppLifecycleState.hidden:
        break;
    }
  }

  void setUid(String uid) => _uid = uid;
  void clearUid() => _uid = '';

  Future<void> enterPage(
    String pgid, [
    ParamMap? pgParams,
    Passthrough? pt,
  ]) async {
    if (_curPage != null) await exitPage();
    _curPage = _PageState(
      pgid: pgid,
      params: pgParams,
      pt: pt,
      enterTs: DateTime.now().millisecondsSinceEpoch,
    );
    await _emit('page_enter', pt: pt);
  }

  Future<void> exitPage() async {
    if (_curPage == null) return;
    final stay = DateTime.now().millisecondsSinceEpoch - _curPage!.enterTs;
    final pt = _curPage!.pt;
    await _emit('page_exit', pageExtras: {'pg_stay': stay}, pt: pt);
    _refPgid = _curPage!.pgid;
    _curPage = null;
  }

  Future<void> elementClick({
    required String eid,
    String? mod,
    int? pos,
    ParamMap? params,
    Passthrough? pt,
  }) async {
    _refEid = eid;
    await _emit(
      'element_click',
      element: ElementContext(eid: eid, mod: mod, pos: pos, params: params),
      pt: pt,
    );
  }

  Future<void> elementExposure({
    required String eid,
    String? mod,
    int? pos,
    ParamMap? params,
    required int expDur,
    required double expRatio,
    Passthrough? pt,
  }) async {
    await _emit(
      'element_exposure',
      element: ElementContext(
        eid: eid,
        mod: mod,
        pos: pos,
        params: params,
        expDur: expDur,
        expRatio: expRatio,
      ),
      pt: pt,
    );
  }

  Future<void> bizEvent(
    String code, [
    ParamMap? params,
    Passthrough? pt,
  ]) async {
    await _emit(
      'biz_event',
      biz: BizContext(code: code, params: params),
      pt: _mergePt(_curPage?.pt, pt),
    );
  }

  /// Low-level track for advanced callers.
  Future<void> track(Map<String, dynamic> envelope) async {
    _queue?.push(envelope);
  }

  Future<void> flush() => _queue?.flush() ?? Future.value();

  PageContext _pageContext({Map<String, Object?>? extras}) {
    final pgid = _curPage?.pgid ?? '';
    final base = PageContext(
      pgid: pgid,
      pgParams: _curPage?.params,
      refPgid: _refPgid.isEmpty ? null : _refPgid,
      refEid: _refEid.isEmpty ? null : _refEid,
      pgStay: extras?['pg_stay'] as int?,
    );
    if (extras == null || extras.isEmpty) return base;
    final json = base.toJson();
    for (final e in extras.entries) {
      if (e.key != 'pg_stay') json[e.key] = e.value;
    }
    return PageContext(
      pgid: json['pgid'] as String,
      pgParams: json['pg_params'] as ParamMap?,
      refPgid: json['ref_pgid'] as String?,
      refEid: json['ref_eid'] as String?,
      pgStay: json['pg_stay'] as int?,
    );
  }

  Future<void> _emit(
    String event, {
    ElementContext? element,
    BizContext? biz,
    Map<String, Object?>? pageExtras,
    Passthrough? pt,
  }) async {
    final cfg = _config;
    final queue = _queue;
    final prefs = _prefs;
    if (cfg == null || queue == null || prefs == null) {
      throw StateError('GisoTracker.init() must be called first');
    }
    final common = await buildCommon(
      config: cfg,
      prefs: prefs,
      uid: _uid,
      platform: _platform,
    );
    final envelope = <String, dynamic>{
      'event': event,
      'log_id': newLogId(),
      'ctime': DateTime.now().millisecondsSinceEpoch,
      'common': common.toJson(),
    };
    final page = _pageContext(extras: pageExtras);
    if (page.pgid.isNotEmpty || pageExtras != null) {
      envelope['page'] = page.toJson();
    }
    if (element != null) envelope['element'] = element.toJson();
    if (biz != null) envelope['biz'] = biz.toJson();
    final mergedPt = _mergePt(_curPage?.pt, pt);
    if (mergedPt != null && mergedPt.isNotEmpty) envelope['pt'] = mergedPt;
    queue.push(envelope);
  }

  Future<void> _fetchRemoteConfig() async {
    final cfg = _config;
    final queue = _queue;
    if (cfg == null || queue == null) return;
    final url = cfg.endpoint.replaceFirst(RegExp(r'/v1/track/?$'), '/v1/config');
    if (url == cfg.endpoint) return;
    try {
      final resp = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 5));
      if (resp.statusCode != 200) return;
      final c = jsonDecode(resp.body);
      if (c is! Map) return;
      queue.updateBatching(
        batchSize: (c['batch_size'] as num?)?.toInt() ?? cfg.batchSize,
        flushIntervalMs:
            (c['flush_interval_ms'] as num?)?.toInt() ?? cfg.flushIntervalMs,
      );
    } catch (_) {
      /* silent fallback */
    }
  }

  Passthrough? _mergePt(Passthrough? a, Passthrough? b) {
    if (a == null && b == null) return null;
    final merged = <String, dynamic>{...?a, ...?b};
    return merged.isEmpty ? null : merged;
  }

  String _detectPlatform() {
    if (kIsWeb) return 'web';
    if (Platform.isIOS) return 'ios';
    return 'android';
  }
}

class _PageState {
  _PageState({
    required this.pgid,
    this.params,
    this.pt,
    required this.enterTs,
  });

  final String pgid;
  final ParamMap? params;
  final Passthrough? pt;
  final int enterTs;
}
