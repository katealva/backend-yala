package com.yala.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A user's application to become a seller. Captures the store data and tracks the Didit
 * KYC verification; when Didit approves it, the user is promoted to SELLER.
 */
@Entity
@Table(name = "seller_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private String storeName;

    /** Optional physical store address. */
    private String address;

    @NotNull
    @Column(nullable = false)
    private String phone;

    @NotNull
    @Column(nullable = false)
    private String cci;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerApplicationStatus status;

    /** Didit session id created for the KYC flow (nullable in demo/local). */
    private String diditSessionId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime decidedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
