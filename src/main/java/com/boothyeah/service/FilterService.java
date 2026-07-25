package com.boothyeah.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Service for applying various image filters to BufferedImage.
 * Filters are applied to the final collage or individual photos.
 *
 * Supported filters:
 * - NONE: Original image
 * - MONOCHROME: Black and white (grayscale)
 * - SEPIA: Warm vintage tone
 * - COOL: Cool blue tone
 * - BRIGHT: Increased brightness
 * - CONTRAST: Enhanced contrast
 * - WARM: Warm orange tone
 */
public class FilterService {
    private static final Logger log = LoggerFactory.getLogger(FilterService.class);

    public enum FilterType {
        NONE("Original"),
        MONOCHROME("Monochrome"),
        SEPIA("Sepia"),
        COOL("Cool"),
        BRIGHT("Bright"),
        CONTRAST("Contrast"),
        WARM("Warm");

        private final String displayName;

        FilterType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Apply a filter to the given BufferedImage.
     *
     * @param image source image
     * @param filter the filter type to apply
     * @return filtered image (new instance)
     */
    public BufferedImage applyFilter(BufferedImage image, FilterType filter) {
        if (image == null) return null;
        if (filter == null || filter == FilterType.NONE) return image;

        BufferedImage result = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        return switch (filter) {
            case NONE -> image;
            case MONOCHROME -> applyMonochrome(image, result);
            case SEPIA -> applySepia(image, result);
            case COOL -> applyCool(image, result);
            case BRIGHT -> applyBright(image, result);
            case CONTRAST -> applyContrast(image, result);
            case WARM -> applyWarm(image, result);
        };
    }

    /**
     * Convert to grayscale (monochrome).
     */
    private BufferedImage applyMonochrome(BufferedImage source, BufferedImage dest) {
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);

                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Luminosity formula for grayscale
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                int result = (a << 24) | (gray << 16) | (gray << 8) | gray;
                dest.setRGB(x, y, result);
            }
        }
        return dest;
    }

    /**
     * Apply sepia tone (warm vintage look).
     */
    private BufferedImage applySepia(BufferedImage source, BufferedImage dest) {
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);

                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                // Sepia color shift
                int sepiaR = Math.min(255, (int) (gray * 1.2));
                int sepiaG = gray;
                int sepiaB = Math.max(0, (int) (gray * 0.8));

                int result = (a << 24) | (sepiaR << 16) | (sepiaG << 8) | sepiaB;
                dest.setRGB(x, y, result);
            }
        }
        return dest;
    }

    /**
     * Apply cool blue tone.
     */
    private BufferedImage applyCool(BufferedImage source, BufferedImage dest) {
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);

                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Reduce red, keep green, enhance blue
                int coolR = (int) (r * 0.7);
                int coolG = g;
                int coolB = Math.min(255, (int) (b * 1.3));

                int result = (a << 24) | (coolR << 16) | (coolG << 8) | coolB;
                dest.setRGB(x, y, result);
            }
        }
        return dest;
    }

    /**
     * Increase brightness.
     */
    private BufferedImage applyBright(BufferedImage source, BufferedImage dest) {
        int brightness = 40;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);

                int a = (rgb >> 24) & 0xFF;
                int r = Math.min(255, ((rgb >> 16) & 0xFF) + brightness);
                int g = Math.min(255, ((rgb >> 8) & 0xFF) + brightness);
                int b = Math.min(255, (rgb & 0xFF) + brightness);

                int result = (a << 24) | (r << 16) | (g << 8) | b;
                dest.setRGB(x, y, result);
            }
        }
        return dest;
    }

    /**
     * Enhance contrast.
     */
    private BufferedImage applyContrast(BufferedImage source, BufferedImage dest) {
        float contrast = 1.5f;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);

                int a = (rgb >> 24) & 0xFF;
                int r = (int) Math.min(255, Math.max(0, ((rgb >> 16) & 0xFF - 128) * contrast + 128));
                int g = (int) Math.min(255, Math.max(0, ((rgb >> 8) & 0xFF - 128) * contrast + 128));
                int b = (int) Math.min(255, Math.max(0, (rgb & 0xFF - 128) * contrast + 128));

                int result = (a << 24) | (r << 16) | (g << 8) | b;
                dest.setRGB(x, y, result);
            }
        }
        return dest;
    }

    /**
     * Apply warm orange tone.
     */
    private BufferedImage applyWarm(BufferedImage source, BufferedImage dest) {
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);

                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Enhance red and green, reduce blue
                int warmR = Math.min(255, (int) (r * 1.2));
                int warmG = Math.min(255, (int) (g * 1.1));
                int warmB = (int) (b * 0.8);

                int result = (a << 24) | (warmR << 16) | (warmG << 8) | warmB;
                dest.setRGB(x, y, result);
            }
        }
        return dest;
    }
}
