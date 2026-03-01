package com.paze.checkout.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paze.checkout.domain.AuthStep;
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
    String deviceChallenge
) {}
