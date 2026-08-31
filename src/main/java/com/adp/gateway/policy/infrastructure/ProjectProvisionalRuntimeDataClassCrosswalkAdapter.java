package com.adp.gateway.policy.infrastructure;

import java.util.Map;
import java.util.Optional;

import com.adp.gateway.policy.domain.CrosswalkMapping;
import com.adp.gateway.policy.domain.RuntimeDataClassCrosswalkPort;
import org.springframework.stereotype.Component;

@Component
public class ProjectProvisionalRuntimeDataClassCrosswalkAdapter implements RuntimeDataClassCrosswalkPort {

    private static final String CROSSWALK_VERSION = "project-provisional/v1";
    private static final Map<String, CrosswalkMapping> MAPPINGS = Map.of(
        "PERSONAL_INFORMATION",
        new CrosswalkMapping(
            CROSSWALK_VERSION,
            "PERSONAL_INFORMATION",
            "CUSTOMER_IDENTIFIER",
            "mapped",
            "PROJECT_PROVISIONAL local BE fixture",
            "0.0.0"
        )
    );

    @Override
    public Optional<CrosswalkMapping> resolve(String regulatoryDataCategory) {
        return Optional.ofNullable(MAPPINGS.get(regulatoryDataCategory));
    }
}
