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
 * A seller's live video broadcast. The actual audio/video flows through LiveKit
 * (identified by {@code roomName}); this entity only tracks the session and is the
 * anchor for the flash auctions and comments that happen during the stream.
 */
@Entity
@Table(name = "live_streams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveStream {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min = 2, max = 120)
    @Column(nullable = false, length = 120)
    private String title;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LiveStatus status;

    /** LiveKit room name; unique per stream. */
    @NotNull
    @Column(nullable = false, unique = true)
    private String roomName;

    private String coverImageUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @OneToMany(mappedBy = "liveStream", fetch = FetchType.LAZY)
    @Builder.Default
    private List<LiveAuction> auctions = new ArrayList<>();

    @OneToMany(mappedBy = "liveStream", fetch = FetchType.LAZY)
    @Builder.Default
    private List<LiveComment> comments = new ArrayList<>();
}
