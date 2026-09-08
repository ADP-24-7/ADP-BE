package com.adp.gateway.digitalasset.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import com.adp.gateway.digitalasset.application.DigitalAssetArtifactIngestionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class NcpDigitalAssetArtifactContentStoreTests {
    private static final String REFERENCE = "handoff/validated/DA-ARTIFACT/1.0.0/"
        + "a".repeat(64) + ".json";

    @Test
    void loadsOnlyBoundedContentAddressedJsonFromFixedBucket() {
        S3Client client = mock(S3Client.class);
        byte[] content = "{\"status\":\"PASS\"}".getBytes(StandardCharsets.UTF_8);
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
            ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentLength((long) content.length).build(), content
            )
        );
        var store = new NcpDigitalAssetArtifactContentStore(client, "adp-qa-data-artifacts");

        assertThat(store.load(REFERENCE, 1024)).isEqualTo("{\"status\":\"PASS\"}");

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("adp-qa-data-artifacts");
        assertThat(request.getValue().key()).isEqualTo(REFERENCE);
        assertThat(request.getValue().range()).isEqualTo("bytes=0-1024");
    }

    @Test
    void rejectsUnsafeOrNonContentAddressedReferenceBeforeStorageAccess() {
        S3Client client = mock(S3Client.class);
        var store = new NcpDigitalAssetArtifactContentStore(client, "adp-qa-data-artifacts");

        for (String reference : new String[] {
            "../secret.json",
            "https://kr.object.ncloudstorage.com/adp-qa-data-artifacts/object.json",
            "handoff/validated/artifact/1.0.0/manifest.json",
            "handoff/validated/artifact/1.0.0/" + "A".repeat(64) + ".json",
            "manifests/artifacts/artifact/1.0.0/" + "a".repeat(64) + ".json"
        }) {
            assertReason(() -> store.load(reference, 1024), "DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
        }

        verifyNoInteractions(client);
    }

    @Test
    void rejectsOversizedResponseAndMalformedUtf8() {
        S3Client oversizedClient = mock(S3Client.class);
        when(oversizedClient.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
            ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentLength(5L).build(), new byte[5]
            )
        );
        var oversized = new NcpDigitalAssetArtifactContentStore(
            oversizedClient, "adp-qa-data-artifacts"
        );
        assertReason(
            () -> oversized.load(REFERENCE, 4),
            "DIGITAL_ASSET_ARTIFACT_SIZE_LIMIT_EXCEEDED"
        );

        S3Client malformedClient = mock(S3Client.class);
        when(malformedClient.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
            ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentLength(2L).build(),
                new byte[] {(byte) 0xc3, (byte) 0x28}
            )
        );
        var malformed = new NcpDigitalAssetArtifactContentStore(
            malformedClient, "adp-qa-data-artifacts"
        );
        assertReason(
            () -> malformed.load(REFERENCE, 1024),
            "DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID"
        );
    }

    @Test
    void normalizesNotFoundAndTransportFailuresWithoutLeakingSdkMessages() {
        S3Client missingClient = mock(S3Client.class);
        when(missingClient.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(
            S3Exception.builder().statusCode(404).message("provider detail").build()
        );
        assertReason(
            () -> new NcpDigitalAssetArtifactContentStore(missingClient, "adp-qa-data-artifacts")
                .load(REFERENCE, 1024),
            "DIGITAL_ASSET_ARTIFACT_OBJECT_NOT_FOUND"
        );

        S3Client unavailableClient = mock(S3Client.class);
        when(unavailableClient.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(
            SdkClientException.create("credential=must-not-be-returned")
        );
        assertThatThrownBy(() -> new NcpDigitalAssetArtifactContentStore(
            unavailableClient, "adp-qa-data-artifacts"
        ).load(REFERENCE, 1024))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .satisfies(exception -> {
                var failure = (DigitalAssetArtifactIngestionException) exception;
                assertThat(failure.reasonCode()).isEqualTo("DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE");
                assertThat(failure.getMessage()).doesNotContain("must-not-be-returned");
            });
    }

    @Test
    void configurationRejectsNonQaEndpointAndBucket() {
        assertThat(NcpDigitalAssetArtifactStoreConfiguration.validateEndpoint(
            "https://kr.object.ncloudstorage.com"
        )).hasToString("https://kr.object.ncloudstorage.com");

        assertReason(
            () -> NcpDigitalAssetArtifactStoreConfiguration.validateEndpoint(
                "https://example.invalid"
            ),
            "DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE"
        );
        assertReason(
            () -> NcpDigitalAssetArtifactStoreConfiguration.validateBucket("adp-qa-tfstate"),
            "DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE"
        );
        assertReason(
            () -> NcpDigitalAssetArtifactStoreConfiguration.validateBucket("other-bucket"),
            "DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE"
        );
    }

    private void assertReason(Runnable action, String reasonCode) {
        assertThatThrownBy(action::run)
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo(reasonCode);
    }
}
