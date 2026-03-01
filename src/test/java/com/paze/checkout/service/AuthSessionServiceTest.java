package com.paze.checkout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paze.checkout.domain.*;
import com.paze.checkout.dto.request.AuthActionRequest;
import com.paze.checkout.dto.response.AuthActionResponse;
import com.paze.checkout.dto.response.InitAuthResponse;
import com.paze.checkout.exception.*;
import com.paze.checkout.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CardRepository cardRepository;
    @Mock private ShippingAddressRepository shippingAddressRepository;
    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private OtpService otpService;
    @Mock private AuthTokenService authTokenService;
    @Mock private DeviceKeyService deviceKeyService;
    @Mock private BCryptPasswordEncoder bcrypt;
    @InjectMocks private AuthSessionService service;

    private User testUser;
    private UUID userId;
    private UUID checkoutSessionId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "sessionExpiryMinutes", 30);
        userId = UUID.randomUUID();
        checkoutSessionId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .phoneNumber("+15551234567")
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .build();
        service = new AuthSessionService(userRepository, cardRepository, shippingAddressRepository,
                authSessionRepository, otpService, authTokenService, deviceKeyService,
                bcrypt, new ObjectMapper());
        ReflectionTestUtils.setField(service, "sessionExpiryMinutes", 30);
    }

    @Test
    void initAuth_unknownPhone_throwsUserNotFoundException() {
        when(userRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class,
                () -> service.initAuth("+10000000000", checkoutSessionId));
    }

    @Test
    void initAuth_knownUser_returnsOtpVerifyStep() {
        when(userRepository.findByPhoneNumber("+15551234567")).thenReturn(Optional.of(testUser));
        OtpChallenge challenge = OtpChallenge.builder()
                .id(UUID.randomUUID()).phoneNumber("+15551234567")
                .code("123456").expiresAt(Instant.now().plusSeconds(600)).used(false).build();
        when(otpService.createChallenge(any())).thenReturn(challenge);
        when(otpService.isMockMode()).thenReturn(true);
        when(authSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deviceKeyService.getFirstDeviceId(any())).thenReturn(Optional.empty());

        InitAuthResponse response = service.initAuth("+15551234567", checkoutSessionId);

        assertEquals(AuthStep.OTP_VERIFY, response.currentStep());
        assertEquals("123456", response.otpCode());
    }

    @Test
    void handleAction_verifyOtp_advancesToCardSelect() {
        UUID authSessionId = UUID.randomUUID();
        AuthSession session = AuthSession.builder()
                .id(authSessionId)
                .user(testUser)
                .checkoutSessionId(checkoutSessionId)
                .currentStep(AuthStep.OTP_VERIFY)
                .deviceVerified(false)
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

        UUID challengeId = UUID.randomUUID();
        AuthActionRequest req = new AuthActionRequest(
                ActionType.VERIFY_OTP, challengeId, "123456",
                null, null, null, null, null, null);

        when(authSessionRepository.findById(authSessionId)).thenReturn(Optional.of(session));
        when(authSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.findByUserId(userId)).thenReturn(List.of());
        when(deviceKeyService.getFirstDeviceId(userId)).thenReturn(Optional.empty());

        AuthActionResponse response = service.handleAction(authSessionId, req, null);

        assertEquals(AuthStep.CARD_SELECT, response.currentStep());
        verify(otpService).verify(challengeId, "+15551234567", "123456");
    }

    @Test
    void handleAction_verifyOtp_wrongStep_throws() {
        UUID authSessionId = UUID.randomUUID();
        AuthSession session = AuthSession.builder()
                .id(authSessionId).user(testUser).checkoutSessionId(checkoutSessionId)
                .currentStep(AuthStep.CARD_SELECT).deviceVerified(false)
                .expiresAt(Instant.now().plusSeconds(1800)).build();

        AuthActionRequest req = new AuthActionRequest(
                ActionType.VERIFY_OTP, UUID.randomUUID(), "123456",
                null, null, null, null, null, null);

        when(authSessionRepository.findById(authSessionId)).thenReturn(Optional.of(session));

        assertThrows(InvalidStepException.class, () -> service.handleAction(authSessionId, req, null));
    }

    @Test
    void handleAction_selectCard_withoutDeviceVerify_advancesToCvv() {
        UUID authSessionId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        AuthSession session = AuthSession.builder()
                .id(authSessionId).user(testUser).checkoutSessionId(checkoutSessionId)
                .currentStep(AuthStep.CARD_SELECT).deviceVerified(false)
                .expiresAt(Instant.now().plusSeconds(1800)).build();

        Card card = Card.builder().id(cardId).user(testUser)
                .last4("4242").cardType("Visa").cardArtUrl("/card-art/visa.svg")
                .expirationDate("12/27").cvvHash("hashed").build();

        AuthActionRequest req = new AuthActionRequest(
                ActionType.SELECT_CARD, null, null,
                null, null, cardId, null, null, null);

        when(authSessionRepository.findById(authSessionId)).thenReturn(Optional.of(session));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(authSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthActionResponse response = service.handleAction(authSessionId, req, null);

        assertEquals(AuthStep.CVV, response.currentStep());
        assertNotNull(response.selectedCard());
        assertEquals("4242", response.selectedCard().last4());
    }

    @Test
    void handleAction_selectCard_cardBelongsToDifferentUser_throws() {
        UUID authSessionId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        AuthSession session = AuthSession.builder()
                .id(authSessionId).user(testUser).checkoutSessionId(checkoutSessionId)
                .currentStep(AuthStep.CARD_SELECT).deviceVerified(false)
                .expiresAt(Instant.now().plusSeconds(1800)).build();
        Card card = Card.builder().id(cardId).user(otherUser).build();

        AuthActionRequest req = new AuthActionRequest(
                ActionType.SELECT_CARD, null, null,
                null, null, cardId, null, null, null);

        when(authSessionRepository.findById(authSessionId)).thenReturn(Optional.of(session));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));

        assertThrows(UnauthorizedException.class, () -> service.handleAction(authSessionId, req, null));
    }

    @Test
    void handleAction_verifyCvv_correct_advancesToReview() {
        UUID authSessionId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        String authToken = "test.jwt.token";
        AuthSession session = AuthSession.builder()
                .id(authSessionId).user(testUser).checkoutSessionId(checkoutSessionId)
                .currentStep(AuthStep.CVV).deviceVerified(false).selectedCardId(cardId)
                .expiresAt(Instant.now().plusSeconds(1800)).build();
        Card card = Card.builder().id(cardId).user(testUser)
                .last4("4242").cardType("Visa").cardArtUrl("/card-art/visa.svg")
                .expirationDate("12/27").cvvHash("$2a$10$hashed").build();

        AuthActionRequest req = new AuthActionRequest(
                ActionType.VERIFY_CVV, null, null,
                null, null, cardId, "123", null, null);

        when(authSessionRepository.findById(authSessionId)).thenReturn(Optional.of(session));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(bcrypt.matches("123", card.getCvvHash())).thenReturn(true);
        when(authTokenService.generateToken(userId)).thenReturn(authToken);
        when(authSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shippingAddressRepository.findByUserId(userId)).thenReturn(List.of());

        AuthActionResponse response = service.handleAction(authSessionId, req, null);

        assertEquals(AuthStep.REVIEW, response.currentStep());
        assertEquals(authToken, response.authToken());
    }

    @Test
    void handleAction_deviceVerify_autoSelectsFirstCard_andAdvancesToReview() {
        UUID authSessionId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        String authToken = "test.jwt.token";

        AuthSession session = AuthSession.builder()
                .id(authSessionId).user(testUser).checkoutSessionId(checkoutSessionId)
                .currentStep(AuthStep.CARD_SELECT).deviceVerified(false)
                .expiresAt(Instant.now().plusSeconds(1800)).build();

        Card card = Card.builder().id(cardId).user(testUser)
                .last4("4242").cardType("Visa").cardArtUrl("/card-art/visa.svg")
                .expirationDate("12/27").cvvHash("hashed").build();

        AuthActionRequest req = new AuthActionRequest(
                ActionType.DEVICE_VERIFY, null, null,
                deviceId, "sig==", null, null, null, null);

        when(authSessionRepository.findById(authSessionId)).thenReturn(Optional.of(session));
        when(deviceKeyService.verifyDevice(deviceId, "sig==")).thenReturn(userId);
        when(cardRepository.findByUserId(userId)).thenReturn(List.of(card));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(authTokenService.generateToken(userId)).thenReturn(authToken);
        when(authSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shippingAddressRepository.findByUserId(userId)).thenReturn(List.of());

        AuthActionResponse response = service.handleAction(authSessionId, req, null);

        assertEquals(AuthStep.REVIEW, response.currentStep());
        assertEquals(authToken, response.authToken());
        assertNotNull(response.selectedCard());
        assertEquals("4242", response.selectedCard().last4());
    }

    @Test
    void handleAction_deviceVerify_noCards_throws() {
        UUID authSessionId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        AuthSession session = AuthSession.builder()
                .id(authSessionId).user(testUser).checkoutSessionId(checkoutSessionId)
                .currentStep(AuthStep.CARD_SELECT).deviceVerified(false)
                .expiresAt(Instant.now().plusSeconds(1800)).build();

        AuthActionRequest req = new AuthActionRequest(
                ActionType.DEVICE_VERIFY, null, null,
                deviceId, "sig==", null, null, null, null);

        when(authSessionRepository.findById(authSessionId)).thenReturn(Optional.of(session));
        when(deviceKeyService.verifyDevice(deviceId, "sig==")).thenReturn(userId);
        when(cardRepository.findByUserId(userId)).thenReturn(List.of());

        assertThrows(InvalidStepException.class, () -> service.handleAction(authSessionId, req, null));
    }

    @Test
    void handleAction_verifyCvv_wrong_throwsCvvInvalidException() {
        UUID authSessionId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        AuthSession session = AuthSession.builder()
                .id(authSessionId).user(testUser).checkoutSessionId(checkoutSessionId)
                .currentStep(AuthStep.CVV).deviceVerified(false).selectedCardId(cardId)
                .expiresAt(Instant.now().plusSeconds(1800)).build();
        Card card = Card.builder().id(cardId).user(testUser).cvvHash("$2a$10$hashed").build();

        AuthActionRequest req = new AuthActionRequest(
                ActionType.VERIFY_CVV, null, null,
                null, null, cardId, "000", null, null);

        when(authSessionRepository.findById(authSessionId)).thenReturn(Optional.of(session));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(bcrypt.matches("000", card.getCvvHash())).thenReturn(false);

        assertThrows(CvvInvalidException.class, () -> service.handleAction(authSessionId, req, null));
    }
}
