package com.paze.checkout.dto.response;

import com.paze.checkout.domain.SessionStatus;
import java.util.UUID;

public record SessionResponse(UUID id, String merchantId, SessionStatus status) {}
