package com.adp.gateway.detection.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.detection.application.SensitiveDataDetector;
import com.adp.gateway.detection.domain.DetectionResult;
import com.adp.gateway.detection.domain.SensitiveDataFinding;
import com.adp.gateway.detection.domain.SensitiveDataType;
import org.springframework.stereotype.Component;

@Component
public class RegexSensitiveDataDetector implements SensitiveDataDetector {

    private static final String VERSION = "regex-dev-1";
    private static final List<Rule> RULES = List.of(
        new Rule(SensitiveDataType.EMAIL, Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
        new Rule(SensitiveDataType.PHONE_NUMBER, Pattern.compile("\\b01[016789]-?\\d{3,4}-?\\d{4}\\b")),
        new Rule(SensitiveDataType.ACCOUNT_NUMBER, Pattern.compile("\\b\\d{2,6}-\\d{2,6}-\\d{3,8}\\b")),
        new Rule(SensitiveDataType.RESIDENT_REGISTRATION_NUMBER, Pattern.compile("\\b\\d{6}-[1-4]\\d{6}\\b")),
        new Rule(SensitiveDataType.PERSON_NAME, Pattern.compile("\\b[A-Z][a-z]+\\s[A-Z][a-z]+\\b"))
    );

    private final CanonicalValueHasher canonicalValueHasher;

    public RegexSensitiveDataDetector(CanonicalValueHasher canonicalValueHasher) {
        this.canonicalValueHasher = canonicalValueHasher;
    }

    @Override
    public String detectorVersion() {
        return VERSION;
    }

    @Override
    public DetectionResult detect(CanonicalContext context) {
        List<SensitiveDataFinding> findings = new ArrayList<>();
        for (CanonicalContextField field : context.fields()) {
            if (field.value() == null) {
                continue;
            }
            String value = String.valueOf(field.value());
            for (Rule rule : RULES) {
                var matcher = rule.pattern().matcher(value);
                while (matcher.find()) {
                    findings.add(new SensitiveDataFinding(
                        rule.type(),
                        field.path(),
                        matcher.start(),
                        matcher.end(),
                        VERSION,
                        canonicalValueHasher.hash(field.path() + ":" + matcher.group())
                    ));
                }
            }
        }

        return new DetectionResult(VERSION, findings);
    }

    private record Rule(SensitiveDataType type, Pattern pattern) {
    }
}
