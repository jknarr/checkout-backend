package com.demo.checkout.repository;

import com.demo.checkout.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {
    List<Card> findByUserId(UUID userId);
}
