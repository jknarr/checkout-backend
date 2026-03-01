package com.paze.checkout.dto.response;

import java.util.UUID;

public record CardResponse(
    UUID id,
    String last4,
    String expirationDate,
    String cardType,
    String cardArtUrl
) {}
