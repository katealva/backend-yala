package com.yala.model;

/**
 * Lifecycle of an auto-generated highlight clip:
 * PENDING while the worker is cutting/uploading it, READY when downloadable,
 * FAILED when the pipeline could not produce the file.
 */
public enum LiveClipStatus {
    PENDING,
    READY,
    FAILED
}
