package com.yala.dto.live;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One of the seller's own live streams (including finished ones) with its auto-generated
 * highlight clips, for the "Clips de tu live" panel in the seller dashboard.
 */
public record ResponseMyLiveDTO(
        Long id,
        String title,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        String recordingStatus,
        List<ResponseLiveClipDTO> clips) {
}
