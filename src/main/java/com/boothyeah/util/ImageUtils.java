package com.boothyeah.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;

/**
 * Utility class for converting between AWT BufferedImage and JavaFX Image.
 * The camera (Sarxos) produces BufferedImage, but JavaFX ImageView/Canvas needs javafx.scene.image.Image.
 */
public final class ImageUtils {

    private ImageUtils() {} // Utility class, no instantiation

    /**
     * Convert AWT BufferedImage to JavaFX WritableImage.
     * Uses SwingFXUtils bridge from javafx-swing module.
     *
     * @param bufferedImage the AWT image from camera or compositing
     * @return JavaFX-compatible image for display in ImageView/Canvas
     */
    public static WritableImage toFXImage(BufferedImage bufferedImage) {
        if (bufferedImage == null) return null;
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }

    /**
     * Convert AWT BufferedImage to JavaFX WritableImage, reusing a buffer for performance.
     * Avoids allocating a new WritableImage on every frame (useful for live camera feed).
     *
     * @param bufferedImage source AWT image
     * @param reusable      existing WritableImage to write into (must match dimensions)
     * @return the WritableImage with updated pixel data
     */
    public static WritableImage toFXImage(BufferedImage bufferedImage, WritableImage reusable) {
        if (bufferedImage == null) return null;
        return SwingFXUtils.toFXImage(bufferedImage, reusable);
    }

    /**
     * Convert JavaFX Image back to AWT BufferedImage.
     * Useful for saving JavaFX-rendered content to disk.
     */
    public static BufferedImage toBufferedImage(Image fxImage) {
        if (fxImage == null) return null;
        return SwingFXUtils.fromFXImage(fxImage, null);
    }
}
