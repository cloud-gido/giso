import 'dart:async';

import 'package:giso_tracker/giso_tracker.dart';

import '../model/video_episode.dart';

/// Mirrors Android demo: video_play_start / heartbeat / end / error.
class PlaybackTracker {
  static const _heartbeatMs = Duration(seconds: 30);

  VideoEpisode? _episode;
  bool _started = false;
  int _accumulatedMs = 0;
  int _lastTickMs = 0;
  double _speed = 1.0;
  Timer? _heartbeatTimer;

  void onEpisodeReady(VideoEpisode ep, {required bool autoPlay}) {
    if (_started) return;
    _episode = ep;
    _started = true;
    _lastTickMs = DateTime.now().millisecondsSinceEpoch;
    final params = _baseParams();
    params[Params.isAuto] = autoPlay ? 1 : 0;
    GisoTracker.instance.bizEvent(BizEvents.videoPlayStart, params);
    _scheduleHeartbeat();
  }

  void onPlaying() {
    _lastTickMs = DateTime.now().millisecondsSinceEpoch;
  }

  void onPaused(int positionMs) {
    _tick(positionMs);
    _heartbeatTimer?.cancel();
    _emitHeartbeat(positionMs);
  }

  void onSpeedChanged(double speed) => _speed = speed;

  void onEnded(int positionMs) => _finish(positionMs);

  void onStopped(int positionMs) => _finish(positionMs);

  void onError(String reason) {
    final ep = _episode;
    if (ep == null) return;
    GisoTracker.instance.bizEvent(BizEvents.videoPlayError, {
      Params.vid: ep.vid,
      Params.failReason: reason,
    });
    _started = false;
    _heartbeatTimer?.cancel();
  }

  void dispose() {
    _heartbeatTimer?.cancel();
    if (_started) {
      _finish(_accumulatedMs);
    }
  }

  void _finish(int positionMs) {
    if (!_started || _episode == null) return;
    _tick(positionMs);
    _heartbeatTimer?.cancel();
    final params = _baseParams();
    params[Params.playDur] = _accumulatedMs;
    params[Params.playPos] = positionMs;
    params[Params.videoDur] = _episode!.durationMs;
    GisoTracker.instance.bizEvent(BizEvents.videoPlayEnd, params);
    _started = false;
  }

  void _emitHeartbeat([int? positionMs]) {
    final ep = _episode;
    if (!_started || ep == null) return;
    final pos = positionMs ?? (_accumulatedMs > 0 ? _accumulatedMs : 0);
    final params = _baseParams();
    params[Params.playDur] = _accumulatedMs;
    params[Params.playPos] = pos;
    params[Params.speed] = _speed;
    GisoTracker.instance.bizEvent(BizEvents.videoPlayHeartbeat, params);
  }

  void _scheduleHeartbeat() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = Timer.periodic(_heartbeatMs, (_) => _emitHeartbeat());
  }

  void _tick(int positionMs) {
    final now = DateTime.now().millisecondsSinceEpoch;
    if (_lastTickMs > 0) {
      _accumulatedMs += (now - _lastTickMs).clamp(0, 1 << 30);
    }
    _lastTickMs = now;
  }

  Map<String, Object?> _baseParams() {
    final ep = _episode!;
    final series = ep.seriesId.isNotEmpty ? ep.seriesId : ep.vid;
    return {
      Params.vid: ep.vid,
      Params.seriesId: series,
      Params.epNum: ep.epNum > 0 ? ep.epNum : 1,
      Params.definition: ep.definition,
    };
  }
}
