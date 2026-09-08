package com.adp.gateway.digitalasset.infrastructure;

import java.net.URI;
import java.time.Duration;

import com.adp.gateway.digitalasset.application.DigitalAssetArtifactIngestionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "adp.digital-asset.artifact-store.type", havingValue = "ncp")
class NcpDigitalAssetArtifactStoreConfiguration {
    static final String QA_ENDPOINT = "https://kr.object.ncloudstorage.com";
    static final String QA_BUCKET = "adp-qa-data-artifacts";

    @Bean(destroyMethod = "close")
    NcpDigitalAssetArtifactContentStore ncpDigitalAssetArtifactContentStore(
        @Value("${adp.digital-asset.artifact-store.ncp.endpoint}") String endpoint,
        @Value("${adp.digital-asset.artifact-store.ncp.region}") String region,
        @Value("${adp.digital-asset.artifact-store.ncp.bucket}") String bucket,
        @Value("${adp.digital-asset.artifact-store.ncp.access-key}") String accessKey,
        @Value("${adp.digital-asset.artifact-store.ncp.secret-key}") String secretKey,
        @Value("${adp.digital-asset.artifact-store.ncp.connect-timeout}") Duration connectTimeout,
        @Value("${adp.digital-asset.artifact-store.ncp.read-timeout}") Duration readTimeout
    ) {
        URI endpointUri = validateEndpoint(endpoint);
        validateBucket(bucket);
        validateSecret("NCLOUD_ACCESS_KEY", accessKey);
        validateSecret("NCLOUD_SECRET_KEY", secretKey);
        if (region == null || region.isBlank() || region.contains("\n") || region.contains("\r")) {
            throw unavailable();
        }
        if (connectTimeout.isZero() || connectTimeout.isNegative()
            || readTimeout.isZero() || readTimeout.isNegative()) {
            throw unavailable();
        }

        S3Client client = S3Client.builder()
            .endpointOverride(endpointUri)
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .httpClient(UrlConnectionHttpClient.builder()
                .connectionTimeout(connectTimeout)
                .socketTimeout(readTimeout)
                .build())
            .build();
        return new NcpDigitalAssetArtifactContentStore(client, bucket);
    }

    static URI validateEndpoint(String endpoint) {
        try {
            URI value = URI.create(endpoint);
            if (!QA_ENDPOINT.equals(value.toString()) || value.getUserInfo() != null
                || value.getQuery() != null || value.getFragment() != null) {
                throw unavailable();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw unavailable(exception);
        }
    }

    static void validateBucket(String bucket) {
        if (!QA_BUCKET.equals(bucket) || bucket.toLowerCase(java.util.Locale.ROOT).contains("tfstate")) {
            throw unavailable();
        }
    }

    private static void validateSecret(String name, String value) {
        if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r")) {
            throw new DigitalAssetArtifactIngestionException(
                "DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE",
                new IllegalStateException(name + " is not configured")
            );
        }
    }

    private static DigitalAssetArtifactIngestionException unavailable() {
        return new DigitalAssetArtifactIngestionException("DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE");
    }

    private static DigitalAssetArtifactIngestionException unavailable(Exception cause) {
        return new DigitalAssetArtifactIngestionException("DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE", cause);
    }
}
