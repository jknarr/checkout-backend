package com.demo.checkout.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InitAuthRequest(
    @NotBlank String phoneNumber,
    @NotNull UUID checkoutSessionId
) {}
