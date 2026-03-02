package com.demo.checkout.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ShippingAddressDto(
    @NotBlank String firstName,
    @NotBlank String lastName,
    String email,
    @NotBlank String address,
    @NotBlank String city,
    @NotBlank String state,
    @NotBlank String zip,
    @NotBlank String country
) {}
