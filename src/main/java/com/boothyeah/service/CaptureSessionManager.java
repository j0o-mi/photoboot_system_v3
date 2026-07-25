package com.boothyeah.service;

import com.boothyeah.model.CaptureSession;
import com.boothyeah.model.CaptureState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * State machine managing the 3-photo capture flow with per-photo retake support.
 *
 * State transitions:
 *   WAITING_TO_START → startCapture() → COUNTDOWN
 *   COUNTDOWN → (timer expires) → CAPTURING
 *   CAPTURING → storePhoto() → PREVIEWING
 *   PREVIEWING → accept() → COUNTDOWN (if more photos needed)
 *   PREVIEWING → accept() → ALL_CAPTURED (if all photos done)
 *   PREVIEWING → retake() → COUNTDOWN (re-capture same slot)
 *
 * The RETAKE LOGIC is simple: currentIndex doesn't change on retake.
 * On accept(), currentIndex++ moves to the next slot. This means each photo
 * can be retaken independently without affecting others.
 */
public class CaptureSessionManager {
    private static final Logger log = LoggerFactory.getLogger(CaptureSessionManager.class);

    private final CaptureSession session;
    /** Callback invoked on every state change — controller uses this to update UI */
    private Consumer<CaptureState> onStateChange;

    public CaptureSessionManager(CaptureSession session) {
        this.session = session;
    }

    public void setOnStateChange(Consumer<CaptureState> callback) {
        this.onStateChange = callback;
    }

    /** Transition to COUNTDOWN state — starts the capture sequence for current slot */
    public void startCapture() {
        transition(CaptureState.COUNTDOWN);
    }

    /** Called when countdown finishes — transition to CAPTURING */
    public void countdownFinished() {
        transition(CaptureState.CAPTURING);
    }

    /** Store the captured photo and transition to PREVIEWING */
    public void storePhoto(BufferedImage photo) {
        session.storePhoto(photo);
        transition(CaptureState.PREVIEWING);
        log.info("Photo {} captured and stored for preview", session.getCurrentIndex() + 1);
    }

    /**
     * ACCEPT the current photo and move to next slot.
     * If all photos are captured, transition to ALL_CAPTURED.
     * Otherwise, transition to COUNTDOWN for the next photo.
     */
    public void accept() {
        session.acceptCurrent(); // Increments currentIndex
        if (session.isComplete()) {
            transition(CaptureState.ALL_CAPTURED);
            log.info("All {} photos captured!", session.getTotalPhotos());
        } else {
            // Return to waiting so user presses Take Photo again
            transition(CaptureState.WAITING_TO_START);
            log.info("Photo accepted, waiting for next capture — photo {}", session.getCurrentIndex() + 1);
        }
    }

    /**
     * RETAKE the current photo — stay at the same index, go back to COUNTDOWN.
     * The previously stored photo at this index will be overwritten.
     */
    public void retake() {
        log.info("Retaking photo {}", session.getCurrentIndex() + 1);
        transition(CaptureState.WAITING_TO_START);
    }

    /** Get the current capture state */
    public CaptureState getState() {
        return session.getState();
    }

    /** Get which photo number we're on (0-based) */
    public int getCurrentIndex() {
        return session.getCurrentIndex();
    }

    /** Get the photo at the current index (for preview display) */
    public BufferedImage getCurrentPhoto() {
        return session.getCurrentPhoto();
    }

    /** Get the underlying session data */
    public CaptureSession getSession() {
        return session;
    }

    /** Internal state transition — updates session and notifies callback */
    private void transition(CaptureState newState) {
        CaptureState old = session.getState();
        session.setState(newState);
        log.debug("State: {} → {}", old, newState);
        if (onStateChange != null) {
            onStateChange.accept(newState);
        }
    }
}
