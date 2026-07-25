package com.boothyeah.model;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Represents a photo strip template loaded from a PNG file.
 * Contains the template image, its transparent-cutout version (for compositing),
 * and the detected slot regions where photos will be placed.
 */
public class PhotoTemplate {
    /** Unique identifier (filename without extension) */
    private String id;
    /** Display name shown in template selection */
    private String name;
    /** Absolute path to the template PNG file */
    private String imagePath;
    /** Template image width in pixels */
    private int width;
    /** Template image height in pixels */
    private int height;
    /** Detected slot regions (white cutout areas) */
    private List<SlotRegion> slots;

    // --- Transient runtime fields (not persisted) ---
    /** Original template image as loaded from disk */
    private transient BufferedImage originalImage;
    /** Template with white cutout areas made transparent (for overlay compositing) */
    private transient BufferedImage transparentImage;

    public PhotoTemplate() {}

    public PhotoTemplate(String id, String name, String imagePath) {
        this.id = id;
        this.name = name;
        this.imagePath = imagePath;
    }

    // --- Getters & Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public List<SlotRegion> getSlots() { return slots; }
    public void setSlots(List<SlotRegion> slots) { this.slots = slots; }
    public BufferedImage getOriginalImage() { return originalImage; }
    public void setOriginalImage(BufferedImage img) { this.originalImage = img; }
    public BufferedImage getTransparentImage() { return transparentImage; }
    public void setTransparentImage(BufferedImage img) { this.transparentImage = img; }

    /** Returns the number of photo slots in this template */
    public int getSlotCount() {
        return slots != null ? slots.size() : 0;
    }
}
