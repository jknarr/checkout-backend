package com.paze.checkout.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paze.checkout.domain.*;
import com.paze.checkout.dto.request.CartItemDto;
import com.paze.checkout.dto.response.OrderResponse;
import com.paze.checkout.dto.response.SessionResponse;
import com.paze.checkout.dto.response.ShippingAddressResponse;
import com.paze.checkout.exception.*;
import com.paze.checkout.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final CardRepository cardRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AuthTokenService authTokenService;
    private final ObjectMapper objectMapper;

    @Value("${paze.session.expiry-minutes}")
    private int expiryMinutes;

    @Transactional
    public SessionResponse createSession(com.paze.checkout.dto.request.CreateSessionRequest req) {
        String cartJson;
        try {
            cartJson = objectMapper.writeValueAsString(req.cart());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize cart", e);
        }
        Session session = Session.builder()
                .merchantId(req.merchantId())
                .status(SessionStatus.PENDING)
                .cartJson(cartJson)
                .expiresAt(Instant.now().plusSeconds(expiryMinutes * 60L))
                .build();
        session = sessionRepository.save(session);
        log.info("Checkout session created: {}", session.getId());
        return new SessionResponse(session.getId(), session.getMerchantId(), session.getStatus());
    }

    public SessionResponse getSession(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));
        return new SessionResponse(session.getId(), session.getMerchantId(), session.getStatus());
    }

    @Transactional
    public OrderResponse submitCheckout(UUID sessionId, String authToken) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));

        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new SessionAlreadySubmittedException("Session already submitted: " + sessionId);
        }

        UUID userId = authTokenService.validateToken(authToken);

        AuthSession authSession = authSessionRepository.findByAuthToken(authToken)
                .orElseThrow(() -> new UnauthorizedException("No auth session found for token"));

        if (!sessionId.equals(authSession.getCheckoutSessionId())) {
            throw new UnauthorizedException("Auth session was not created for this checkout session");
        }

        if (authSession.getSelectedCardId() == null) {
            throw new InvalidStepException("No card selected in auth session");
        }

        Card card = cardRepository.findById(authSession.getSelectedCardId())
                .orElseThrow(() -> new InvalidStepException("Selected card not found"));
        if (!card.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Card does not belong to authenticated user");
        }

        // Resolve shipping from AuthSession
        String shippingFirstName, shippingLastName, shippingEmail,
                shippingAddr, shippingCity, shippingState, shippingZip, shippingCountry;

        if (authSession.getSelectedAddressId() != null) {
            ShippingAddress addr = shippingAddressRepository.findById(authSession.getSelectedAddressId())
                    .orElseThrow(() -> new InvalidStepException("Selected address not found"));
            shippingFirstName = addr.getFirstName();
            shippingLastName  = addr.getLastName();
            shippingEmail     = authSession.getUser().getEmail();
            shippingAddr      = addr.getAddress();
            shippingCity      = addr.getCity();
            shippingState     = addr.getState();
            shippingZip       = addr.getZip();
            shippingCountry   = addr.getCountry();
        } else if (authSession.getInlineAddressJson() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> a = objectMapper.readValue(authSession.getInlineAddressJson(), Map.class);
                shippingFirstName = a.getOrDefault("firstName", "");
                shippingLastName  = a.getOrDefault("lastName", "");
                shippingEmail     = a.getOrDefault("email", authSession.getUser().getEmail());
                shippingAddr      = a.getOrDefault("address", "");
                shippingCity      = a.getOrDefault("city", "");
                shippingState     = a.getOrDefault("state", "");
                shippingZip       = a.getOrDefault("zip", "");
                shippingCountry   = a.getOrDefault("country", "US");
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse inline address", e);
            }
        } else {
            List<ShippingAddress> addrs = shippingAddressRepository.findByUserId(userId);
            if (addrs.isEmpty()) throw new InvalidStepException("No shipping address available");
            ShippingAddress addr = addrs.stream().filter(ShippingAddress::isDefault)
                    .findFirst().orElse(addrs.get(0));
            shippingFirstName = addr.getFirstName();
            shippingLastName  = addr.getLastName();
            shippingEmail     = authSession.getUser().getEmail();
            shippingAddr      = addr.getAddress();
            shippingCity      = addr.getCity();
            shippingState     = addr.getState();
            shippingZip       = addr.getZip();
            shippingCountry   = addr.getCountry();
        }

        // Calculate totals
        List<CartItemDto> cartItems;
        try {
            cartItems = objectMapper.readValue(session.getCartJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CartItemDto.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse cart", e);
        }

        BigDecimal subtotal = cartItems.stream()
                .map(i -> BigDecimal.valueOf(i.price()).multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal shippingCost = subtotal.compareTo(BigDecimal.valueOf(75)) >= 0
                ? BigDecimal.ZERO : BigDecimal.valueOf(9.99);
        BigDecimal tax   = subtotal.multiply(BigDecimal.valueOf(0.08)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(shippingCost).add(tax).setScale(2, RoundingMode.HALF_UP);

        String transactionId = "txn_" + Instant.now().toEpochMilli() + "_"
                + UUID.randomUUID().toString().substring(0, 8);

        Order order = Order.builder()
                .session(session)
                .transactionId(transactionId)
                .shippingFirstName(shippingFirstName).shippingLastName(shippingLastName)
                .shippingEmail(shippingEmail).shippingAddress(shippingAddr)
                .shippingCity(shippingCity).shippingState(shippingState)
                .shippingZip(shippingZip).shippingCountry(shippingCountry)
                .subtotal(subtotal).shippingCost(shippingCost).tax(tax).total(total)
                .build();
        order = orderRepository.save(order);

        session.setStatus(SessionStatus.COMPLETED);
        sessionRepository.save(session);
        authSession.setCurrentStep(AuthStep.COMPLETED);
        authSessionRepository.save(authSession);

        log.info("Order created: {} for session: {}", order.getId(), sessionId);

        ShippingAddressResponse shippingResponse = new ShippingAddressResponse(
                null, null, shippingFirstName, shippingLastName,
                shippingAddr, shippingCity, shippingState, shippingZip, shippingCountry, false);

        return new OrderResponse(order.getId(), transactionId, order.getCreatedAt(),
                shippingResponse, subtotal, shippingCost, tax, total);
    }
}
