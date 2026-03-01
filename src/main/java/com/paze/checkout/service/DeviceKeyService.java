package com.paze.checkout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paze.checkout.domain.DeviceRegistration;
import com.paze.checkout.domain.User;
import com.paze.checkout.exception.DeviceVerificationException;
import com.paze.checkout.repository.DeviceRegistrationRepository;
import com.paze.checkout.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceKeyService {

    private final DeviceRegistrationRepository deviceRegistrationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${paze.device.challenge-expiry-seconds}")
    private int challengeExpirySeconds;

    private final ConcurrentHashMap<UUID, ChallengeEntry> activeChallenges = new ConcurrentHashMap<>();

    record ChallengeEntry(String challenge, Instant expiresAt) {}

    public String issueChallenge(UUID deviceId) {
        deviceRegistrationRepository.findById(deviceId)
                .orElseThrow(() -> new DeviceVerificationException("Device not found: " + deviceId));
        String challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(secureRandomBytes(32));
        activeChallenges.put(deviceId, new ChallengeEntry(challenge,
                Instant.now().plusSeconds(challengeExpirySeconds)));
        return challenge;
    }

    @Transactional
    public UUID verifyDevice(UUID deviceId, String signature) {
        DeviceRegistration reg = deviceRegistrationRepository.findById(deviceId)
                .orElseThrow(() -> new DeviceVerificationException("Device not found"));

        ChallengeEntry entry = activeChallenges.remove(deviceId);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            throw new DeviceVerificationException("Challenge expired or not found");
        }

        try {
            PublicKey publicKey = parsePublicKey(reg.getPublicKeyJwk());
            byte[] sigBytes = Base64.getUrlDecoder().decode(signature);
            byte[] challengeBytes = entry.challenge().getBytes(java.nio.charset.StandardCharsets.UTF_8);

            Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
            verifier.initVerify(publicKey);
            verifier.update(challengeBytes);
            if (!verifier.verify(sigBytes)) {
                throw new DeviceVerificationException("Signature verification failed");
            }
            log.debug("Device verified: {}", deviceId);
            return reg.getUser().getId();
        } catch (DeviceVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Device verification error", e);
            throw new DeviceVerificationException("Signature verification failed");
        }
    }

    @Transactional
    public UUID registerDevice(UUID userId, String publicKeyJwk) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        DeviceRegistration reg = DeviceRegistration.builder()
                .user(user)
                .publicKeyJwk(publicKeyJwk)
                .build();
        DeviceRegistration saved = deviceRegistrationRepository.save(reg);
        log.info("Device registered for userId: {}, deviceId: {}", userId, saved.getId());
        return saved.getId();
    }

    public Optional<UUID> getFirstDeviceId(UUID userId) {
        List<DeviceRegistration> devices = deviceRegistrationRepository.findByUserId(userId);
        return devices.isEmpty() ? Optional.empty() : Optional.of(devices.get(0).getId());
    }

    @SuppressWarnings("unchecked")
    private PublicKey parsePublicKey(String jwkJson) throws Exception {
        Map<String, Object> jwk = objectMapper.readValue(jwkJson, Map.class);
        byte[] x = Base64.getUrlDecoder().decode((String) jwk.get("x"));
        byte[] y = Base64.getUrlDecoder().decode((String) jwk.get("y"));
        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(point, spec);
        return KeyFactory.getInstance("EC").generatePublic(keySpec);
    }

    private byte[] secureRandomBytes(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }
}
