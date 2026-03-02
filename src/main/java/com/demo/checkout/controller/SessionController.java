package com.demo.checkout.controller;

import com.demo.checkout.dto.request.CreateSessionRequest;
import com.demo.checkout.dto.response.OrderResponse;
import com.demo.checkout.dto.response.SessionResponse;
import com.demo.checkout.service.SessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Checkout Sessions")
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse createSession(@RequestBody @Valid CreateSessionRequest req) {
        return sessionService.createSession(req);
    }

    @GetMapping("/{id}")
    public SessionResponse getSession(@PathVariable UUID id) {
        return sessionService.getSession(id);
    }

    @PostMapping("/{id}/submit")
    public OrderResponse submitCheckout(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String bearer) {
        log.info("Submit checkout for session: {}", id);
        return sessionService.submitCheckout(id, bearer.replace("Bearer ", ""));
    }
}
