package com.demo.checkout.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.demo.checkout.domain.SessionStatus;
import com.demo.checkout.dto.request.CartItemDto;
import com.demo.checkout.dto.request.CreateSessionRequest;
import com.demo.checkout.dto.response.SessionResponse;
import com.demo.checkout.exception.GlobalExceptionHandler;
import com.demo.checkout.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
@Import(GlobalExceptionHandler.class)
class SessionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private SessionService sessionService;

    @Test
    void createSession_returns201() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.createSession(any()))
                .thenReturn(new SessionResponse(sessionId, "demo-merchant-001", SessionStatus.PENDING));

        CreateSessionRequest req = new CreateSessionRequest("demo-merchant-001",
                List.of(new CartItemDto("p1", "Widget", 29.99, 2, null)));

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
