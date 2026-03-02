package com.demo.checkout.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
    UUID orderId,
    String transactionId,
    Instant timestamp,
    ShippingAddressResponse shipping,
    BigDecimal subtotal,
    BigDecimal shippingCost,
    BigDecimal tax,
    BigDecimal total
) {}
