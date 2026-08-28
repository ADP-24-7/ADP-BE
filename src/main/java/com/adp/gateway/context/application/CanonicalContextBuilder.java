package com.adp.gateway.context.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.retrieval.domain.RetrievalField;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import org.springframework.stereotype.Service;

@Service
public class CanonicalContextBuilder {

    private final CanonicalValueHasher canonicalValueHasher;
    private final SubjectRefHasher subjectRefHasher;

    public CanonicalContextBuilder(
        CanonicalValueHasher canonicalValueHasher,
        SubjectRefHasher subjectRefHasher
    ) {
        this.canonicalValueHasher = canonicalValueHasher;
        this.subjectRefHasher = subjectRefHasher;
    }

    public CanonicalContext build(RetrievalResult result) {
        Map<String, RetrievalField> selectedFields = result.selectedFields().stream()
            .collect(Collectors.toMap(RetrievalField::qualifiedName, Function.identity()));
        List<com.adp.gateway.context.domain.CanonicalContextField> fields = new ArrayList<>();

        for (int recordIndex = 0; recordIndex < result.records().size(); recordIndex++) {
            var record = result.records().get(recordIndex);
            for (Map.Entry<String, Object> entry : record.fields().entrySet()) {
                String qualifiedName = record.datasetName() + "." + entry.getKey();
                RetrievalField selectedField = selectedFields.get(qualifiedName);
                if (selectedField == null) {
                    continue;
                }
                String path = "$.records[%d].%s.%s".formatted(recordIndex, record.datasetName(), entry.getKey());
                String canonicalValue = canonicalValue(path, selectedField, entry.getValue());
                fields.add(new com.adp.gateway.context.domain.CanonicalContextField(
                    path,
                    record.datasetName(),
                    entry.getKey(),
                    selectedField.dataClass(),
                    entry.getValue(),
                    canonicalValueHasher.hash(canonicalValue)
                ));
            }
        }

        fields.sort(Comparator.comparing(com.adp.gateway.context.domain.CanonicalContextField::path));
        String contextDigest = canonicalValueHasher.hash(fields.stream()
            .map(field -> field.path() + ":" + field.dataClass().name() + ":" + field.valueDigest())
            .collect(Collectors.joining("|")));

        return new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_" + UUID.randomUUID(),
            result.dataAccessId(),
            result.workloadId(),
            result.purpose(),
            result.subjectType(),
            subjectRefHasher.hash(new com.adp.gateway.auth.domain.SubjectRef(result.subjectType(), result.subjectId())),
            fields,
            contextDigest
        );
    }

    private String canonicalValue(String path, RetrievalField field, Object value) {
        DataClass dataClass = field.dataClass() == null ? DataClass.UNKNOWN : field.dataClass();
        return path + ":" + dataClass.name() + ":" + String.valueOf(value);
    }
}
