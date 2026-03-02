package com.demo.checkout.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RegisterDeviceRequest(@NotBlank String publicKey) {}
