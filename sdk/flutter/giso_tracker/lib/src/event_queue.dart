import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

const _storeKey = 'giso_tracker_queue';
const _maxStored = 500;
const _maxBackoffMs = 60000;

/// Batch queue aligned with Android/iOS lifecycle contract:
/// - cold-start spill held until first [onForeground]
/// - [onBackground] drains queue (persist + send)
/// - [onForeground] drains backlog, sends foreground, then releases spill
class EventQueue {
  EventQueue({
    required this.endpoint,
    required this.appKey,
    required this.debug,
    required SharedPreferences prefs,
    int batchSize = 20,
    int flushIntervalMs = 15000,
  })  : _prefs = prefs,
        _batchSize = batchSize,
        _flushIntervalMs = flushIntervalMs {
    _spill.addAll(_loadSpillFromDisk());
  }

  final String endpoint;
  final String appKey;
  final bool debug;
  final SharedPreferences _prefs;

  final List<Map<String, dynamic>> _buffer = [];
  final List<Map<String, dynamic>> _spill = [];
  bool _spillReleased = false;

  Timer? _timer;
  bool _sending = false;
  int _backoffMs = 1000;
  int _batchSize;
  int _flushIntervalMs;

  void updateBatching({required int batchSize, required int flushIntervalMs}) {
    _batchSize = batchSize;
    _flushIntervalMs = flushIntervalMs;
  }

  void push(Map<String, dynamic> event) {
    if (debug) {
      debugPrint('[giso_tracker] ${event['event']} $event');
    }
    _buffer.add(event);
    if (debug) {
      unawaited(flushAll(urgent: false));
    } else if (_buffer.length >= _batchSize) {
      unawaited(flushAll(urgent: false));
    } else {
      _timer ??= Timer(Duration(milliseconds: _flushIntervalMs), () {
        _timer = null;
        unawaited(flushAll(urgent: false));
      });
    }
  }

  /// Enter foreground: drain backlog → foreground → release cold-start spill.
  Future<void> onForeground(Map<String, dynamic> event) async {
    await flushAll(urgent: false);
    _buffer.add(event);
    if (debug) debugPrint('[giso_tracker] ${event['event']} $event');
    await flushAll(urgent: false);
    await _releaseSpill();
  }

  /// Enter background: enqueue partial heartbeat then background, persist, drain.
  Future<void> onBackground(
    Map<String, dynamic> event, {
    List<Map<String, dynamic>> preceding = const [],
  }) async {
    _timer?.cancel();
    _timer = null;
    _buffer.addAll(preceding);
    _buffer.add(event);
    if (debug) debugPrint('[giso_tracker] ${event['event']} $event');
    await _persistAll();
    await flushAll(urgent: true);
  }

  Future<void> flush() => flushAll(urgent: false);

  Future<void> flushAll({required bool urgent}) async {
    _timer?.cancel();
    _timer = null;
    if (_sending) return;

    _sending = true;
    try {
      while (_buffer.isNotEmpty) {
        final batch = <Map<String, dynamic>>[];
        while (batch.length < _batchSize && _buffer.isNotEmpty) {
          batch.add(_buffer.removeAt(0));
        }
        final status = await _send(batch);
        if (_isAccept(status)) {
          _backoffMs = 1000;
        } else {
          _buffer.insertAll(0, batch);
          await _persistAll();
          if (!urgent) {
            Future<void>.delayed(Duration(milliseconds: _backoffMs), () {
              _backoffMs = (_backoffMs * 2).clamp(1000, _maxBackoffMs);
              unawaited(flushAll(urgent: false));
            });
          }
          return;
        }
      }
      if (_spill.isEmpty) {
        await _prefs.remove(_storeKey);
      } else {
        await _persistAll();
      }
    } finally {
      _sending = false;
    }
  }

  Future<void> _releaseSpill() async {
    if (_spillReleased) return;
    _spillReleased = true;
    while (_spill.isNotEmpty) {
      final batch = <Map<String, dynamic>>[];
      while (batch.length < _batchSize && _spill.isNotEmpty) {
        batch.add(_spill.removeAt(0));
      }
      final status = await _send(batch);
      if (_isAccept(status)) {
        _backoffMs = 1000;
      } else {
        _spill.insertAll(0, batch);
        await _persistAll();
        return;
      }
    }
    await _persistAll();
  }

  bool _isAccept(int status) => status >= 200 && status < 500 && status != 429;

  Future<int> _send(List<Map<String, dynamic>> batch) async {
    final body = utf8.encode(jsonEncode(batch));
    final gz = gzip.encode(body);
    try {
      final resp = await http
          .post(
            Uri.parse(endpoint),
            headers: {
              'Content-Type': 'application/json',
              'Content-Encoding': 'gzip',
              'X-App-Key': appKey,
            },
            body: gz,
          )
          .timeout(const Duration(seconds: 8));
      return resp.statusCode;
    } catch (_) {
      return -1;
    }
  }

  Future<void> _persistAll() async {
    if (_spill.isEmpty && _buffer.isEmpty) {
      await _prefs.remove(_storeKey);
      return;
    }
    final all = <Map<String, dynamic>>[..._spill, ..._buffer];
    final slice =
        all.length > _maxStored ? all.sublist(all.length - _maxStored) : all;
    await _prefs.setString(_storeKey, jsonEncode(slice));
  }

  List<Map<String, dynamic>> _loadSpillFromDisk() {
    final raw = _prefs.getString(_storeKey);
    if (raw == null || raw.isEmpty) return [];
    // keep key until released/sent; rewrite via persist after load
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        final list = decoded
            .whereType<Map>()
            .map((e) => e.map((k, v) => MapEntry(k.toString(), v)))
            .toList();
        return list.length > _maxStored
            ? list.sublist(list.length - _maxStored)
            : list;
      }
    } catch (_) {
      /* ignore corrupt cache */
    }
    return [];
  }
}
