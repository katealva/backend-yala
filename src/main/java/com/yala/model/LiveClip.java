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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * An auto-generated highlight clip cut from a finished live's recording (ADR-001).
 * {@code startMs}/{@code endMs} are offsets into the recording; the video file lives
 * in S3 at {@code s3Key} and is served via the public {@code url}. Title/caption come
 * from the highlight selection (signal timeline + LLM).
 */
@Entity
@Table(name = "live_clip")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveClip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "live_stream_id", nullable = false)
    private LiveStream liveStream;

    /** Ready-to-post title suggested for the clip. */
    @Column(length = 160)
    private String title;

    /** Ready-to-post caption/description for social media. */
    @Column(length = 600)
    private String caption;

    /** Why this moment was picked (for the seller to understand the suggestion). */
    @Column(length = 300)
    private String reason;

    /** Offset (ms from recording start) where the clip begins. */
    private Long startMs;

    /** Offset (ms from recording start) where the clip ends. */
    private Long endMs;

    /** Relevance score used to rank candidates. */
    private Double score;

    /** S3 key of the clip file. */
    private String s3Key;

    /** Public URL to download the clip. */
    @Column(length = 512)
    private String url;

    /** Aspect ratio / target format, e.g. "16:9" (MVP) or "9:16" (later). */
    private String format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LiveClipStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
