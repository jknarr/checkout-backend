package com.paze.checkout.dto.request;

public record CartItemDto(
    String productId,
    String name,
    double price,
    int quantity,
    String imageUrl
) {}
