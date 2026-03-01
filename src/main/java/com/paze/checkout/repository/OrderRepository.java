package com.paze.checkout.repository;

import com.paze.checkout.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
