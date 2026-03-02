package com.paze.checkout.repository;

import com.paze.checkout.domain.PasskeyCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, UUID> {
    List<PasskeyCredential> findByUserIdAndRevokedAtIsNull(UUID userId);
    Optional<PasskeyCredential> findByCredentialIdAndRevokedAtIsNull(String credentialId);
    Optional<PasskeyCredential> findByCredentialId(String credentialId);
    boolean existsByUserIdAndRevokedAtIsNull(UUID userId);
}
