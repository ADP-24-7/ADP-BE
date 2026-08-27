package com.adp.gateway.retrieval.infrastructure;

import java.util.List;
import java.util.Optional;

import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.retrieval.domain.RetrievalDatasetScope;
import com.adp.gateway.retrieval.domain.RetrievalField;
import com.adp.gateway.retrieval.domain.RetrievalProfile;
import com.adp.gateway.retrieval.domain.RetrievalProfilePort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcRetrievalProfileAdapter implements RetrievalProfilePort {

    private final JdbcClient jdbcClient;

    public JdbcRetrievalProfileAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<RetrievalProfile> findEnabled(String workloadId, String purpose, String subjectType) {
        return jdbcClient.sql("""
                select profile_id, workload_id, purpose, subject_type
                from retrieval_profile
                where workload_id = :workloadId
                  and purpose = :purpose
                  and subject_type = :subjectType
                  and enabled = true
                """)
            .param("workloadId", workloadId)
            .param("purpose", purpose)
            .param("subjectType", subjectType)
            .query((rs, rowNum) -> {
                String profileId = rs.getString("profile_id");
                return new RetrievalProfile(
                    profileId,
                    rs.getString("workload_id"),
                    rs.getString("purpose"),
                    rs.getString("subject_type"),
                    datasetScopes(profileId),
                    fields(profileId)
                );
            })
            .optional();
    }

    private List<RetrievalDatasetScope> datasetScopes(String profileId) {
        return jdbcClient.sql("""
                select dataset_name, row_limit, time_window_days
                from retrieval_profile_dataset
                where profile_id = :profileId
                order by dataset_name
                """)
            .param("profileId", profileId)
            .query((rs, rowNum) -> {
                Integer timeWindowDays = rs.getObject("time_window_days", Integer.class);
                return new RetrievalDatasetScope(
                    rs.getString("dataset_name"),
                    rs.getInt("row_limit"),
                    timeWindowDays
                );
            })
            .list();
    }

    private List<RetrievalField> fields(String profileId) {
        return jdbcClient.sql("""
                select dataset_name, field_name, data_class
                from retrieval_profile_field
                where profile_id = :profileId
                order by dataset_name, field_name
                """)
            .param("profileId", profileId)
            .query((rs, rowNum) -> new RetrievalField(
                rs.getString("dataset_name"),
                rs.getString("field_name"),
                DataClass.valueOf(rs.getString("data_class"))
            ))
            .list();
    }
}
