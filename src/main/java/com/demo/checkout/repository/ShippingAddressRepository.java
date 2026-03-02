package com.demo.checkout.repository;

import com.demo.checkout.domain.ShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, UUID> {
    List<ShippingAddress> findByUserId(UUID userId);
}
