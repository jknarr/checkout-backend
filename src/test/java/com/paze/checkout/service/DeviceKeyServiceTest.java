package com.paze.checkout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paze.checkout.domain.DeviceRegistration;
import com.paze.checkout.domain.User;
import com.paze.checkout.exception.DeviceVerificationException;
import com.paze.checkout.repository.DeviceRegistrationRepository;
import com.paze.checkout.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceKeyServiceTest {

    @Mock private DeviceRegistrationRepository deviceRegistrationRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private DeviceKeyService service;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new DeviceKeyService(deviceRegistrationRepository, userRepository, new ObjectMapper());
        ReflectionTestUtils.setField(service, "challengeExpirySeconds", 60);
        userId = UUID.randomUUID();
        testUser = User.builder().id(userId).phoneNumber("+15551234567")
                .firstName("Jane").lastName("Doe").email("jane@example.com").build();
    }

    @Test
    void issueChallenge_unknownDevice_throws() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRegistrationRepository.findById(deviceId)).thenReturn(Optional.empty());
        assertThrows(DeviceVerificationException.class, () -> service.issueChallenge(deviceId));
    }

    @Test
    void issueChallenge_knownDevice_returnsBase64Challenge() {
        UUID deviceId = UUID.randomUUID();
        DeviceRegistration reg = DeviceRegistration.builder().id(deviceId).user(testUser)
                .publicKeyJwk("{}").build();
        when(deviceRegistrationRepository.findById(deviceId)).thenReturn(Optional.of(reg));

        String challenge = service.issueChallenge(deviceId);

        assertNotNull(challenge);
        assertFalse(challenge.isEmpty());
    }

    @Test
    void verifyDevice_noActiveChallenge_throws() {
        UUID deviceId = UUID.randomUUID();
        DeviceRegistration reg = DeviceRegistration.builder().id(deviceId).user(testUser)
                .publicKeyJwk("{}").build();
        when(deviceRegistrationRepository.findById(deviceId)).thenReturn(Optional.of(reg));

        // No challenge issued — should throw
        assertThrows(DeviceVerificationException.class,
                () -> service.verifyDevice(deviceId, "badsig"));
    }

    @Test
    void getFirstDeviceId_noDevices_returnsEmpty() {
        when(deviceRegistrationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        assertTrue(service.getFirstDeviceId(userId).isEmpty());
    }

    @Test
    void getFirstDeviceId_hasDevices_returnsFirst() {
        UUID deviceId = UUID.randomUUID();
        DeviceRegistration reg = DeviceRegistration.builder().id(deviceId).user(testUser)
                .publicKeyJwk("{}").build();
        when(deviceRegistrationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(reg));

        assertEquals(deviceId, service.getFirstDeviceId(userId).orElseThrow());
    }

    @Test
    void registerDevice_userNotFound_throws() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.registerDevice(userId, "{}"));
    }

    @Test
    void registerDevice_success_returnsDeviceId() {
        UUID deviceId = UUID.randomUUID();
        DeviceRegistration saved = DeviceRegistration.builder().id(deviceId).user(testUser)
                .publicKeyJwk("{}").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(deviceRegistrationRepository.save(any())).thenReturn(saved);

        UUID result = service.registerDevice(userId, "{}");

        assertEquals(deviceId, result);
        verify(deviceRegistrationRepository).deleteByUserId(userId);
    }

    @Test
    void registerDevice_replacesExistingDevice() {
        UUID newDeviceId = UUID.randomUUID();
        DeviceRegistration saved = DeviceRegistration.builder().id(newDeviceId).user(testUser)
                .publicKeyJwk("{\"new\":true}").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(deviceRegistrationRepository.save(any())).thenReturn(saved);

        UUID result = service.registerDevice(userId, "{\"new\":true}");

        assertEquals(newDeviceId, result);
        verify(deviceRegistrationRepository).deleteByUserId(userId);
        verify(deviceRegistrationRepository).save(any());
    }

    @Test
    void issueChallengeForUserDevice_notOwned_returnsEmpty() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRegistrationRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.empty());

        Optional<String> result = service.issueChallengeForUserDevice(userId, deviceId);

        assertTrue(result.isEmpty());
    }
}
