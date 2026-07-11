package com.yala.dto.live;

import com.yala.model.LiveClip;

/**
 * A highlight clip as shown to the seller in "Clips de tu live": title/caption to post,
 * why it was picked, the download URL, and where in the live it came from.
 */
public record ResponseLiveClipDTO(
        Long id,
        Long liveId,
        String title,
        String caption,
        String reason,
        Long startMs,
        Long endMs,
        Double score,
        String url,
        String format,
        String status) {

    public static ResponseLiveClipDTO from(LiveClip c) {
        return new ResponseLiveClipDTO(
                c.getId(),
                c.getLiveStream() != null ? c.getLiveStream().getId() : null,
                c.getTitle(),
                c.getCaption(),
                c.getReason(),
                c.getStartMs(),
                c.getEndMs(),
                c.getScore(),
                c.getUrl(),
                c.getFormat(),
                c.getStatus() != null ? c.getStatus().name() : null);
    }
}
