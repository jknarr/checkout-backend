package com.paze.checkout.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paze.checkout.domain.AuthStep;
import com.paze.checkout.dto.request.InitAuthRequest;
import com.paze.checkout.dto.response.InitAuthResponse;
import com.paze.checkout.exception.GlobalExceptionHandler;
import com.paze.checkout.exception.UserNotFoundException;
import com.paze.checkout.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthSessionController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private AuthSessionService authSessionService;

    @Test
    void initAuth_returns201() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID authSessionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();

        when(authSessionService.initAuth(eq("+15551234567"), any()))
                .thenReturn(new InitAuthResponse(authSessionId, AuthStep.OTP_VERIFY, challengeId, "123456", null));

        InitAuthRequest req = new InitAuthRequest("+15551234567", sessionId);

        mockMvc.perform(post("/api/v1/auth/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentStep").value("OTP_VERIFY"))
                .andExpect(jsonPath("$.otpCode").value("123456"));
    }

    @Test
    void initAuth_userNotFound_returns404() throws Exception {
        when(authSessionService.initAuth(any(), any()))
                .thenThrow(new UserNotFoundException("No account found"));

        InitAuthRequest req = new InitAuthRequest("+10000000000", UUID.randomUUID());

        mockMvc.perform(post("/api/v1/auth/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
