package com.giso.tracker;

/** SDK 配置。曝光阈值等口径参数集中于此，支持远程配置覆盖。 */
public final class TrackerConfig {
    public final String appId;
    public final String appVersion;
    public final String endpoint;
    public final String channel;
    public final boolean debug;
    /** prod=生产 test=联调；未显式设置时 debug=true → test，否则 prod */
    public final String env;

    /** 曝光判定：可视面积比例阈值 */
    public final float exposureRatio;
    /** 曝光判定：持续时长 ms */
    public final long exposureDurationMs;
    /** 同一元素实例单次页面进入内最大曝光次数 */
    public final int exposureMaxPerPage;
    /** 攒批条数 */
    public final int batchSize;
    /** 攒批最大间隔 ms */
    public final long flushIntervalMs;

    private TrackerConfig(Builder b) {
        this.appId = b.appId;
        this.appVersion = b.appVersion;
        this.endpoint = b.endpoint;
        this.channel = b.channel;
        this.debug = b.debug;
        this.env = b.env != null ? b.env : (b.debug ? "test" : "prod");
        this.exposureRatio = b.exposureRatio;
        this.exposureDurationMs = b.exposureDurationMs;
        this.exposureMaxPerPage = b.exposureMaxPerPage;
        this.batchSize = b.batchSize;
        this.flushIntervalMs = b.flushIntervalMs;
    }

    public static Builder builder(String appId, String appVersion, String endpoint) {
        return new Builder(appId, appVersion, endpoint);
    }

    public static final class Builder {
        private final String appId;
        private final String appVersion;
        private final String endpoint;
        private String channel = "";
        private boolean debug = false;
        private String env = null;
        private float exposureRatio = 0.5f;
        private long exposureDurationMs = 500L;
        private int exposureMaxPerPage = 3;
        private int batchSize = 20;
        private long flushIntervalMs = 15_000L;

        private Builder(String appId, String appVersion, String endpoint) {
            this.appId = appId;
            this.appVersion = appVersion;
            this.endpoint = endpoint;
        }

        public Builder channel(String channel) { this.channel = channel; return this; }
        public Builder debug(boolean debug) { this.debug = debug; return this; }
        public Builder env(String env) { this.env = env; return this; }
        public Builder exposureRatio(float ratio) { this.exposureRatio = ratio; return this; }
        public Builder exposureDurationMs(long ms) { this.exposureDurationMs = ms; return this; }
        public Builder exposureMaxPerPage(int n) { this.exposureMaxPerPage = n; return this; }
        public Builder batchSize(int n) { this.batchSize = n; return this; }
        public Builder flushIntervalMs(long ms) { this.flushIntervalMs = ms; return this; }

        public TrackerConfig build() { return new TrackerConfig(this); }
    }
}
