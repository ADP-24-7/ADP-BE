package com.adp.gateway.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.ai.application.AiModelProfileCatalog;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.egress.domain.DestinationBinding;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.egress.domain.FieldTreatment;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiExternalSchemaMapperTests {
    private final AiModelProfileCatalog catalog = new AiModelProfileCatalog();
    private final AiExternalSchemaMapper mapper = new AiExternalSchemaMapper(
        new ObjectMapper(), new CanonicalValueHasher(), catalog
    );

    @Test
    void exposesExactlyThreeServerOwnedEvaluationProfiles() {
        assertThat(catalog.profiles())
            .extracting(profile -> profile.modelId())
            .containsExactly(
                "nvidia/nemotron-3.5-lightning-30b-a3b",
                "meta/muse-glimmer-30b",
                "google/gemma-4-31b-it"
            );
    }

    @Test
    void mapsCatalogModelAndFixedEvaluationParametersIntoNvidiaRequest() {
        var profile = catalog.profiles().getFirst();
        var request = mapper.map(destination(profile.profileId(), profile.destinationProfileId()), outbound());

        assertThat(request.providerProfileId()).isEqualTo(profile.profileId());
        assertThat(request.payload())
            .containsEntry("model", profile.modelId())
            .containsEntry("max_tokens", 512)
            .containsEntry("temperature", 0.0)
            .containsEntry("stream", false);
        assertThat(request.payload().keySet())
            .containsExactlyInAnyOrder("model", "messages", "max_tokens", "temperature", "stream");
        assertThat(request.toString()).doesNotContain("approved context");
    }

    private DestinationProfile destination(String profileId, String destinationId) {
        return new DestinationProfile(
            destinationId, AiModelProfileCatalog.PROFILE_VERSION, "digest", "nvidia-nim-chat-completions",
            profileId, ExecutionPackType.AI, "ai-provider-response/v1", "tenant", "NVIDIA_HOSTED",
            "PROVIDER_CONTROLLED", false, "ACTIVE", OffsetDateTime.parse("2026-09-07T00:00:00Z"), null,
            List.of(new DestinationBinding("customer_summary", "CUSTOMER_SUPPORT")), List.of()
        );
    }

    private OutboundCandidatePayload outbound() {
        return new OutboundCandidatePayload(
            "out", "destination", "version", "digest", ExecutionPackType.AI, "schema", "candidate-digest",
            List.of(new OutboundCandidateField(
                "$.input.prompt", DataClass.BUSINESS_METADATA, TransformStrategy.KEEP,
                FieldObligation.CONDITIONAL_EXACT, FieldTreatment.KEEP_EXACT_PROTECTED,
                "value-digest", List.of(), "approved context"
            ))
        );
    }
}

