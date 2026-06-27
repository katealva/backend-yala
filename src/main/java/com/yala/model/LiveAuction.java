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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
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

/**
 * A flash auction created by the seller during a live stream. Unlike the regular
 * {@link Auction}, it is not backed by a {@link Listing} and is closed manually by
 * the seller (not by an {@code endsAt} timer). {@code currentPrice} is null until the
 * first bid; each new bid must be at least {@code currentPrice + bidIncrement}
 * (or {@code basePrice} for the first bid). Optimistic locking via {@code version}
 * guards concurrent bids, mirroring {@link Auction}.
 */
@Entity
@Table(name = "live_auctions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveAuction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @NotNull
    @Size(min = 2, max = 120)
    @Column(nullable = false, length = 120)
    private String title;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Float basePrice;

    /** Minimum step between consecutive bids (defaults to 1 sol). */
    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Float bidIncrement;

    /** Highest valid bid so far; null while there are no bids yet. */
    @Min(0)
    private Float currentPrice;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LiveAuctionStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Float winningAmount;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "live_stream_id", nullable = false)
    private LiveStream liveStream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @OneToMany(mappedBy = "liveAuction", fetch = FetchType.LAZY)
    @Builder.Default
    private List<LiveBid> bids = new ArrayList<>();
}
