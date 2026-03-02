package com.demo.checkout.repository;

import com.demo.checkout.domain.DeviceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRegistrationRepository extends JpaRepository<DeviceRegistration, UUID> {
    List<DeviceRegistration> findByUserId(UUID userId);
    List<DeviceRegistration> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<DeviceRegistration> findByIdAndUserId(UUID id, UUID userId);
    Optional<DeviceRegistration> findByUserIdAndPublicKeyJwk(UUID userId, String publicKeyJwk);
}
