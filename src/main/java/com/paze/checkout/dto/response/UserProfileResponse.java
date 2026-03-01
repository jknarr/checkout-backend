package com.paze.checkout.dto.response;

import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String phoneNumber
) {}
