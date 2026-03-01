package com.paze.checkout.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_challenges")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
