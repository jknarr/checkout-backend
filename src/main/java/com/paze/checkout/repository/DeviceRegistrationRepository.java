package com.paze.checkout.repository;

import com.paze.checkout.domain.DeviceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DeviceRegistrationRepository extends JpaRepository<DeviceRegistration, UUID> {
    List<DeviceRegistration> findByUserId(UUID userId);
}
