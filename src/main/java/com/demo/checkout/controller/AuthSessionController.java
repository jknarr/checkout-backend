package com.demo.checkout.controller;

import com.demo.checkout.dto.request.AuthActionRequest;
import com.demo.checkout.dto.request.InitAuthRequest;
import com.demo.checkout.dto.response.AuthActionResponse;
import com.demo.checkout.dto.response.InitAuthResponse;
import com.demo.checkout.service.AuthSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth Sessions", description = "Phone-based authentication with progressive disclosure")
public class AuthSessionController {

    private final AuthSessionService authSessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InitAuthResponse initAuth(@RequestBody @Valid InitAuthRequest req) {
        log.info("Auth init for phone: {}", req.phoneNumber());
        return authSessionService.initAuth(req.phoneNumber(), req.checkoutSessionId());
    }

    @PostMapping("/{authSessionId}/action")
    public AuthActionResponse action(
            @PathVariable UUID authSessionId,
            @RequestBody @Valid AuthActionRequest req,
            @RequestHeader(value = "Authorization", required = false) String bearer) {
        log.debug("Auth action: {} for session: {}", req.action(), authSessionId);
        String token = bearer != null ? bearer.replace("Bearer ", "") : null;
        return authSessionService.handleAction(authSessionId, req, token);
    }
}
