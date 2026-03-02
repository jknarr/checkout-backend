package com.demo.checkout.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.demo.checkout.domain.AuthStep;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthActionResponse(
    AuthStep currentStep,
    List<CardResponse> cards,
    String authToken,
    UserProfileResponse user,
    List<ShippingAddressResponse> addresses,
    CardResponse selectedCard,
    ShippingAddressResponse selectedAddress,
    String deviceChallenge,
    UUID challengeId,
    String otpCode,
    JsonNode passkeyOptions,
    Boolean offerPasskeyRegistration,
    BigDecimal subtotal,
    BigDecimal shippingCost,
    BigDecimal tax,
    BigDecimal total
) {
    public AuthActionResponse(
            AuthStep currentStep,
            List<CardResponse> cards,
            String authToken,
            UserProfileResponse user,
            List<ShippingAddressResponse> addresses,
            CardResponse selectedCard,
            ShippingAddressResponse selectedAddress,
            String deviceChallenge
    ) {
        this(currentStep, cards, authToken, user, addresses, selectedCard, selectedAddress,
                deviceChallenge, null, null, null, null, null, null, null, null);
    }
}
