import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

const _didKey = 'giso_did';
const _bizDidKey = 'demo_biz_did';

class DebugPanel extends StatefulWidget {
  const DebugPanel({
    super.key,
    required this.pgid,
    required this.endpoint,
    required this.appKey,
  });

  final String pgid;
  final String endpoint;
  final String appKey;

  @override
  State<DebugPanel> createState() => _DebugPanelState();
}

class _DebugPanelState extends State<DebugPanel> {
  String _did = '';
  String _bizDid = '';
  String _gatewayStatus = '网关: 检测中…';
  bool _gatewayOk = false;

  @override
  void initState() {
    super.initState();
    _loadIds();
    _probeGateway();
  }

  @override
  void didUpdateWidget(covariant DebugPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.pgid != widget.pgid) {
      setState(() {});
    }
  }

  Future<void> _loadIds() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _did = prefs.getString(_didKey) ?? '';
      _bizDid = prefs.getString(_bizDidKey) ?? '';
    });
  }

  Future<void> _probeGateway() async {
    final configUrl =
        widget.endpoint.replaceFirst(RegExp(r'/v1/track/?$'), '/v1/config');
    try {
      final resp =
          await http.get(Uri.parse(configUrl)).timeout(const Duration(seconds: 8));
      setState(() {
        _gatewayOk = resp.statusCode == 200;
        _gatewayStatus =
            _gatewayOk ? '网关: 已连通 ✓' : '网关: HTTP ${resp.statusCode} ✗';
      });
    } catch (_) {
      final remote = widget.endpoint.startsWith('https://');
      setState(() {
        _gatewayOk = false;
        _gatewayStatus = remote
            ? '网关: 不可达 ✗（检查网络或 App Key 白名单）'
            : '网关: 不可达 ✗（手机与电脑须同一 Wi-Fi）';
      });
    }
  }

  void _copyDid() {
    if (_did.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('did 尚未生成，请先打开任意页面')),
      );
      return;
    }
    Clipboard.setData(ClipboardData(text: _did));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('已复制 did')),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.black.withValues(alpha: 0.88),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    'did: ${_did.isEmpty ? '（启动后生成）' : _did}',
                    style: TextStyle(color: Colors.grey.shade300, fontSize: 11),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                TextButton(
                  onPressed: _copyDid,
                  child: const Text('复制 did', style: TextStyle(fontSize: 12)),
                ),
              ],
            ),
            Text(
              'biz_did: ${_bizDid.isEmpty ? '（未设置）' : _bizDid}',
              style: TextStyle(color: Colors.grey.shade300, fontSize: 11),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            Text(
              'pgid: ${widget.pgid} · sdk=1.0.8',
              style: TextStyle(color: Colors.grey.shade400, fontSize: 11),
            ),
            Text(
              'app_key: ${widget.appKey}',
              style: TextStyle(color: Colors.grey.shade400, fontSize: 11),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            Text(
              _gatewayStatus,
              style: TextStyle(
                color: _gatewayOk ? Colors.lightGreenAccent : Colors.redAccent,
                fontSize: 11,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
