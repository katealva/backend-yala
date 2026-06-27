package com.yala.event;

/** Published when a live stream ends. Consumed by the listener that broadcasts LIVE_ENDED. */
public record LiveEndedEvent(Long liveStreamId) {
}
