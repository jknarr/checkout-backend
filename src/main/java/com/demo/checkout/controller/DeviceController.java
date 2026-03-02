package com.demo.checkout.controller;

import com.demo.checkout.dto.request.RegisterDeviceRequest;
import com.demo.checkout.dto.response.RegisterDeviceResponse;
import com.demo.checkout.service.AuthTokenService;
import com.demo.checkout.service.DeviceKeyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/device")
@RequiredArgsConstructor
@Tag(name = "Device Registration")
public class DeviceController {

    private final DeviceKeyService deviceKeyService;
    private final AuthTokenService authTokenService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterDeviceResponse registerDevice(
            @RequestBody @Valid RegisterDeviceRequest req,
            @RequestHeader("Authorization") String bearer) {
        UUID userId = authTokenService.validateToken(bearer.replace("Bearer ", ""));
        UUID deviceId = deviceKeyService.registerDevice(userId, req.publicKey());
        return new RegisterDeviceResponse(deviceId);
    }
}
