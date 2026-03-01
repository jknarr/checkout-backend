package com.paze.checkout.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false)
    private String last4;

    @Column(nullable = false)
    private String expirationDate;

    @Column(nullable = false)
    private String cardType;

    @Column(nullable = false)
    private String cardArtUrl;

    @Column(nullable = false)
    private String cvvHash;
}
