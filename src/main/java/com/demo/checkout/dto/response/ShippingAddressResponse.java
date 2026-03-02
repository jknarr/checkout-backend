package com.demo.checkout.dto.response;

import java.util.UUID;

public record ShippingAddressResponse(
    UUID id,
    String label,
    String firstName,
    String lastName,
    String address,
    String city,
    String state,
    String zip,
    String country,
    boolean isDefault
) {}
