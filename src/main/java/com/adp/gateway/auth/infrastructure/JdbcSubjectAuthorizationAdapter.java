package com.adp.gateway.auth.infrastructure;

import com.adp.gateway.auth.application.SubjectAuthorizationPort;
import com.adp.gateway.auth.domain.RuntimeAction;
import com.adp.gateway.auth.domain.SubjectRef;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcSubjectAuthorizationAdapter implements SubjectAuthorizationPort {

    private final JdbcClient jdbcClient;

    public JdbcSubjectAuthorizationAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean canAccess(
        String principalId,
        String workloadId,
        RuntimeAction action,
        String purpose,
        SubjectRef subject
    ) {
        Integer grantCount = jdbcClient.sql("""
                select count(*)
                from auth_subject_grant
                where principal_id = :principalId
                  and workload_id = :workloadId
                  and action_name = :actionName
                  and purpose = :purpose
                  and subject_type = :subjectType
                  and subject_id = :subjectId
                """)
            .param("principalId", principalId)
            .param("workloadId", workloadId)
            .param("actionName", action.name())
            .param("purpose", purpose)
            .param("subjectType", subject.subjectType())
            .param("subjectId", subject.subjectId())
            .query(Integer.class)
            .single();

        return grantCount > 0;
    }
}
