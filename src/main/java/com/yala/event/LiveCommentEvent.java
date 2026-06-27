package com.yala.event;

/** Published when a viewer posts a chat comment. Consumed by the listener that broadcasts it. */
public record LiveCommentEvent(Long commentId) {
}
