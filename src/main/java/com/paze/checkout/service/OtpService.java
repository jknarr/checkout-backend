package com.paze.checkout.service;

import com.paze.checkout.domain.OtpChallenge;
import com.paze.checkout.exception.OtpExpiredException;
import com.paze.checkout.exception.OtpInvalidException;
import com.paze.checkout.repository.OtpChallengeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpChallengeRepository otpChallengeRepository;

    @Value("${paze.otp.expiry-minutes}")
    private int expiryMinutes;

    @Value("${paze.otp.mock-mode}")
    private boolean mockMode;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public OtpChallenge createChallenge(String phoneNumber) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        OtpChallenge challenge = OtpChallenge.builder()
                .phoneNumber(phoneNumber)
                .code(code)
                .expiresAt(Instant.now().plusSeconds(expiryMinutes * 60L))
                .used(false)
                .build();
        log.debug("OTP challenge created for phone: {}, mockMode={}", phoneNumber, mockMode);
        return otpChallengeRepository.save(challenge);
    }

    @Transactional
    public void verify(UUID challengeId, String phoneNumber, String code) {
        OtpChallenge challenge = otpChallengeRepository.findById(challengeId)
                .orElseThrow(() -> new OtpInvalidException("Invalid challenge ID"));
        if (challenge.isUsed()) throw new OtpInvalidException("OTP already used");
        if (!challenge.getPhoneNumber().equals(phoneNumber)) throw new OtpInvalidException("Phone number mismatch");
        if (Instant.now().isAfter(challenge.getExpiresAt())) throw new OtpExpiredException("OTP has expired");
        if (!challenge.getCode().equals(code)) throw new OtpInvalidException("Invalid OTP code");

        challenge.setUsed(true);
        otpChallengeRepository.save(challenge);
        log.debug("OTP verified for challengeId: {}", challengeId);
    }

    public boolean isMockMode() {
        return mockMode;
    }
}
