package com.demo.checkout.repository;

import com.demo.checkout.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
