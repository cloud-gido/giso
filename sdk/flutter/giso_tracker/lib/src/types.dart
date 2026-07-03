/// Protocol types aligned with docs/tracking/02-上报协议规范.md.

typedef Params = Map<String, Object?>;
typedef Passthrough = Map<String, dynamic>;

class CommonParams {
  const CommonParams({
    required this.appId,
    required this.platform,
    required this.appVersion,
    required this.sdkVersion,
    required this.did,
    required this.uid,
    required this.sessionId,
    required this.channel,
    required this.env,
    required this.osVersion,
    required this.devBrand,
    required this.devModel,
    required this.screenRes,
    required this.netType,
    required this.lang,
    required this.tz,
  });

  final String appId;
  final String platform;
  final String appVersion;
  final String sdkVersion;
  final String did;
  final String uid;
  final String sessionId;
  final String channel;
  final String env;
  final String osVersion;
  final String devBrand;
  final String devModel;
  final String screenRes;
  final String netType;
  final String lang;
  final String tz;

  Map<String, Object?> toJson() => {
        'app_id': appId,
        'platform': platform,
        'app_vrsn': appVersion,
        'sdk_vrsn': sdkVersion,
        'did': did,
        'uid': uid,
        'session_id': sessionId,
        'channel': channel,
        'env': env,
        'os_vrsn': osVersion,
        'dev_brand': devBrand,
        'dev_model': devModel,
        'screen_res': screenRes,
        'net_type': netType,
        'lang': lang,
        'tz': tz,
      };
}

class PageContext {
  const PageContext({
    required this.pgid,
    this.pgParams,
    this.refPgid,
    this.refEid,
    this.pgStay,
  });

  final String pgid;
  final Params? pgParams;
  final String? refPgid;
  final String? refEid;
  final int? pgStay;

  Map<String, Object?> toJson() {
    final m = <String, Object?>{'pgid': pgid};
    if (pgParams != null && pgParams!.isNotEmpty) m['pg_params'] = pgParams;
    if (refPgid != null && refPgid!.isNotEmpty) m['ref_pgid'] = refPgid;
    if (refEid != null && refEid!.isNotEmpty) m['ref_eid'] = refEid;
    if (pgStay != null) m['pg_stay'] = pgStay;
    return m;
  }
}

class ElementContext {
  const ElementContext({
    required this.eid,
    this.mod,
    this.pos,
    this.params,
    this.expDur,
    this.expRatio,
  });

  final String eid;
  final String? mod;
  final int? pos;
  final Params? params;
  final int? expDur;
  final double? expRatio;

  Map<String, Object?> toJson() {
    final m = <String, Object?>{'eid': eid};
    if (mod != null) m['mod'] = mod;
    if (pos != null) m['pos'] = pos;
    if (params != null && params!.isNotEmpty) m['params'] = params;
    if (expDur != null) m['exp_dur'] = expDur;
    if (expRatio != null) m['exp_ratio'] = expRatio;
    return m;
  }
}

class BizContext {
  const BizContext({required this.code, this.params});

  final String code;
  final Params? params;

  Map<String, Object?> toJson() {
    final m = <String, Object?>{'code': code};
    if (params != null && params!.isNotEmpty) m['params'] = params;
    return m;
  }
}
