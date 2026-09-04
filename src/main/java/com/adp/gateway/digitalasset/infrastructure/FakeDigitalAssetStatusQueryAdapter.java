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

    public FakeDigitalAssetStatusQueryAdapter(CanonicalValueHasher hasher) {
        this.hasher = hasher;
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
        String evidenceDigest = hasher.hash(String.join("|",
            "digital-asset-status/v1", recovery.providerCorrelationKey(), ConnectorStatus.ACKNOWLEDGED.name()
        ));
        return new ExternalStatusQueryResult(ConnectorStatus.ACKNOWLEDGED, evidenceDigest);
    }
}
