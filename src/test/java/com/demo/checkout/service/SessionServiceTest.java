package com.demo.checkout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.demo.checkout.domain.*;
import com.demo.checkout.dto.response.OrderResponse;
import com.demo.checkout.exception.*;
import com.demo.checkout.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CardRepository cardRepository;
    @Mock private ShippingAddressRepository shippingAddressRepository;
    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private AuthTokenService authTokenService;
    @InjectMocks private SessionService service;

    private UUID sessionId;
    private UUID userId;
    private UUID cardId;
    private UUID addressId;
    private String authToken;
    private User testUser;
    private Session checkoutSession;
    private AuthSession authSession;
    private Card card;
    private ShippingAddress address;

    @BeforeEach
    void setUp() {
        service = new SessionService(sessionRepository, orderRepository, cardRepository,
                shippingAddressRepository, authSessionRepository, authTokenService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "expiryMinutes", 30);

        sessionId = UUID.randomUUID();
        userId = UUID.randomUUID();
        cardId = UUID.randomUUID();
        addressId = UUID.randomUUID();
        authToken = "test.jwt.token";

        testUser = User.builder().id(userId)
                .firstName("Jane").lastName("Doe").email("jane@example.com")
                .phoneNumber("+15551234567").build();

        checkoutSession = Session.builder()
                .id(sessionId)
                .merchantId("demo-merchant-001")
                .status(SessionStatus.PENDING)
                .cartJson("[{\"productId\":\"p1\",\"name\":\"Widget\",\"price\":29.99,\"quantity\":2}]")
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

        card = Card.builder().id(cardId).user(testUser)
                .last4("4242").cardType("Visa").expirationDate("12/27").build();

        address = ShippingAddress.builder().id(addressId).user(testUser)
                .label("Home").firstName("Jane").lastName("Doe")
                .address("123 Main St").city("San Francisco").state("CA")
                .zip("94102").country("US").isDefault(true).build();

        authSession = AuthSession.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .checkoutSessionId(sessionId)
                .currentStep(AuthStep.REVIEW)
                .deviceVerified(false)
                .selectedCardId(cardId)
                .selectedAddressId(addressId)
                .authToken(authToken)
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
    }

    @Test
    void submitCheckout_success_returnsOrderWithCorrectTotals() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(checkoutSession));
        when(authTokenService.validateToken(authToken)).thenReturn(userId);
        when(authSessionRepository.findByAuthToken(authToken)).thenReturn(Optional.of(authSession));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(address));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            o.setCreatedAt(Instant.now());
            return o;
        });
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = service.submitCheckout(sessionId, authToken);

        assertNotNull(result.orderId());
        assertEquals(new BigDecimal("59.98"), result.subtotal());
        assertEquals(new BigDecimal("9.99"), result.shippingCost()); // subtotal < 75
        assertEquals(new BigDecimal("4.80"), result.tax());          // 59.98 * 0.08
        assertEquals(new BigDecimal("74.77"), result.total());
        assertEquals("Jane", result.shipping().firstName());
        assertEquals("San Francisco", result.shipping().city());
    }

    @Test
    void submitCheckout_freeShipping_whenSubtotalOver75() throws Exception {
        // 3 × 29.99 = 89.97 → free shipping
        checkoutSession = Session.builder()
                .id(sessionId).merchantId("demo-merchant-001").status(SessionStatus.PENDING)
                .cartJson("[{\"productId\":\"p1\",\"name\":\"Widget\",\"price\":29.99,\"quantity\":3}]")
                .expiresAt(Instant.now().plusSeconds(1800)).build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(checkoutSession));
        when(authTokenService.validateToken(authToken)).thenReturn(userId);
        when(authSessionRepository.findByAuthToken(authToken)).thenReturn(Optional.of(authSession));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(shippingAddressRepository.findById(addressId)).thenReturn(Optional.of(address));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            o.setCreatedAt(Instant.now());
            return o;
        });
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = service.submitCheckout(sessionId, authToken);

        assertEquals(BigDecimal.ZERO, result.shippingCost());
    }

    @Test
    void submitCheckout_alreadyCompleted_throws() {
        checkoutSession.setStatus(SessionStatus.COMPLETED);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(checkoutSession));

        assertThrows(SessionAlreadySubmittedException.class,
                () -> service.submitCheckout(sessionId, authToken));
    }

    @Test
    void submitCheckout_authSessionForDifferentCheckoutSession_throws() {
        authSession = AuthSession.builder()
                .id(UUID.randomUUID()).user(testUser)
                .checkoutSessionId(UUID.randomUUID()) // different session
                .currentStep(AuthStep.REVIEW).deviceVerified(false)
                .selectedCardId(cardId).authToken(authToken)
                .expiresAt(Instant.now().plusSeconds(1800)).build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(checkoutSession));
        when(authTokenService.validateToken(authToken)).thenReturn(userId);
        when(authSessionRepository.findByAuthToken(authToken)).thenReturn(Optional.of(authSession));

        assertThrows(UnauthorizedException.class,
                () -> service.submitCheckout(sessionId, authToken));
    }

    @Test
    void submitCheckout_noCardSelected_throws() {
        authSession.setSelectedCardId(null);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(checkoutSession));
        when(authTokenService.validateToken(authToken)).thenReturn(userId);
        when(authSessionRepository.findByAuthToken(authToken)).thenReturn(Optional.of(authSession));

        assertThrows(InvalidStepException.class,
                () -> service.submitCheckout(sessionId, authToken));
    }

    @Test
    void submitCheckout_sessionNotFound_throws() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        assertThrows(SessionNotFoundException.class,
                () -> service.submitCheckout(sessionId, authToken));
    }
}
