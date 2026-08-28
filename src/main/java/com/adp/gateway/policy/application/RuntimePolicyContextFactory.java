package com.adp.gateway.policy.application;

import java.util.List;
import java.util.stream.Collectors;

import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.retrieval.domain.DataClass;
import org.springframework.stereotype.Component;

@Component
public class RuntimePolicyContextFactory {

    private final SubjectRefHasher subjectRefHasher;
    private final CanonicalValueHasher canonicalValueHasher;

    public RuntimePolicyContextFactory(
        SubjectRefHasher subjectRefHasher,
        CanonicalValueHasher canonicalValueHasher
    ) {
        this.subjectRefHasher = subjectRefHasher;
        this.canonicalValueHasher = canonicalValueHasher;
    }

    public RuntimePolicyContext from(RuntimeRequestContext requestContext) {
        SubjectRef subject = SubjectRef.from(requestContext.subject());
        String subjectType = subject == null ? null : subject.subjectType();
        String subjectDigest = subject == null ? null : subjectRefHasher.hash(subject);
        return from(
            requestContext.workloadId(),
            requestContext.purpose(),
            subjectType,
            subjectDigest,
            null,
            List.of(),
            List.of(),
            null
        );
    }

    public RuntimePolicyContext from(CanonicalContext canonicalContext) {
        return from(
            canonicalContext.workloadId(),
            canonicalContext.purpose(),
            canonicalContext.subjectType(),
            canonicalContext.subjectRefDigest(),
            canonicalContext.contextDigest(),
            canonicalContext.fields().stream()
                .map(field -> field.dataClass() == null ? DataClass.UNKNOWN : field.dataClass())
                .distinct()
                .sorted()
                .toList(),
            List.of(),
            null
        );
    }

    private RuntimePolicyContext from(
        String workloadId,
        String purpose,
        String subjectType,
        String subjectRefDigest,
        String canonicalContextDigest,
        List<DataClass> runtimeDataClasses,
        List<String> processingContexts,
        String provider
    ) {
        String runtimeDataClassValue = runtimeDataClasses.stream()
            .map(Enum::name)
            .collect(Collectors.joining(","));
        String processingContextValue = processingContexts.stream()
            .sorted()
            .collect(Collectors.joining(","));
        String digest = canonicalValueHasher.hash(String.join(
            "|",
            value(workloadId),
            value(purpose),
            value(subjectType),
            value(subjectRefDigest),
            value(canonicalContextDigest),
            runtimeDataClassValue,
            processingContextValue,
            value(provider)
        ));
        return new RuntimePolicyContext(
            workloadId,
            purpose,
            subjectType,
            subjectRefDigest,
            canonicalContextDigest,
            List.copyOf(runtimeDataClasses),
            List.copyOf(processingContexts),
            provider,
            digest
        );
    }

    private String value(String value) {
        return value == null ? "<none>" : value;
    }
}
