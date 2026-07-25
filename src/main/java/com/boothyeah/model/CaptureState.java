package com.boothyeah.model;

/**
 * Represents the possible states during the 3-photo capture sequence.
 * Used by CaptureSessionManager to drive the state machine cleanly.
 */
public enum CaptureState {
    /** Waiting for user to initiate a capture (shows camera feed, "Take Photo" button) */
    WAITING_TO_START,

    /** Countdown timer is running (3, 2, 1...) */
    COUNTDOWN,

    /** Grabbing the frame from the camera */
    CAPTURING,

    /** Showing the captured photo; user can Accept or Retake */
    PREVIEWING,

    /** All 3 photos have been accepted; ready for compositing */
    ALL_CAPTURED
}
