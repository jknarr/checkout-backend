package com.demo.checkout.controller;

import com.demo.checkout.service.AuthTokenService;
import com.demo.checkout.service.PasskeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasskeyController.class)
class PasskeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasskeyService passkeyService;

    @MockBean
    private AuthTokenService authTokenService;

    @Test
    void list_returnsPasskeysForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authTokenService.validateToken("token")).thenReturn(userId);
        when(passkeyService.listCredentials(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auth/passkeys")
                        .header("Authorization", "Bearer token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void revoke_returnsNoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        String credentialId = "cred-id";
        when(authTokenService.validateToken("token")).thenReturn(userId);

        mockMvc.perform(delete("/api/v1/auth/passkeys/{credentialId}", credentialId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent());

        verify(passkeyService).revokeCredential(userId, credentialId);
    }
}
