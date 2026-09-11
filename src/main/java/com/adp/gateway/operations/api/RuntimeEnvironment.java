package com.adp.gateway.operations.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class RuntimeEnvironment {

    private final RuntimeProfile profile;
    private final DataProvenance dataProvenance;

    public RuntimeEnvironment(
        @Value("${adp.environment.profile:production-like}") String profile,
        @Value("${adp.environment.data-provenance:NONE}") String dataProvenance
    ) {
        this.profile = RuntimeProfile.from(profile);
        this.dataProvenance = DataProvenance.from(dataProvenance);
    }

    public String profile() {
        return profile.configValue;
    }

    public String dataProvenance() {
        return dataProvenance.name();
    }

    private enum RuntimeProfile {
        LOCAL("local"),
        DEMO("demo"),
        PRODUCTION_LIKE("production-like");

        private final String configValue;

        RuntimeProfile(String configValue) {
            this.configValue = configValue;
        }

        static RuntimeProfile from(String value) {
            for (RuntimeProfile candidate : values()) {
                if (candidate.configValue.equals(value)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException(
                "Invalid adp.environment.profile: " + value
                    + ". Expected local, demo, or production-like."
            );
        }
    }

    private enum DataProvenance {
        LOCAL_DEVELOPMENT,
        SYNTHETIC,
        NONE;

        static DataProvenance from(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new IllegalArgumentException(
                    "Invalid adp.environment.data-provenance: " + value
                        + ". Expected LOCAL_DEVELOPMENT, SYNTHETIC, or NONE.",
                    exception
                );
            }
        }
    }
}
