package com.adp.gateway.egress.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.egress.application.ResponseLeakageDetector;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ResponseSensitiveFinding;
import org.springframework.stereotype.Component;

@Component
public class RegexResponseLeakageDetector implements ResponseLeakageDetector {

    private static final String VERSION = "ai-response-regex-v1";
    private static final List<Rule> RULES = List.of(
        new Rule("EMAIL", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
        new Rule("PHONE_NUMBER", Pattern.compile("\\b01[016789]-?\\d{3,4}-?\\d{4}\\b")),
        new Rule(
            "ACCOUNT_NUMBER",
            Pattern.compile("\\b(?!01[016789]-?\\d{3,4}-?\\d{4}\\b)\\d{2,6}-\\d{2,6}-\\d{3,8}\\b")
        ),
        new Rule("RESIDENT_REGISTRATION_NUMBER", Pattern.compile("\\b\\d{6}-[1-4]\\d{6}\\b"))
    );

    private final CanonicalValueHasher hasher;

    public RegexResponseLeakageDetector(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public String detectorVersion() {
        return VERSION;
    }

    @Override
    public List<ResponseSensitiveFinding> detect(OutboundCandidatePayload outboundPayload, Object responsePayload) {
        if (responsePayload == null) {
            return List.of();
        }
        String response = String.valueOf(responsePayload);
        List<ResponseSensitiveFinding> findings = new ArrayList<>();
        RULES.forEach(rule -> {
            var matcher = rule.pattern().matcher(response);
            while (matcher.find()) {
                findings.add(finding(rule.type(), matcher.start(), matcher.end(), matcher.group()));
            }
        });
        outboundPayload.fields().stream()
            .map(field -> field.value() == null ? null : String.valueOf(field.value()))
            .filter(value -> value != null && value.length() >= 4)
            .distinct()
            .forEach(value -> {
                int offset = response.indexOf(value);
                if (offset >= 0) {
                    findings.add(finding("RAW_VALUE_REFLECTION", offset, offset + value.length(), value));
                }
            });
        return List.copyOf(findings);
    }

    private ResponseSensitiveFinding finding(String type, int start, int end, String evidence) {
        return new ResponseSensitiveFinding(
            type,
            "$.response",
            start,
            end,
            VERSION,
            hasher.hash("$.response:" + evidence)
        );
    }

    private record Rule(String type, Pattern pattern) {
    }
}
