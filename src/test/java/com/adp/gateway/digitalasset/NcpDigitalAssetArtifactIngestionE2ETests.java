package com.adp.gateway.digitalasset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@EnabledIfEnvironmentVariable(named = "ADP_NCP_ARTIFACT_INGEST_CONFIRM", matches = "YES")
@SpringBootTest(properties = {
    "adp.local-user-auth.enabled=true",
    "adp.local-fixtures.enabled=true",
    "adp.digital-asset.artifact-store.type=ncp"
})
@AutoConfigureMockMvc
class NcpDigitalAssetArtifactIngestionE2ETests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void ingestsDaPublishedNcpBundleThroughTrustedBeValidation() throws Exception {
        String manifestReference = required("ADP_NCP_MANIFEST_REFERENCE");
        String expectedDigest = required("ADP_NCP_EXPECTED_CONTENT_DIGEST");
        String request = """
            {"manifestReference":"%s","expectedContentDigest":"%s"}
            """.formatted(manifestReference, expectedDigest);

        String response = mockMvc.perform(post("/api/admin/digital-assets/artifacts/ingestions")
                .header("X-ADP-User-Id", "ncp-artifact-maker")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lifecycleStage").value("CANDIDATE"))
            .andExpect(jsonPath("$.artifactDigest").value(expectedDigest.substring("sha256:".length())))
            .andExpect(jsonPath("$.fileCount").value(5))
            .andReturn().getResponse().getContentAsString();

        String artifactId = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(response).path("artifactId").asText();
        String artifactVersion = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(response).path("artifactVersion").asText();

        mockMvc.perform(get("/api/admin/digital-assets/artifacts/{id}/versions/{version}",
                artifactId, artifactVersion)
                .header("X-ADP-User-Id", "ncp-artifact-auditor")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifestReference").value(manifestReference))
            .andExpect(jsonPath("$.artifactDigest").value(expectedDigest.substring("sha256:".length())))
            .andExpect(jsonPath("$.lifecycleStage").value("CANDIDATE"));

        Integer ingestionCount = jdbcClient.sql("""
            select count(*) from policy.digital_asset_artifact_ingestion
            where institution_id = 'institution_local'
              and artifact_id = :artifactId
              and artifact_version = :artifactVersion
              and artifact_digest = :artifactDigest
            """)
            .param("artifactId", artifactId)
            .param("artifactVersion", artifactVersion)
            .param("artifactDigest", expectedDigest.substring("sha256:".length()))
            .query(Integer.class).single();
        assertThat(ingestionCount).isEqualTo(1);
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
