package com.paze.checkout.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateSessionRequest(
    @NotBlank String merchantId,
    @NotEmpty List<CartItemDto> cart
) {}
