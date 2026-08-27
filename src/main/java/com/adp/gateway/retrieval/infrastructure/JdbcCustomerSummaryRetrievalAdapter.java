package com.adp.gateway.retrieval.infrastructure;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.adp.gateway.dataaccess.application.DataAccessRequest;
import com.adp.gateway.retrieval.application.PredefinedRetrievalAdapter;
import com.adp.gateway.retrieval.domain.RetrievalProfile;
import com.adp.gateway.retrieval.domain.RetrievalRecord;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcCustomerSummaryRetrievalAdapter implements PredefinedRetrievalAdapter {

    private static final String WORKLOAD_ID = "customer_summary";

    private final JdbcClient jdbcClient;

    public JdbcCustomerSummaryRetrievalAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean supports(String workloadId) {
        return WORKLOAD_ID.equals(workloadId);
    }

    @Override
    public RetrievalResult retrieve(DataAccessRequest request, RetrievalProfile profile) {
        List<RetrievalRecord> records = new ArrayList<>();
        records.add(customerRecord(request, profile));
        records.addAll(accountRecords(request, profile));
        records.addAll(transactionRecords(request, profile));

        return new RetrievalResult(
            null,
            request.workloadId(),
            request.purpose(),
            request.subject().subjectType(),
            request.subject().subjectId(),
            profile.profileId(),
            profile.rowLimit(),
            records.size(),
            profile.fields(),
            records
        );
    }

    private RetrievalRecord customerRecord(DataAccessRequest request, RetrievalProfile profile) {
        Map<String, Object> row = jdbcClient.sql("""
                select customer_id, segment
                from synthetic_customer
                where customer_id = :customerId
                """)
            .param("customerId", request.subject().subjectId())
            .query((rs, rowNum) -> {
                Map<String, Object> fields = new LinkedHashMap<>();
                putIfAllowed(profile, fields, "customer", "customer_id", rs.getString("customer_id"));
                putIfAllowed(profile, fields, "customer", "segment", rs.getString("segment"));
                return fields;
            })
            .single();

        return new RetrievalRecord("customer", row);
    }

    private List<RetrievalRecord> accountRecords(DataAccessRequest request, RetrievalProfile profile) {
        return jdbcClient.sql("""
                select account_id, account_type, balance
                from synthetic_account
                where customer_id = :customerId
                order by opened_at desc, account_id
                limit :rowLimit
                """)
            .param("customerId", request.subject().subjectId())
            .param("rowLimit", profile.rowLimit())
            .query((rs, rowNum) -> {
                Map<String, Object> fields = new LinkedHashMap<>();
                putIfAllowed(profile, fields, "account", "account_id", rs.getString("account_id"));
                putIfAllowed(profile, fields, "account", "account_type", rs.getString("account_type"));
                putIfAllowed(profile, fields, "account", "balance", rs.getBigDecimal("balance"));
                return new RetrievalRecord("account", fields);
            })
            .list();
    }

    private List<RetrievalRecord> transactionRecords(DataAccessRequest request, RetrievalProfile profile) {
        LocalDate since = LocalDate.now().minusDays(profile.timeWindowDays());

        return jdbcClient.sql("""
                select t.transaction_id, t.posted_at, t.merchant_category, t.amount
                from synthetic_transaction t
                join synthetic_account a on a.account_id = t.account_id
                where a.customer_id = :customerId
                  and t.posted_at >= :since
                order by t.posted_at desc, t.transaction_id
                limit :rowLimit
                """)
            .param("customerId", request.subject().subjectId())
            .param("since", since)
            .param("rowLimit", profile.rowLimit())
            .query((rs, rowNum) -> {
                Map<String, Object> fields = new LinkedHashMap<>();
                putIfAllowed(profile, fields, "transaction", "transaction_id", rs.getString("transaction_id"));
                putIfAllowed(profile, fields, "transaction", "posted_at", rs.getDate("posted_at").toLocalDate());
                putIfAllowed(profile, fields, "transaction", "merchant_category", rs.getString("merchant_category"));
                putIfAllowed(profile, fields, "transaction", "amount", rs.getBigDecimal("amount"));
                return new RetrievalRecord("transaction", fields);
            })
            .list();
    }

    private void putIfAllowed(
        RetrievalProfile profile,
        Map<String, Object> fields,
        String datasetName,
        String fieldName,
        Object value
    ) {
        if (profile.allowsField(datasetName, fieldName)) {
            fields.put(fieldName, value);
        }
    }
}
