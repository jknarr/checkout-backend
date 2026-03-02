package com.paze.checkout.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paze.checkout.domain.AuthStep;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InitAuthResponse(
    UUID authSessionId,
    AuthStep currentStep,
    UUID challengeId,
    String otpCode,
    String deviceChallenge,
    Boolean hasPasskey
) {
    public InitAuthResponse(
            UUID authSessionId,
            AuthStep currentStep,
            UUID challengeId,
            String otpCode,
            String deviceChallenge
    ) {
        this(authSessionId, currentStep, challengeId, otpCode, deviceChallenge, null);
    }
}
