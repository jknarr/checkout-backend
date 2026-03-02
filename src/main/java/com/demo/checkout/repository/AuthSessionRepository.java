package com.demo.checkout.repository;

import com.demo.checkout.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    Optional<AuthSession> findByAuthToken(String authToken);
}
