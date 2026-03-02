package com.demo.checkout.service;

import com.demo.checkout.domain.OtpChallenge;
import com.demo.checkout.exception.OtpExpiredException;
import com.demo.checkout.exception.OtpInvalidException;
import com.demo.checkout.repository.OtpChallengeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock private OtpChallengeRepository otpChallengeRepository;
    @InjectMocks private OtpService otpService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "expiryMinutes", 10);
        ReflectionTestUtils.setField(otpService, "mockMode", true);
    }

    @Test
    void createChallenge_savesAndReturns() {
        OtpChallenge saved = OtpChallenge.builder().id(UUID.randomUUID())
                .phoneNumber("+15551234567").code("123456")
                .expiresAt(Instant.now().plusSeconds(600)).used(false).build();
        when(otpChallengeRepository.save(any())).thenReturn(saved);

        OtpChallenge result = otpService.createChallenge("+15551234567");
        assertNotNull(result);
        verify(otpChallengeRepository).save(any());
    }

    @Test
    void verify_success() {
        UUID id = UUID.randomUUID();
        OtpChallenge c = OtpChallenge.builder().id(id).phoneNumber("+15551234567")
                .code("123456").expiresAt(Instant.now().plusSeconds(600)).used(false).build();
        when(otpChallengeRepository.findById(id)).thenReturn(Optional.of(c));
        when(otpChallengeRepository.save(any())).thenReturn(c);

        assertDoesNotThrow(() -> otpService.verify(id, "+15551234567", "123456"));
    }

    @Test
    void verify_wrongCode_throws() {
        UUID id = UUID.randomUUID();
        OtpChallenge c = OtpChallenge.builder().id(id).phoneNumber("+15551234567")
                .code("123456").expiresAt(Instant.now().plusSeconds(600)).used(false).build();
        when(otpChallengeRepository.findById(id)).thenReturn(Optional.of(c));

        assertThrows(OtpInvalidException.class, () -> otpService.verify(id, "+15551234567", "000000"));
    }

    @Test
    void verify_expired_throws() {
        UUID id = UUID.randomUUID();
        OtpChallenge c = OtpChallenge.builder().id(id).phoneNumber("+15551234567")
                .code("123456").expiresAt(Instant.now().minusSeconds(1)).used(false).build();
        when(otpChallengeRepository.findById(id)).thenReturn(Optional.of(c));

        assertThrows(OtpExpiredException.class, () -> otpService.verify(id, "+15551234567", "123456"));
    }
}
