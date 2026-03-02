package com.demo.checkout.dto.response;

import com.demo.checkout.domain.SessionStatus;
import java.util.UUID;

public record SessionResponse(UUID id, String merchantId, SessionStatus status) {}
