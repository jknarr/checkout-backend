package com.paze.checkout.controller;

import com.paze.checkout.dto.response.OrderResponse;
import com.paze.checkout.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }
}
