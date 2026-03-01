package com.paze.checkout.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paze.checkout.domain.*;
import com.paze.checkout.dto.request.AuthActionRequest;
import com.paze.checkout.dto.response.*;
import com.paze.checkout.exception.*;
import com.paze.checkout.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthSessionService {

    private final UserRepository userRepository;
    private final CardRepository cardRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final AuthSessionRepository authSessionRepository;
    private final OtpService otpService;
    private final AuthTokenService authTokenService;
    private final DeviceKeyService deviceKeyService;
    private final BCryptPasswordEncoder bcrypt;
    private final ObjectMapper objectMapper;

    @Value("${paze.session.expiry-minutes}")
    private int sessionExpiryMinutes;

    @Transactional
    public InitAuthResponse initAuth(String phoneNumber, UUID checkoutSessionId) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new UserNotFoundException("No account found for phone: " + phoneNumber));

        OtpChallenge challenge = otpService.createChallenge(phoneNumber);

        AuthSession authSession = AuthSession.builder()
                .user(user)
                .checkoutSessionId(checkoutSessionId)
                .currentStep(AuthStep.OTP_VERIFY)
                .deviceVerified(false)
                .expiresAt(Instant.now().plusSeconds(sessionExpiryMinutes * 60L))
                .build();
        authSession = authSessionRepository.save(authSession);

        String otpCode = otpService.isMockMode() ? challenge.getCode() : null;
        return new InitAuthResponse(authSession.getId(), AuthStep.OTP_VERIFY,
                challenge.getId(), otpCode, null);
    }

    @Transactional
    public AuthActionResponse handleAction(UUID authSessionId, AuthActionRequest req, String token) {
        AuthSession session = authSessionRepository.findById(authSessionId)
                .orElseThrow(() -> new AuthSessionNotFoundException("Auth session not found: " + authSessionId));

        return switch (req.action()) {
            case VERIFY_OTP      -> handleVerifyOtp(session, req);
            case DEVICE_VERIFY   -> handleDeviceVerify(session, req);
            case SELECT_CARD     -> handleSelectCard(session, req);
            case VERIFY_CVV      -> handleVerifyCvv(session, req);
            case CHANGE_CARD     -> handleChangeCard(session, token);
            case SELECT_SHIPPING -> handleSelectShipping(session, req, token);
        };
    }

    private AuthActionResponse handleVerifyOtp(AuthSession session, AuthActionRequest req) {
        if (session.getCurrentStep() != AuthStep.OTP_VERIFY) {
            throw new InvalidStepException("Expected OTP_VERIFY, current: " + session.getCurrentStep());
        }
        otpService.verify(req.challengeId(), session.getUser().getPhoneNumber(), req.otpCode());
        session.setCurrentStep(AuthStep.CARD_SELECT);
        authSessionRepository.save(session);

        List<CardResponse> cards = cardRepository.findByUserId(session.getUser().getId())
                .stream().map(this::toCardResponse).collect(Collectors.toList());

        String deviceChallenge = null;
        if (req.deviceId() != null) {
            deviceChallenge = deviceKeyService
                    .issueChallengeForUserDevice(session.getUser().getId(), req.deviceId())
                    .orElse(null);
        }

        return new AuthActionResponse(AuthStep.CARD_SELECT, cards, null, null, null, null, null, deviceChallenge);
    }

    private AuthActionResponse handleDeviceVerify(AuthSession session, AuthActionRequest req) {
        if (session.getCurrentStep() != AuthStep.CARD_SELECT) {
            throw new InvalidStepException("Expected CARD_SELECT step for DEVICE_VERIFY");
        }
        UUID userId = deviceKeyService.verifyDevice(req.deviceId(), req.signature());
        if (!userId.equals(session.getUser().getId())) {
            throw new DeviceVerificationException("Device does not belong to this user");
        }

        // Auto-select first card — user can change via CHANGE_CARD from review screen
        List<Card> cards = cardRepository.findByUserId(userId);
        if (cards.isEmpty()) {
            throw new InvalidStepException("No cards available for this user");
        }
        session.setSelectedCardId(cards.get(0).getId());

        session.setDeviceVerified(true);
        String authToken = authTokenService.generateToken(session.getUser().getId());
        session.setAuthToken(authToken);
        session.setCurrentStep(AuthStep.REVIEW);
        authSessionRepository.save(session);
        return buildReviewResponse(session, authToken);
    }

    private AuthActionResponse handleSelectCard(AuthSession session, AuthActionRequest req) {
        if (session.getCurrentStep() != AuthStep.CARD_SELECT) {
            throw new InvalidStepException("Expected CARD_SELECT step");
        }
        Card card = cardRepository.findById(req.cardId())
                .orElseThrow(() -> new InvalidStepException("Card not found: " + req.cardId()));
        if (!card.getUser().getId().equals(session.getUser().getId())) {
            throw new UnauthorizedException("Card does not belong to this user");
        }
        session.setSelectedCardId(req.cardId());

        if (session.isDeviceVerified()) {
            session.setCurrentStep(AuthStep.REVIEW);
            authSessionRepository.save(session);
            return buildReviewResponse(session, session.getAuthToken());
        } else {
            session.setCurrentStep(AuthStep.CVV);
            authSessionRepository.save(session);
            return new AuthActionResponse(AuthStep.CVV, null, null, null, null, toCardResponse(card), null, null);
        }
    }

    private AuthActionResponse handleVerifyCvv(AuthSession session, AuthActionRequest req) {
        if (session.getCurrentStep() != AuthStep.CVV) {
            throw new InvalidStepException("Expected CVV step");
        }
        Card card = cardRepository.findById(session.getSelectedCardId())
                .orElseThrow(() -> new InvalidStepException("Selected card not found"));
        if (!bcrypt.matches(req.cvv(), card.getCvvHash())) {
            throw new CvvInvalidException("Invalid CVV");
        }
        String authToken = authTokenService.generateToken(session.getUser().getId());
        session.setAuthToken(authToken);
        session.setCurrentStep(AuthStep.REVIEW);
        authSessionRepository.save(session);
        return buildReviewResponse(session, authToken);
    }

    private AuthActionResponse handleChangeCard(AuthSession session, String token) {
        if (session.getCurrentStep() != AuthStep.REVIEW) {
            throw new InvalidStepException("Expected REVIEW step for CHANGE_CARD");
        }
        requireAuth(session, token);
        session.setSelectedCardId(null);
        session.setCurrentStep(AuthStep.CARD_SELECT);
        authSessionRepository.save(session);

        List<CardResponse> cards = cardRepository.findByUserId(session.getUser().getId())
                .stream().map(this::toCardResponse).collect(Collectors.toList());
        return new AuthActionResponse(AuthStep.CARD_SELECT, cards, null, null, null, null, null, null);
    }

    private AuthActionResponse handleSelectShipping(AuthSession session, AuthActionRequest req, String token) {
        if (session.getCurrentStep() != AuthStep.REVIEW) {
            throw new InvalidStepException("Expected REVIEW step for SELECT_SHIPPING");
        }
        requireAuth(session, token);

        if (req.addressId() != null) {
            ShippingAddress addr = shippingAddressRepository.findById(req.addressId())
                    .orElseThrow(() -> new InvalidStepException("Address not found: " + req.addressId()));
            if (!addr.getUser().getId().equals(session.getUser().getId())) {
                throw new UnauthorizedException("Address does not belong to this user");
            }
            session.setSelectedAddressId(req.addressId());
            session.setInlineAddressJson(null);
        } else if (req.newAddress() != null) {
            try {
                session.setInlineAddressJson(objectMapper.writeValueAsString(req.newAddress()));
                session.setSelectedAddressId(null);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize address", e);
            }
        }
        authSessionRepository.save(session);
        return buildReviewResponse(session, session.getAuthToken());
    }

    private AuthActionResponse buildReviewResponse(AuthSession session, String authToken) {
        User user = session.getUser();
        UserProfileResponse userProfile = new UserProfileResponse(
                user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhoneNumber());

        List<ShippingAddressResponse> addresses = shippingAddressRepository.findByUserId(user.getId())
                .stream().map(this::toAddressResponse).collect(Collectors.toList());

        CardResponse selectedCard = null;
        if (session.getSelectedCardId() != null) {
            selectedCard = cardRepository.findById(session.getSelectedCardId())
                    .map(this::toCardResponse).orElse(null);
        }

        ShippingAddressResponse selectedAddress = null;
        if (session.getSelectedAddressId() != null) {
            selectedAddress = shippingAddressRepository.findById(session.getSelectedAddressId())
                    .map(this::toAddressResponse).orElse(null);
        }

        // Auto-select default address if none chosen yet
        if (selectedAddress == null && !addresses.isEmpty()) {
            selectedAddress = addresses.stream()
                    .filter(ShippingAddressResponse::isDefault)
                    .findFirst()
                    .orElse(addresses.get(0));
            session.setSelectedAddressId(selectedAddress.id());
            authSessionRepository.save(session);
        }

        return new AuthActionResponse(AuthStep.REVIEW, null, authToken, userProfile,
                addresses, selectedCard, selectedAddress, null);
    }

    private void requireAuth(AuthSession session, String token) {
        if (token == null) throw new UnauthorizedException("Auth token required");
        // Validate token is valid JWT for this user
        UUID tokenUserId = authTokenService.validateToken(token);
        if (!tokenUserId.equals(session.getUser().getId())) {
            throw new UnauthorizedException("Token does not match session user");
        }
    }

    private CardResponse toCardResponse(Card card) {
        return new CardResponse(card.getId(), card.getLast4(),
                card.getExpirationDate(), card.getCardType(), card.getCardArtUrl());
    }

    private ShippingAddressResponse toAddressResponse(ShippingAddress addr) {
        return new ShippingAddressResponse(addr.getId(), addr.getLabel(),
                addr.getFirstName(), addr.getLastName(), addr.getAddress(),
                addr.getCity(), addr.getState(), addr.getZip(), addr.getCountry(), addr.isDefault());
    }
}
