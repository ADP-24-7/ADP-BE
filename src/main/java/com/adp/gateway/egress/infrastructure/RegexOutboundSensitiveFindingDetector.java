package com.adp.gateway.egress.infrastructure;

import java.util.List;
import java.util.regex.Pattern;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.egress.application.OutboundSensitiveFindingDetector;
import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundSensitiveFinding;
import org.springframework.stereotype.Component;

@Component
public class RegexOutboundSensitiveFindingDetector implements OutboundSensitiveFindingDetector {

    public static final String DETECTOR_VERSION = "regex-outbound-secret-detector-v1";

    private static final Pattern SECRET_PATTERN = Pattern.compile(
        "(?i).*(api[_-]?key|secret|private[_-]?key|seed|credential|access[_-]?token|refresh[_-]?token|"
            + "sk-[a-z0-9_-]{8,}|ghp_[a-z0-9_]{8,}|xox[baprs]-[a-z0-9-]{8,}).*"
    );

    private final CanonicalValueHasher hasher;

    public RegexOutboundSensitiveFindingDetector(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public List<OutboundSensitiveFinding> detect(OutboundCandidateField field) {
        if (SECRET_PATTERN.matcher(field.path()).matches()
            || SECRET_PATTERN.matcher(String.valueOf(field.value())).matches()) {
            return List.of(new OutboundSensitiveFinding(
                "SECRET",
                DETECTOR_VERSION,
                hasher.hash(field.path() + ":" + String.valueOf(field.value()))
            ));
        }
        return List.of();
    }
}
