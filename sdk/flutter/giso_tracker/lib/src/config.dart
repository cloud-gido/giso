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
    this.osVersion = '',
    this.devBrand = '',
    this.devModel = '',
    this.screenRes = '',
    this.netType = 'unknown',
    this.lang = '',
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
  final String osVersion;
  final String devBrand;
  final String devModel;
  final String screenRes;
  final String netType;
  final String lang;
  final String tz;

  String resolvedEnv() => env ?? (debug ? 'test' : 'prod');
}
