package com.adp.gateway.digitalasset.infrastructure;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.recovery.application.ExternalStatusQueryPermanentException;
import com.adp.gateway.recovery.application.ExternalStatusQueryPort;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class FakeDigitalAssetStatusQueryAdapter implements ExternalStatusQueryPort {

    static final String CONNECTOR_ID = "fake-digital-asset-platform";

    private final CanonicalValueHasher hasher;
    private final FakeDigitalAssetPlatformStateStore stateStore;

    public FakeDigitalAssetStatusQueryAdapter(
        CanonicalValueHasher hasher,
        FakeDigitalAssetPlatformStateStore stateStore
    ) {
        this.hasher = hasher;
        this.stateStore = stateStore;
    }

    @Override
    public boolean supports(String connectorId) {
        return CONNECTOR_ID.equals(connectorId);
    }

    @Override
    public ExternalStatusQueryResult query(ExternalInteractionRecovery recovery) {
        if (!supports(recovery.connectorId())) {
            throw new ExternalStatusQueryPermanentException("Unsupported digital asset connector");
        }
        if (recovery.providerCorrelationKey() == null || recovery.providerCorrelationKey().isBlank()) {
            throw new ExternalStatusQueryPermanentException("Digital asset provider correlation key is missing");
        }
        ConnectorStatus status = stateStore.find(recovery.providerCorrelationKey())
            .orElseThrow(() -> new ExternalStatusQueryPermanentException(
                "Digital asset provider request was not found"
            ));
        String evidenceDigest = hasher.hash(String.join("|",
            "digital-asset-status/v1", recovery.providerCorrelationKey(), status.name()
        ));
        return new ExternalStatusQueryResult(status, evidenceDigest);
    }
}
