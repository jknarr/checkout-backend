package com.demo.checkout.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passkey_credentials", indexes = @Index(name = "idx_passkey_user", columnList = "user_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(unique = true, nullable = false, length = 1024)
    private String credentialId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String publicKeyCose;

    @Column(nullable = false)
    private long signCount;

    @Column(length = 64)
    private String aaguid;

    @Column(columnDefinition = "TEXT")
    private String transports;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant lastUsedAt;

    private Instant revokedAt;

    @Column(length = 100)
    private String label;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
