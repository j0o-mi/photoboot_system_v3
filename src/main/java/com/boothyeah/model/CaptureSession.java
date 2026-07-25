package com.boothyeah.model;

import java.awt.image.BufferedImage;

/**
 * Holds all data for a single photobooth session:
 * the selected template, captured photos, and current capture state.
 *
 * The photos array has a fixed size matching the template's slot count (typically 3).
 * currentIndex tracks which photo slot we're currently capturing for.
 */
public class CaptureSession {
    private PhotoTemplate template;
    private BufferedImage[] photos;
    private int currentIndex;
    private CaptureState state;

    public CaptureSession(PhotoTemplate template) {
        this.template = template;
        this.photos = new BufferedImage[template.getSlotCount()];
        this.currentIndex = 0;
        this.state = CaptureState.WAITING_TO_START;
    }

    /** Store a captured photo at the current index */
    public void storePhoto(BufferedImage photo) {
        if (currentIndex >= 0 && currentIndex < photos.length) {
            photos[currentIndex] = photo;
        }
    }

    /** Get the photo at the current index (for preview) */
    public BufferedImage getCurrentPhoto() {
        return (currentIndex >= 0 && currentIndex < photos.length) ? photos[currentIndex] : null;
    }

    /** Accept current photo and advance to next slot */
    public void acceptCurrent() {
        currentIndex++;
    }

    /** Check if all photos have been captured and accepted */
    public boolean isComplete() {
        return currentIndex >= photos.length;
    }

    /** Get total number of photos needed */
    public int getTotalPhotos() {
        return photos.length;
    }

    // --- Getters & Setters ---
    public PhotoTemplate getTemplate() { return template; }
    public BufferedImage[] getPhotos() { return photos; }
    public int getCurrentIndex() { return currentIndex; }
    public void setCurrentIndex(int idx) { this.currentIndex = idx; }
    public CaptureState getState() { return state; }
    public void setState(CaptureState state) { this.state = state; }
}
