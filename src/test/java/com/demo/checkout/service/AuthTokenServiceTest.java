package com.demo.checkout.service;

import com.demo.checkout.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthTokenServiceTest {

    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        authTokenService = new AuthTokenService();
        ReflectionTestUtils.setField(authTokenService, "secret",
                "demo-dev-secret-change-in-prod-must-be-256-bits-long-abcdefghijklmno");
        ReflectionTestUtils.setField(authTokenService, "expiryMinutes", 60);
        authTokenService.init();
    }

    @Test
    void generateAndValidate_roundTrip() {
        UUID userId = UUID.randomUUID();
        String token = authTokenService.generateToken(userId);
        UUID parsed = authTokenService.validateToken(token);
        assertEquals(userId, parsed);
    }

    @Test
    void validateToken_invalid_throws() {
        assertThrows(UnauthorizedException.class, () -> authTokenService.validateToken("bad.token.here"));
    }
}
