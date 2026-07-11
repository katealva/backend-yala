package com.yala.dto.live;

/**
 * The text transcribed from an audio chunk of the live (ADR-002 Fase 3). The client streams
 * short overlapping chunks of the seller's mic and stitches these transcripts into a rolling
 * buffer to detect the trigger phrase.
 */
public record ResponseTranscriptDTO(String text) {
}
