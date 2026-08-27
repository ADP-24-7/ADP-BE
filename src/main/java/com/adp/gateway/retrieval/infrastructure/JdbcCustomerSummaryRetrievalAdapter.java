package com.adp.gateway.retrieval.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.adp.gateway.dataaccess.application.DataAccessRequest;
import com.adp.gateway.retrieval.application.PredefinedRetrievalAdapter;
import com.adp.gateway.retrieval.domain.RetrievalDatasetScope;
import com.adp.gateway.retrieval.domain.RetrievalField;
import com.adp.gateway.retrieval.domain.RetrievalProfile;
import com.adp.gateway.retrieval.domain.RetrievalRecord;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcCustomerSummaryRetrievalAdapter implements PredefinedRetrievalAdapter {

    private static final String WORKLOAD_ID = "customer_summary";
    private static final Map<String, Map<String, FieldBinding>> FIELD_CATALOG = Map.of(
        "customer", Map.of(
            "customer_id", stringField("customer_id"),
            "segment", stringField("segment")
        ),
        "account", Map.of(
            "account_id", stringField("account_id"),
            "account_type", stringField("account_type"),
            "balance", decimalField("balance")
        ),
        "transaction", Map.of(
            "transaction_id", stringField("t.transaction_id"),
            "posted_at", dateField("t.posted_at"),
            "merchant_category", stringField("t.merchant_category"),
            "amount", decimalField("t.amount")
        )
    );

    private final JdbcClient jdbcClient;
    private final Clock clock;

    public JdbcCustomerSummaryRetrievalAdapter(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Override
    public boolean supports(String workloadId) {
        return WORKLOAD_ID.equals(workloadId);
    }

    @Override
    public RetrievalResult retrieve(DataAccessRequest request, RetrievalProfile profile) {
        List<RetrievalRecord> records = new ArrayList<>();
        customerRecord(request, profile).ifPresent(records::add);
        records.addAll(accountRecords(request, profile));
        records.addAll(transactionRecords(request, profile));

        return new RetrievalResult(
            null,
            request.workloadId(),
            request.purpose(),
            request.subject().subjectType(),
            request.subject().subjectId(),
            profile.profileId(),
            records.size(),
            profile.datasetScopes(),
            profile.fields(),
            records
        );
    }

    private Optional<RetrievalRecord> customerRecord(DataAccessRequest request, RetrievalProfile profile) {
        List<String> fields = allowedFields(profile, "customer");
        Optional<RetrievalDatasetScope> scope = profile.scopeFor("customer");
        if (fields.isEmpty() || scope.isEmpty()) {
            return Optional.empty();
        }

        String sql = """
                select %s
                from synthetic_customer
                where customer_id = :customerId
                limit :rowLimit
                """.formatted(selectList("customer", fields));

        return jdbcClient.sql(sql)
            .param("customerId", request.subject().subjectId())
            .param("rowLimit", scope.orElseThrow().rowLimit())
            .query((rs, rowNum) -> new RetrievalRecord("customer", row("customer", fields, rs)))
            .optional();
    }

    private List<RetrievalRecord> accountRecords(DataAccessRequest request, RetrievalProfile profile) {
        List<String> fields = allowedFields(profile, "account");
        Optional<RetrievalDatasetScope> scope = profile.scopeFor("account");
        if (fields.isEmpty() || scope.isEmpty()) {
            return List.of();
        }

        String sql = """
                select %s
                from synthetic_account
                where customer_id = :customerId
                order by opened_at desc, account_id
                limit :rowLimit
                """.formatted(selectList("account", fields));

        return jdbcClient.sql(sql)
            .param("customerId", request.subject().subjectId())
            .param("rowLimit", scope.orElseThrow().rowLimit())
            .query((rs, rowNum) -> new RetrievalRecord("account", row("account", fields, rs)))
            .list();
    }

    private List<RetrievalRecord> transactionRecords(DataAccessRequest request, RetrievalProfile profile) {
        List<String> fields = allowedFields(profile, "transaction");
        Optional<RetrievalDatasetScope> scope = profile.scopeFor("transaction");
        if (fields.isEmpty() || scope.isEmpty()) {
            return List.of();
        }

        String sql = """
                select %s
                from synthetic_transaction t
                join synthetic_account a on a.account_id = t.account_id
                where a.customer_id = :customerId
                  and t.posted_at >= :since
                order by t.posted_at desc, t.transaction_id
                limit :rowLimit
                """.formatted(selectList("transaction", fields));

        return jdbcClient.sql(sql)
            .param("customerId", request.subject().subjectId())
            .param("since", LocalDate.now(clock).minusDays(scope.orElseThrow().timeWindowDays()))
            .param("rowLimit", scope.orElseThrow().rowLimit())
            .query((rs, rowNum) -> new RetrievalRecord("transaction", row("transaction", fields, rs)))
            .list();
    }

    private List<String> allowedFields(RetrievalProfile profile, String datasetName) {
        Set<String> catalogFields = FIELD_CATALOG.getOrDefault(datasetName, Map.of()).keySet();
        return profile.fieldsFor(datasetName).stream()
            .map(RetrievalField::fieldName)
            .filter(catalogFields::contains)
            .toList();
    }

    private String selectList(String datasetName, List<String> fields) {
        Map<String, FieldBinding> catalog = FIELD_CATALOG.get(datasetName);
        return fields.stream()
            .map(field -> catalog.get(field).columnExpression() + " as " + field)
            .collect(Collectors.joining(", "));
    }

    private Map<String, Object> row(String datasetName, List<String> fields, ResultSet rs) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, FieldBinding> catalog = FIELD_CATALOG.get(datasetName);
        for (String field : fields) {
            values.put(field, catalog.get(field).reader().apply(rs, field));
        }
        return values;
    }

    private static FieldBinding stringField(String columnExpression) {
        return new FieldBinding(columnExpression, (rs, field) -> {
            try {
                return rs.getString(field);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static FieldBinding decimalField(String columnExpression) {
        return new FieldBinding(columnExpression, (rs, field) -> {
            try {
                return rs.getBigDecimal(field);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static FieldBinding dateField(String columnExpression) {
        return new FieldBinding(columnExpression, (rs, field) -> {
            try {
                return rs.getDate(field).toLocalDate();
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private record FieldBinding(
        String columnExpression,
        BiFunction<ResultSet, String, Object> reader
    ) {
    }
}
