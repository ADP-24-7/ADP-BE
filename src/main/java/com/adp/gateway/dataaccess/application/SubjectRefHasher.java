package com.adp.gateway.dataaccess.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.adp.gateway.auth.domain.SubjectRef;
import org.springframework.stereotype.Component;

@Component
public class SubjectRefHasher {

    public String hash(SubjectRef subject) {
        String canonical = subject.subjectType() + ":" + subject.subjectId();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
