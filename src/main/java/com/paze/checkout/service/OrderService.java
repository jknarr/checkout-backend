package com.paze.checkout.service;

import com.paze.checkout.domain.Order;
import com.paze.checkout.dto.response.OrderResponse;
import com.paze.checkout.dto.response.ShippingAddressResponse;
import com.paze.checkout.exception.OrderNotFoundException;
import com.paze.checkout.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        ShippingAddressResponse shipping = new ShippingAddressResponse(
                null, null,
                order.getShippingFirstName(), order.getShippingLastName(),
                order.getShippingAddress(), order.getShippingCity(),
                order.getShippingState(), order.getShippingZip(),
                order.getShippingCountry(), false);

        return new OrderResponse(order.getId(), order.getTransactionId(), order.getCreatedAt(),
                shipping, order.getSubtotal(), order.getShippingCost(), order.getTax(), order.getTotal());
    }
}
