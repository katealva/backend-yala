package com.yala.model;

import com.yala.model.Bid;
import com.yala.model.Listing;
import com.yala.model.Notification;
import com.yala.model.Order;
import com.yala.model.Review;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min = 2, max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull
    @Email
    @Column(nullable = false, unique = true)
    private String email;

    @NotNull
    @Column(nullable = false)
    private String passwordHash;

    private String avatarUrl;

    @Builder.Default
    private Float reputation = 0.0f;

    @Builder.Default
    private Boolean isVerifiedSeller = false;

    @Builder.Default
    private Boolean isIdentityVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "seller", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Listing> listings = new ArrayList<>();

    @OneToMany(mappedBy = "bidder", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Bid> bids = new ArrayList<>();

    @OneToMany(mappedBy = "buyer", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Order> ordersAsBuyer = new ArrayList<>();

    @OneToMany(mappedBy = "seller", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Order> ordersAsSeller = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Notification> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Review> reviewsWritten = new ArrayList<>();

    @OneToMany(mappedBy = "recipient", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Review> reviewsReceived = new ArrayList<>();
}
