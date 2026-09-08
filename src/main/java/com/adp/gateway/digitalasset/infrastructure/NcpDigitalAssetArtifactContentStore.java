package com.adp.gateway.digitalasset.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import com.adp.gateway.digitalasset.application.DigitalAssetArtifactContentStore;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactIngestionException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

public final class NcpDigitalAssetArtifactContentStore
    implements DigitalAssetArtifactContentStore, AutoCloseable {

    private static final Pattern CONTENT_ADDRESSED_REFERENCE = Pattern.compile(
        "^handoff/validated/[A-Za-z0-9][A-Za-z0-9._-]{0,127}/"
            + "[A-Za-z0-9][A-Za-z0-9._-]{0,127}/[0-9a-f]{64}\\.json$"
    );

    private final S3Client client;
    private final String bucket;

    public NcpDigitalAssetArtifactContentStore(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public String load(String reference, long maxBytes) {
        validateReference(reference);
        if (maxBytes <= 0 || maxBytes >= Integer.MAX_VALUE) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_SIZE_LIMIT_EXCEEDED");
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(reference)
                .range("bytes=0-" + maxBytes)
                .build();
            ResponseBytes<GetObjectResponse> response = client.getObjectAsBytes(request);
            byte[] bytes = response.asByteArray();
            if (bytes.length > maxBytes) {
                throw rejected("DIGITAL_ASSET_ARTIFACT_SIZE_LIMIT_EXCEEDED");
            }
            verifyContentAddress(reference, bytes);
            return decodeUtf8(bytes);
        } catch (DigitalAssetArtifactIngestionException exception) {
            throw exception;
        } catch (NoSuchKeyException exception) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_OBJECT_NOT_FOUND", exception);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw rejected("DIGITAL_ASSET_ARTIFACT_OBJECT_NOT_FOUND", exception);
            }
            throw rejected("DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE", exception);
        } catch (SdkException exception) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE", exception);
        } catch (RuntimeException exception) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE", exception);
        }
    }

    @Override
    public void close() {
        client.close();
    }

    static void validateReference(String reference) {
        if (reference == null || reference.length() > 512
            || !CONTENT_ADDRESSED_REFERENCE.matcher(reference).matches()) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
        }
    }

    private static void verifyContentAddress(String reference, byte[] bytes) {
        int filenameStart = reference.lastIndexOf('/') + 1;
        String expectedDigest = reference.substring(filenameStart, reference.length() - ".json".length());
        try {
            String actualDigest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
            );
            if (!MessageDigest.isEqual(
                expectedDigest.getBytes(StandardCharsets.US_ASCII),
                actualDigest.getBytes(StandardCharsets.US_ASCII)
            )) {
                throw rejected("DIGITAL_ASSET_ARTIFACT_DIGEST_MISMATCH");
            }
        } catch (NoSuchAlgorithmException exception) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE", exception);
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID", exception);
        }
    }

    private static DigitalAssetArtifactIngestionException rejected(String reasonCode) {
        return new DigitalAssetArtifactIngestionException(reasonCode);
    }

    private static DigitalAssetArtifactIngestionException rejected(String reasonCode, Exception cause) {
        return new DigitalAssetArtifactIngestionException(reasonCode, cause);
    }
}
