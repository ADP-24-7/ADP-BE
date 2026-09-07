package com.adp.gateway.ai.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.adp.gateway.ai.domain.AiModelProfile;
import org.springframework.stereotype.Component;

@Component
public class AiModelProfileCatalog {
    public static final String PROFILE_VERSION = "2026-09-07";

    private static final List<AiModelProfile> PROFILES = List.of(
        profile("nvidia-nemotron-3.5-lightning-30b-a3b", "nvidia/nemotron-3.5-lightning-30b-a3b"),
        profile("meta-muse-glimmer-30b", "meta/muse-glimmer-30b"),
        profile("google-gemma-4-31b-it", "google/gemma-4-31b-it")
    );
    private static final Map<String, AiModelProfile> BY_PROFILE_ID = PROFILES.stream()
        .collect(Collectors.toUnmodifiableMap(AiModelProfile::profileId, Function.identity()));
    private static final Map<String, AiModelProfile> BY_DESTINATION_ID = PROFILES.stream()
        .collect(Collectors.toUnmodifiableMap(AiModelProfile::destinationProfileId, Function.identity()));

    public List<AiModelProfile> profiles() {
        return PROFILES;
    }

    public Optional<AiModelProfile> findByProfileId(String profileId) {
        return profileId == null ? Optional.empty() : Optional.ofNullable(BY_PROFILE_ID.get(profileId));
    }

    public Optional<AiModelProfile> findByDestinationProfileId(String destinationProfileId) {
        return destinationProfileId == null
            ? Optional.empty()
            : Optional.ofNullable(BY_DESTINATION_ID.get(destinationProfileId));
    }

    public String approvalReference(AiModelProfile profile) {
        return "approval_ai_eval_" + profile.profileId();
    }

    public String policySnapshotDigest(AiModelProfile profile) {
        return "be-snapshot-local-fixture:customer-summary:customer-support:" + profile.profileId();
    }

    private static AiModelProfile profile(String profileId, String modelId) {
        String destinationProfileId = "dest_" + profileId.replace('.', '-');
        return new AiModelProfile(
            profileId,
            PROFILE_VERSION,
            modelId,
            PROFILE_VERSION,
            destinationProfileId,
            PROFILE_VERSION,
            "sha256:local-ai-eval-profile:" + profileId + ":" + PROFILE_VERSION,
            512,
            0.0
        );
    }
}
