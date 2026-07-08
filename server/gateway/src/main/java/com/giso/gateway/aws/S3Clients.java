package com.giso.gateway.aws;

import com.giso.gateway.GatewayConfig;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/** 共享 S3 客户端工厂（IRSA / 静态 AK 均支持）。 */
public final class S3Clients {
    private S3Clients() { }

    public static S3Client create(GatewayConfig config) {
        var builder = S3Client.builder().region(Region.of(config.s3Region));
        if (config.s3Endpoint != null && !config.s3Endpoint.isBlank()) {
            builder = builder.endpointOverride(URI.create(config.s3Endpoint.trim()));
            builder = builder.forcePathStyle(true);
        }
        if (config.s3AccessKey != null && !config.s3AccessKey.isBlank()
                && config.s3SecretKey != null && !config.s3SecretKey.isBlank()) {
            builder = builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.s3AccessKey, config.s3SecretKey)));
        } else {
            builder = builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    public static String normalizePrefix(String p) {
        if (p == null || p.isBlank()) return "";
        return p.endsWith("/") ? p : p + "/";
    }
}
