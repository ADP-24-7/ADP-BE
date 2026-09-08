package com.adp.gateway.digitalasset.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-user-auth.enabled=true",
    "adp.digital-asset.artifact-store.type=local",
    "adp.digital-asset.artifact-store.local-root=."
})
@AutoConfigureMockMvc
class DigitalAssetArtifactControllerTests {
    private static final String ARTIFACT_ID = "DA-DIGITAL-ASSET-RUNTIME-CANDIDATE-001";
    private static final String DIGEST = "sha256:dfffbdbf0c0bc65a4adc89e6a3a4e6cfd171db52182bf2b7ba4c3df54781e76c";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void ingestsValidatedBundleAsLifecycleCandidateAndReplaysSameIdentity() throws Exception {
        String request = """
            {"manifestReference":"docs/contracts/artifacts/p0-5-sample/manifest.json",
             "expectedContentDigest":"%s"}
            """.formatted(DIGEST);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/admin/digital-assets/artifacts/ingestions")
                    .header("X-ADP-User-Id", "artifact-maker")
                    .header("X-ADP-User-Roles", "OPERATOR")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.artifactId").value(ARTIFACT_ID))
                .andExpect(jsonPath("$.lifecycleStage").value("CANDIDATE"))
                .andExpect(jsonPath("$.fileCount").value(5));
        }

        mockMvc.perform(get("/api/admin/digital-assets/artifacts/{id}/versions/1.0.0", ARTIFACT_ID)
                .header("X-ADP-User-Id", "artifact-auditor")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artifactDigest").value(DIGEST.substring("sha256:".length())));

        Integer artifactRows = jdbcClient.sql("""
            select count(*) from policy.digital_asset_artifact_ingestion where artifact_id = :artifactId
            """).param("artifactId", ARTIFACT_ID).query(Integer.class).single();
        Integer transitionRows = jdbcClient.sql("""
            select count(*) from policy.lifecycle_transition_event where artifact_id = :artifactId
            """).param("artifactId", ARTIFACT_ID).query(Integer.class).single();
        assertThat(artifactRows).isEqualTo(1);
        assertThat(transitionRows).isEqualTo(2);
    }
}
