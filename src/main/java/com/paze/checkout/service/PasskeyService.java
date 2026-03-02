package com.paze.checkout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paze.checkout.domain.PasskeyCredential;
import com.paze.checkout.domain.User;
import com.paze.checkout.exception.PasskeyException;
import com.paze.checkout.repository.PasskeyCredentialRepository;
import com.paze.checkout.repository.UserRepository;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.data.exception.Base64UrlException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasskeyService {

    private final PasskeyCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final PasskeyChallengeStore challengeStore;
    private final ObjectMapper objectMapper;

    @Value("${paze.passkey.rp-id}")
    private String rpId;

    @Value("${paze.passkey.rp-name}")
    private String rpName;

    @Value("${paze.passkey.origin}")
    private String origin;

    @Value("${paze.passkey.challenge-ttl-seconds:120}")
    private int challengeTtlSeconds;

    public JsonNode beginRegistration(UUID authSessionId, User user) {
        try {
            PublicKeyCredentialCreationOptions options = relyingParty().startRegistration(
                    StartRegistrationOptions.builder()
                            .user(UserIdentity.builder()
                                    .name(user.getPhoneNumber())
                                    .displayName(user.getFirstName() + " " + user.getLastName())
                                    .id(uuidToByteArray(user.getId()))
                                    .build())
                            .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                    .residentKey(ResidentKeyRequirement.REQUIRED)
                                    .userVerification(UserVerificationRequirement.REQUIRED)
                                    .build())
                            .build());

            String json = options.toJson();
            challengeStore.put(authSessionId, user.getId(), "registration", json,
                    Instant.now().plusSeconds(challengeTtlSeconds));

            log.info("[passkey] registration begin userId={} authSessionId={}", user.getId(), authSessionId);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new PasskeyException("Failed to start passkey registration", e);
        }
    }

    @Transactional
    public void finishRegistration(UUID authSessionId, User user, String clientResponseJson) {
        String requestJson = challengeStore.getAndConsume(authSessionId, user.getId(), "registration")
                .orElseThrow(() -> new PasskeyException("Passkey challenge expired or not found"));

        try {
            PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions.fromJson(requestJson);
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> response =
                    PublicKeyCredential.parseRegistrationResponseJson(clientResponseJson);

            RegistrationResult result = relyingParty().finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(request)
                            .response(response)
                            .build());

            PasskeyCredential credential = PasskeyCredential.builder()
                    .user(user)
                    .credentialId(result.getKeyId().getId().getBase64Url())
                    .publicKeyCose(result.getPublicKeyCose().getBase64Url())
                    .signCount(result.getSignatureCount())
                    .aaguid(result.getAaguid().getHex())
                    .transports(serializeTransports(response.getResponse().getTransports()))
                    .build();

            credentialRepository.save(credential);
            log.info("[passkey] registration success userId={} credentialId={}", user.getId(), credential.getCredentialId());
        } catch (Exception e) {
            log.warn("[passkey] registration failed userId={} authSessionId={} reason={}",
                    user.getId(), authSessionId, e.getMessage());
            throw new PasskeyException("Passkey registration verification failed", e);
        }
    }

    public JsonNode beginAssertion(UUID authSessionId, User user) {
        try {
            AssertionRequest request = relyingParty().startAssertion(
                    StartAssertionOptions.builder()
                            .username(user.getPhoneNumber())
                            .userVerification(UserVerificationRequirement.REQUIRED)
                            .build());

            String json = request.toJson();
            challengeStore.put(authSessionId, user.getId(), "assertion", json,
                    Instant.now().plusSeconds(challengeTtlSeconds));

            log.info("[passkey] assertion begin userId={} authSessionId={}", user.getId(), authSessionId);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new PasskeyException("Failed to start passkey authentication", e);
        }
    }

    @Transactional
    public UUID finishAssertion(UUID authSessionId, User user, String clientResponseJson) {
        String requestJson = challengeStore.getAndConsume(authSessionId, user.getId(), "assertion")
                .orElseThrow(() -> new PasskeyException("Passkey challenge expired or not found"));

        try {
            AssertionRequest request = AssertionRequest.fromJson(requestJson);
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> response =
                    PublicKeyCredential.parseAssertionResponseJson(clientResponseJson);

            AssertionResult result = relyingParty().finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(request)
                            .response(response)
                            .build());

            if (!result.isSuccess()) {
                throw new PasskeyException("Passkey assertion failed");
            }
            if (!result.getUsername().equals(user.getPhoneNumber())) {
                throw new PasskeyException("Passkey user mismatch");
            }

            String credentialId = result.getCredentialId().getBase64Url();
            PasskeyCredential stored = credentialRepository.findByCredentialIdAndRevokedAtIsNull(credentialId)
                    .orElseThrow(() -> new PasskeyException("Credential not found or revoked"));

            long newSignCount = result.getSignatureCount();
            if (stored.getSignCount() > 0 && newSignCount <= stored.getSignCount()) {
                log.warn("[passkey] sign counter anomaly credentialId={} stored={} received={}",
                        credentialId, stored.getSignCount(), newSignCount);
                throw new PasskeyException("Sign counter anomaly detected");
            }

            stored.setSignCount(newSignCount);
            stored.setLastUsedAt(Instant.now());
            credentialRepository.save(stored);

            log.info("[passkey] assertion success userId={} credentialId={}", user.getId(), credentialId);
            return user.getId();
        } catch (PasskeyException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[passkey] assertion failed userId={} authSessionId={} reason={}",
                    user.getId(), authSessionId, e.getMessage());
            throw new PasskeyException("Passkey verification failed", e);
        }
    }

    public boolean hasActivePasskey(UUID userId) {
        return credentialRepository.existsByUserIdAndRevokedAtIsNull(userId);
    }

    public List<PasskeyCredential> listCredentials(UUID userId) {
        return credentialRepository.findByUserIdAndRevokedAtIsNull(userId);
    }

    @Transactional
    public void revokeCredential(UUID userId, String credentialId) {
        PasskeyCredential credential = credentialRepository.findByCredentialIdAndRevokedAtIsNull(credentialId)
                .orElseThrow(() -> new PasskeyException("Credential not found"));

        if (!credential.getUser().getId().equals(userId)) {
            throw new PasskeyException("Credential does not belong to this user");
        }

        credential.setRevokedAt(Instant.now());
        credentialRepository.save(credential);
        log.info("[passkey] credential revoked userId={} credentialId={}", userId, credentialId);
    }

    private RelyingParty relyingParty() {
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(rpId).name(rpName).build())
                .credentialRepository(new JpaCredentialRepository())
                .origins(Set.of(origin))
                .attestationConveyancePreference(AttestationConveyancePreference.NONE)
                .build();
    }

    private String serializeTransports(SortedSet<AuthenticatorTransport> transports) {
        if (transports == null || transports.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(transports.stream().map(AuthenticatorTransport::getId).toList());
        } catch (IOException e) {
            return "[]";
        }
    }

    private Set<AuthenticatorTransport> parseTransports(String transportsJson) {
        if (transportsJson == null || transportsJson.isBlank()) {
            return Set.of();
        }
        try {
            List<String> ids = objectMapper.readValue(transportsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return ids.stream()
                    .map(id -> {
                        try {
                            return AuthenticatorTransport.of(id);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return Set.of();
        }
    }

    private static ByteArray uuidToByteArray(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return new ByteArray(buffer.array());
    }

    private static UUID byteArrayToUuid(ByteArray userHandle) {
        byte[] bytes = userHandle.getBytes();
        if (bytes.length != 16) {
            throw new IllegalArgumentException("Invalid user handle length");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private class JpaCredentialRepository implements CredentialRepository {

        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            Optional<User> user = userRepository.findByPhoneNumber(username);
            if (user.isEmpty()) {
                return Set.of();
            }
            UUID userId = user.get().getId();
            return credentialRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
                    .map(c -> {
                        try {
                            PublicKeyCredentialDescriptor.PublicKeyCredentialDescriptorBuilder builder =
                                    PublicKeyCredentialDescriptor.builder().id(ByteArray.fromBase64Url(c.getCredentialId()));
                            Set<AuthenticatorTransport> transports = parseTransports(c.getTransports());
                            if (!transports.isEmpty()) {
                                builder.transports(transports);
                            }
                            return builder.build();
                        } catch (Base64UrlException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            return userRepository.findByPhoneNumber(username)
                    .map(User::getId)
                    .map(PasskeyService::uuidToByteArray);
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            try {
                UUID userId = byteArrayToUuid(userHandle);
                return userRepository.findById(userId).map(User::getPhoneNumber);
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            return credentialRepository.findByCredentialIdAndRevokedAtIsNull(credentialId.getBase64Url())
                    .filter(c -> userHandle == null || c.getUser().getId().equals(byteArrayToUuid(userHandle)))
                    .map(this::toRegisteredCredential);
        }

        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            return credentialRepository.findByCredentialIdAndRevokedAtIsNull(credentialId.getBase64Url())
                    .map(c -> Set.of(toRegisteredCredential(c)))
                    .orElseGet(Set::of);
        }

        private RegisteredCredential toRegisteredCredential(PasskeyCredential c) {
            try {
                return RegisteredCredential.builder()
                        .credentialId(ByteArray.fromBase64Url(c.getCredentialId()))
                        .userHandle(uuidToByteArray(c.getUser().getId()))
                        .publicKeyCose(ByteArray.fromBase64Url(c.getPublicKeyCose()))
                        .signatureCount(c.getSignCount())
                        .build();
            } catch (Base64UrlException e) {
                throw new PasskeyException("Stored passkey data is invalid", e);
            }
        }
    }
}
