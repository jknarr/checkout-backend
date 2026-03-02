package com.demo.checkout.controller;

import com.demo.checkout.dto.response.OrderResponse;
import com.demo.checkout.service.OrderService;
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
