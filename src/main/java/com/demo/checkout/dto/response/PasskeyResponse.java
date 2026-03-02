package com.demo.checkout.dto.response;

import java.time.Instant;

public record PasskeyResponse(
        String credentialId,
        String label,
        Instant createdAt,
        Instant lastUsedAt
) {}
