/// SDK initialization configuration.
class GisoConfig {
  const GisoConfig({
    required this.appId,
    required this.appKey,
    required this.appVersion,
    required this.endpoint,
    this.platform,
    this.channel = '',
    this.debug = false,
    this.env,
    this.batchSize = 20,
    this.flushIntervalMs = 15000,
    this.heartbeatIntervalMs = 60000,
    this.osVersion = '',
    this.devBrand = '',
    this.devModel = '',
    this.screenRes = '',
    this.netType = '',
    this.lang = '',
    this.appPkg = '',
    this.tz = '',
  });

  /// Same as [appKey] for protocol header `X-App-Key`.
  final String appId;

  /// App Key sent as `X-App-Key`; defaults to [appId] when equal.
  final String appKey;
  final String appVersion;
  final String endpoint;

  /// `android` or `ios`; auto-detected when null.
  final String? platform;
  final String channel;
  final bool debug;

  /// `test` or `prod`; defaults to test when [debug] is true.
  final String? env;
  final int batchSize;
  final int flushIntervalMs;

  /// Foreground app heartbeat interval in ms (default 60s).
  final int heartbeatIntervalMs;

  /// Overrides auto-detected OS version when non-empty.
  final String osVersion;

  /// Overrides auto-detected device brand when non-empty.
  final String devBrand;

  /// Overrides auto-detected device model when non-empty.
  final String devModel;

  /// Overrides auto-detected logical screen size when non-empty.
  final String screenRes;

  /// Overrides auto-detected network (`wifi` / `cellular` / `none`) when non-empty.
  final String netType;

  /// Overrides auto-detected locale when non-empty.
  final String lang;

  /// Overrides auto-detected package name when non-empty.
  final String appPkg;
  final String tz;

  String resolvedEnv() => env ?? (debug ? 'test' : 'prod');
}
