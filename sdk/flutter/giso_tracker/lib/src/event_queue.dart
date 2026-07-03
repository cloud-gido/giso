import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

const _storeKey = 'giso_tracker_queue';
const _maxStored = 500;
const _maxBackoffMs = 60000;

/// Batch queue with gzip upload, disk fallback, and exponential backoff.
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
    _buffer.addAll(_restore());
  }

  final String endpoint;
  final String appKey;
  final bool debug;
  final SharedPreferences _prefs;

  final List<Map<String, dynamic>> _buffer = [];
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
      _buffer.add(event);
      unawaited(flush());
      return;
    }
    _buffer.add(event);
    if (_buffer.length >= _batchSize) {
      unawaited(flush());
    } else {
      _timer ??= Timer(Duration(milliseconds: _flushIntervalMs), () {
        _timer = null;
        unawaited(flush());
      });
    }
  }

  Future<void> flush() async {
    _timer?.cancel();
    _timer = null;
    if (_buffer.isEmpty || _sending) return;

    final batch = <Map<String, dynamic>>[];
    while (batch.length < _batchSize && _buffer.isNotEmpty) {
      batch.add(_buffer.removeAt(0));
    }
    if (batch.isEmpty) return;

    _sending = true;
    try {
      final status = await _send(batch);
      if (status >= 200 && status < 500 && status != 429) {
        _backoffMs = 1000;
        if (_buffer.length >= _batchSize) {
          await flush();
        }
      } else {
        _buffer.insertAll(0, batch);
        await _persist();
        await Future<void>.delayed(Duration(milliseconds: _backoffMs));
        _backoffMs = (_backoffMs * 2).clamp(1000, _maxBackoffMs);
        if (_buffer.isNotEmpty) await flush();
      }
    } finally {
      _sending = false;
    }
  }

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
          .timeout(const Duration(seconds: 15));
      return resp.statusCode;
    } catch (_) {
      return -1;
    }
  }

  Future<void> _persist() async {
    final slice = _buffer.length > _maxStored
        ? _buffer.sublist(_buffer.length - _maxStored)
        : List<Map<String, dynamic>>.from(_buffer);
    await _prefs.setString(_storeKey, jsonEncode(slice));
  }

  List<Map<String, dynamic>> _restore() {
    final raw = _prefs.getString(_storeKey);
    if (raw == null || raw.isEmpty) return [];
    _prefs.remove(_storeKey);
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        return decoded
            .whereType<Map>()
            .map((e) => e.map((k, v) => MapEntry(k.toString(), v)))
            .toList();
      }
    } catch (_) {
      /* ignore corrupt cache */
    }
    return [];
  }
}
