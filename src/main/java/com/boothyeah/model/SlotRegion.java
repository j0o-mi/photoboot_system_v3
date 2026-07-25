package com.boothyeah.model;

/**
 * Represents a slot within a template where a photo will be placed.
 * Supports both simple rectangles (auto-detected) and custom quadrilaterals
 * (admin-defined via the slot editor). Coordinates are in template pixel space.
 *
 * When hasCustomCorners() returns true, the 8 corner fields define the quad
 * and CollageService uses QuadWarpRenderer. Otherwise the rect fields are used.
 */
public class SlotRegion {
    // --- Rectangle fields (auto-detected, also used as fallback) ---
    private int x;
    private int y;
    private int width;
    private int height;

    // --- Quadrilateral corner fields (admin-defined, doubles for sub-pixel precision) ---
    private double tlX, tlY; // top-left
    private double trX, trY; // top-right
    private double brX, brY; // bottom-right
    private double blX, blY; // bottom-left

    public SlotRegion() {}

    public SlotRegion(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Returns true if this slot has custom quadrilateral corners defined.
     * A valid quad must have TR.x > TL.x (positive width in at least one direction).
     */
    public boolean hasCustomCorners() {
        return trX > tlX || brX > blX;
    }

    /**
     * Initialize the 4 corner fields from the bounding rectangle.
     * Call this before opening the slot editor for auto-detected slots.
     */
    public void initCornersFromRect() {
        tlX = x;
        tlY = y;
        trX = x + width;
        trY = y;
        brX = x + width;
        brY = y + height;
        blX = x;
        blY = y + height;
    }

    // --- Rectangle Getters & Setters ---
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    // --- Corner Getters & Setters ---
    public double getTlX() { return tlX; }
    public void setTlX(double tlX) { this.tlX = tlX; }
    public double getTlY() { return tlY; }
    public void setTlY(double tlY) { this.tlY = tlY; }

    public double getTrX() { return trX; }
    public void setTrX(double trX) { this.trX = trX; }
    public double getTrY() { return trY; }
    public void setTrY(double trY) { this.trY = trY; }

    public double getBrX() { return brX; }
    public void setBrX(double brX) { this.brX = brX; }
    public double getBrY() { return brY; }
    public void setBrY(double brY) { this.brY = brY; }

    public double getBlX() { return blX; }
    public void setBlX(double blX) { this.blX = blX; }
    public double getBlY() { return blY; }
    public void setBlY(double blY) { this.blY = blY; }

    @Override
    public String toString() {
        if (hasCustomCorners()) {
            return String.format("SlotRegion[TL=(%.0f,%.0f) TR=(%.0f,%.0f) BR=(%.0f,%.0f) BL=(%.0f,%.0f)]",
                    tlX, tlY, trX, trY, brX, brY, blX, blY);
        }
        return String.format("SlotRegion[x=%d, y=%d, w=%d, h=%d]", x, y, width, height);
    }
}
