package com.paze.checkout.repository;

import com.paze.checkout.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    Optional<AuthSession> findByAuthToken(String authToken);
}
