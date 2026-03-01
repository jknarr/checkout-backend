package com.paze.checkout.controller;

import com.paze.checkout.dto.response.OrderResponse;
import com.paze.checkout.dto.response.ShippingAddressResponse;
import com.paze.checkout.exception.GlobalExceptionHandler;
import com.paze.checkout.exception.OrderNotFoundException;
import com.paze.checkout.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private OrderService orderService;

    @Test
    void getOrder_found_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        ShippingAddressResponse shipping = new ShippingAddressResponse(
                null, null, "Jane", "Doe", "123 Main St",
                "San Francisco", "CA", "94102", "US", false);
        OrderResponse response = new OrderResponse(orderId, "txn_abc123", Instant.now(),
                shipping, new BigDecimal("59.98"), new BigDecimal("9.99"),
                new BigDecimal("4.80"), new BigDecimal("74.77"));

        when(orderService.getOrder(orderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.transactionId").value("txn_abc123"))
                .andExpect(jsonPath("$.subtotal").value(59.98))
                .andExpect(jsonPath("$.shipping.city").value("San Francisco"));
    }

    @Test
    void getOrder_notFound_returns404() throws Exception {
        when(orderService.getOrder(any())).thenThrow(new OrderNotFoundException("not found"));

        mockMvc.perform(get("/api/v1/orders/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
