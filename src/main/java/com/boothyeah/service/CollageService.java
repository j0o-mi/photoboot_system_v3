package com.boothyeah.service;

import com.boothyeah.AppConfig;
import com.boothyeah.model.CaptureSession;
import com.boothyeah.model.PhotoTemplate;
import com.boothyeah.model.SlotRegion;
import com.boothyeah.util.QuadWarpRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Creates the final photo strip collage by compositing captured photos
 * into the template's slot regions, then overlaying the template frame.
 */
public class CollageService {
    private static final Logger log = LoggerFactory.getLogger(CollageService.class);
    private static final DateTimeFormatter FILE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public File createCollage(CaptureSession session) {
        return createCollage(session, (FilterService.FilterType) null);
    }

    /**
     * Creates and saves the final collage file to disk.
     * CALL THIS ONLY WHEN SAVING FINAL OUTPUT ON APPLY!
     */
    public File createCollage(CaptureSession session, FilterService.FilterType filter) {
        BufferedImage canvas = renderCollageToMemory(session, filter);
        if (canvas == null) return null;

        try {
            String filename = "ThePhotoboothCo_" + LocalDateTime.now().format(FILE_FORMAT) + ".png";
            File outputFile = new File(AppConfig.OUTPUT_DIR, filename);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            ImageIO.write(canvas, "PNG", outputFile);
            log.info("Collage successfully saved to disk: {}", outputFile.getAbsolutePath());
            return outputFile;
        } catch (IOException e) {
            log.error("Failed to write composite canvas to disk", e);
            return null;
        }
    }

    /**
     * Renders the collage strictly IN-MEMORY without creating files on disk.
     * Perfect for live UI previews!
     */
    public BufferedImage renderCollageToMemory(CaptureSession session, FilterService.FilterType filter) {
        if (session == null || session.getTemplate() == null || session.getPhotos() == null) {
            log.error("Invalid capture session or missing template/photos.");
            return null;
        }

        PhotoTemplate template = session.getTemplate();
        BufferedImage[] photos = session.getPhotos();
        List<SlotRegion> slots = template.getSlots();

        if (slots == null || slots.isEmpty()) {
            log.error("Template contains no slot regions defined.");
            return null;
        }

        // 1. Apply filter ONLY to captured photos
        BufferedImage[] filteredPhotos = new BufferedImage[photos.length];
        FilterService filterService = (filter != null && filter != FilterService.FilterType.NONE) ? new FilterService() : null;

        for (int i = 0; i < photos.length; i++) {
            if (photos[i] != null) {
                filteredPhotos[i] = (filterService != null) ? filterService.applyFilter(photos[i], filter) : photos[i];
            }
        }

        // 2. Initialize canvas
        int w = template.getWidth();
        int h = template.getHeight();
        BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = canvas.createGraphics();

        configureGraphicsQuality(g2d);

        // 3. Render filtered photos into slots
        for (int i = 0; i < Math.min(filteredPhotos.length, slots.size()); i++) {
            if (filteredPhotos[i] != null) {
                SlotRegion slot = slots.get(i);
                if (slot.hasCustomCorners()) {
                    g2d.dispose();
                    QuadWarpRenderer.drawWarpedPhoto(canvas, filteredPhotos[i], slot);
                    g2d = canvas.createGraphics();
                    configureGraphicsQuality(g2d);
                } else {
                    drawCenterCrop(g2d, filteredPhotos[i], slot);
                }
            }
        }

        // 4. Draw transparent overlay frame ON TOP (unfiltered)
        BufferedImage overlay = template.getTransparentImage();
        if (overlay != null) {
            g2d.drawImage(overlay, 0, 0, null);
        }

        g2d.dispose();
        return canvas;
    }

    private void drawCenterCrop(Graphics2D g2d, BufferedImage photo, SlotRegion slot) {
        double slotRatio = (double) slot.getWidth() / slot.getHeight();
        double photoRatio = (double) photo.getWidth() / photo.getHeight();

        int srcX, srcY, srcW, srcH;

        if (photoRatio > slotRatio) {
            srcH = photo.getHeight();
            srcW = (int) (srcH * slotRatio);
            srcX = (photo.getWidth() - srcW) / 2;
            srcY = 0;
        } else {
            srcW = photo.getWidth();
            srcH = (int) (srcW / slotRatio);
            srcX = 0;
            srcY = (photo.getHeight() - srcH) / 2;
        }

        g2d.drawImage(
                photo,
                slot.getX(), slot.getY(), slot.getX() + slot.getWidth(), slot.getY() + slot.getHeight(),
                srcX, srcY, srcX + srcW, srcY + srcH,
                null
        );
    }

    private void configureGraphicsQuality(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}