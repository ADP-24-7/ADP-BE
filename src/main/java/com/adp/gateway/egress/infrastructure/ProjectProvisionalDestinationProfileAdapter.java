package com.adp.gateway.egress.infrastructure;

import java.util.Set;

import com.adp.gateway.egress.application.DestinationProfileNotFoundException;
import com.adp.gateway.egress.application.DestinationProfilePort;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class ProjectProvisionalDestinationProfileAdapter implements DestinationProfilePort {

    @Override
    public DestinationProfile load(String providerProfileId) {
        if (!"internal-provider".equals(providerProfileId)) {
            throw new DestinationProfileNotFoundException(providerProfileId);
        }
        return new DestinationProfile(
            "dest_internal_provider_project_provisional",
            providerProfileId,
            ExecutionPackType.AI,
            "project-provisional-egress-schema-v1",
            true,
            Set.of("customer_summary"),
            Set.of("CUSTOMER_SUPPORT")
        );
    }
}
