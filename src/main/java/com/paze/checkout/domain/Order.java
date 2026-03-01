package com.paze.checkout.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "session_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Session session;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false) private String shippingFirstName;
    @Column(nullable = false) private String shippingLastName;
    @Column(nullable = false) private String shippingEmail;
    @Column(nullable = false) private String shippingAddress;
    @Column(nullable = false) private String shippingCity;
    @Column(nullable = false) private String shippingState;
    @Column(nullable = false) private String shippingZip;
    @Column(nullable = false) private String shippingCountry;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
