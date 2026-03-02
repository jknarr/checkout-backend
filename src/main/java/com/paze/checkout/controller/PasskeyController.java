package com.paze.checkout.controller;

import com.paze.checkout.dto.response.PasskeyResponse;
import com.paze.checkout.service.AuthTokenService;
import com.paze.checkout.service.PasskeyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/passkeys")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Passkeys")
public class PasskeyController {

    private final PasskeyService passkeyService;
    private final AuthTokenService authTokenService;

    @GetMapping
    public List<PasskeyResponse> list(@RequestHeader("Authorization") String bearer) {
        UUID userId = authTokenService.validateToken(bearer.replace("Bearer ", ""));
        return passkeyService.listCredentials(userId).stream()
                .map(c -> new PasskeyResponse(c.getCredentialId(), c.getLabel(), c.getCreatedAt(), c.getLastUsedAt()))
                .toList();
    }

    @DeleteMapping("/{credentialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String credentialId,
                       @RequestHeader("Authorization") String bearer) {
        UUID userId = authTokenService.validateToken(bearer.replace("Bearer ", ""));
        passkeyService.revokeCredential(userId, credentialId);
    }
}
