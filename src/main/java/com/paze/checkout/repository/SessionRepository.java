package com.paze.checkout.repository;

import com.paze.checkout.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
}
