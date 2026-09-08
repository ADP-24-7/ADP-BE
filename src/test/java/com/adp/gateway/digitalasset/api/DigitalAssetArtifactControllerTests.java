package com.adp.gateway.digitalasset.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    private static final String DIGEST = "sha256:2a0fad69b2db5f8e18081fadbdb71e5c0af2c12ea436e25276f5b8fa693d468f";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void concurrentlyIngestsSameIdentityAsOneLifecycleCandidateAndOneReplay() throws Exception {
        String request = """
            {"manifestReference":"docs/contracts/artifacts/p0-5-sample/manifest.json",
             "expectedContentDigest":"%s"}
            """.formatted(DIGEST);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var requestTask = (java.util.concurrent.Callable<Void>) () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            mockMvc.perform(post("/api/admin/digital-assets/artifacts/ingestions")
                    .header("X-ADP-User-Id", "artifact-maker")
                    .header("X-ADP-User-Roles", "OPERATOR")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.artifactId").value(ARTIFACT_ID))
                .andExpect(jsonPath("$.lifecycleStage").value("CANDIDATE"))
                .andExpect(jsonPath("$.fileCount").value(5));
            return null;
        };
        try {
            var first = executor.submit(requestTask);
            var second = executor.submit(requestTask);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
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

    @Test
    void auditorCannotIngestAtSecurityFilter() throws Exception {
        mockMvc.perform(post("/api/admin/digital-assets/artifacts/ingestions")
                .header("X-ADP-User-Id", "artifact-auditor")
                .header("X-ADP-User-Roles", "AUDITOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }
}
