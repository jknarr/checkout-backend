package com.paze.checkout.dto.request;

import com.paze.checkout.domain.ActionType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record AuthActionRequest(
    @NotNull ActionType action,
    // VERIFY_OTP
    UUID challengeId,
    String otpCode,
    // DEVICE_VERIFY
    UUID deviceId,
    String signature,
    // SELECT_CARD / CHANGE_CARD
    UUID cardId,
    // VERIFY_CVV
    String cvv,
    // SELECT_SHIPPING
    UUID addressId,
    Map<String, String> newAddress,
    // PASSKEY_REGISTER_FINISH / PASSKEY_AUTH_FINISH
    String passkeyResponse
) {
    public AuthActionRequest(
            @NotNull ActionType action,
            UUID challengeId,
            String otpCode,
            UUID deviceId,
            String signature,
            UUID cardId,
            String cvv,
            UUID addressId,
            Map<String, String> newAddress
    ) {
        this(action, challengeId, otpCode, deviceId, signature, cardId, cvv, addressId, newAddress, null);
    }
}
