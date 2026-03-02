package com.demo.checkout.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthStep currentStep;

    @Column(nullable = false)
    private UUID checkoutSessionId;

    @Column(nullable = false)
    private boolean deviceVerified;

    private UUID selectedCardId;

    private UUID selectedAddressId;

    @Column(columnDefinition = "TEXT")
    private String inlineAddressJson;

    @Column(columnDefinition = "TEXT")
    private String authToken;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        if (currentStep == null) currentStep = AuthStep.OTP_VERIFY;
    }
}
