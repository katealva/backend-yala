package com.yala.repository;

import com.yala.model.LiveClip;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveClipRepository extends JpaRepository<LiveClip, Long> {

    /** Clips of a live, newest first, for the seller's "Clips de tu live" panel. */
    List<LiveClip> findByLiveStreamIdOrderByScoreDesc(Long liveStreamId);
}
