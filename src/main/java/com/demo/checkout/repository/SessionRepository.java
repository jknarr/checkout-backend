package com.demo.checkout.repository;

import com.demo.checkout.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
}
