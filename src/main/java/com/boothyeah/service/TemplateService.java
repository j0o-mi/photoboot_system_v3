package com.boothyeah.service;

import com.boothyeah.AppConfig;
import com.boothyeah.model.PhotoTemplate;
import com.boothyeah.model.SlotLayout;
import com.boothyeah.model.SlotRegion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages photo strip templates: loading from data/templates/, auto-detecting
 * cutout slots (transparent or white regions), adding/removing templates.
 *
 * Slot detection algorithm:
 * 1. Build a binary mask of "cutout" pixels (fully transparent OR white)
 * 2. Count cutout pixels per row to find horizontal bands
 * 3. Group contiguous cutout-heavy rows into vertical ranges
 * 4. For each range, find the horizontal bounds
 * 5. Filter out regions smaller than 50x50px
 *
 * If a sidecar <templateId>.layout.json exists, its quad corners override
 * auto-detection. This allows admins to manually position slots via the editor.
 */
public class TemplateService {
    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);
    private static final int WHITE_THRESHOLD = 240;
    private static final int ALPHA_THRESHOLD = 30;
    private static final int MIN_SLOT_SIZE = 50;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<PhotoTemplate> templates = new ArrayList<>();

    /** Load all templates from the templates directory */
    public void loadAll() {
        templates.clear();
        File dir = new File(AppConfig.TEMPLATES_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("Templates directory not found: {}", dir.getAbsolutePath());
            return;
        }

        File[] imageFiles = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        });
        if (imageFiles == null || imageFiles.length == 0) {
            log.warn("No template images found in {}", dir.getAbsolutePath());
            return;
        }

        for (File file : imageFiles) {
            try {
                PhotoTemplate t = loadTemplate(file);
                if (t != null && t.getSlotCount() > 0) {
                    templates.add(t);
                    log.info("Loaded template '{}' with {} slots: {}", t.getName(), t.getSlotCount(), t.getSlots());
                } else {
                    log.warn("Skipped template '{}' — no slots detected", file.getName());
                }
            } catch (Exception e) {
                log.error("Failed to load template: {}", file.getName(), e);
            }
        }
        log.info("Template loading complete: {} templates loaded", templates.size());
    }

    /** Load and analyze a single template file, applying sidecar layout if present */
    private PhotoTemplate loadTemplate(File file) throws IOException {
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            log.error("ImageIO.read returned null for: {}", file.getAbsolutePath());
            return null;
        }

        if (img.getType() != BufferedImage.TYPE_INT_ARGB && img.getType() != BufferedImage.TYPE_4BYTE_ABGR) {
            BufferedImage converted = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            converted.createGraphics().drawImage(img, 0, 0, null);
            img = converted;
        }

        String filename = file.getName();
        String id = filename.substring(0, filename.lastIndexOf('.'));
        String name = id.replace('_', ' ');

        PhotoTemplate t = new PhotoTemplate(id, name, file.getAbsolutePath());
        t.setWidth(img.getWidth());
        t.setHeight(img.getHeight());
        t.setOriginalImage(img);

        // Auto-detect slots first (used as fallback or initial editor positions)
        List<SlotRegion> slots = detectSlots(img);
        t.setSlots(slots);

        // Override with sidecar layout if it exists
        loadSidecarIfExists(t);

        t.setTransparentImage(createTransparentOverlay(img));
        return t;
    }

    /**
     * Step 1 of the add-template flow: copy the file to data/templates/,
     * load the image and auto-detect initial slot rectangles.
     * Does NOT add to the live templates list yet.
     * The caller should open the slot editor, then call finalizeTemplate().
     */
    public PhotoTemplate copyAndPreload(File sourceFile) throws IOException {
        File dest = new File(AppConfig.TEMPLATES_DIR, sourceFile.getName());
        Files.copy(sourceFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        PhotoTemplate t = loadTemplate(dest);
        if (t == null) {
            dest.delete();
        }
        return t;
    }

    /**
     * Step 2 of the add-template flow: persist the sidecar JSON and add
     * the template to the live list. Call after the slot editor confirms.
     */
    public void finalizeTemplate(PhotoTemplate t) throws IOException {
        saveSidecar(t);
        templates.add(t);
        log.info("Finalized template '{}' with {} slots", t.getName(), t.getSlotCount());
    }

    /** Persist the slot layout as a sidecar JSON file beside the template PNG. */
    public void saveSidecar(PhotoTemplate t) throws IOException {
        SlotLayout layout = new SlotLayout();
        layout.templateId = t.getId();
        layout.slots = new ArrayList<>();

        List<SlotRegion> slots = t.getSlots();
        for (int i = 0; i < slots.size(); i++) {
            SlotRegion s = slots.get(i);
            SlotLayout.SlotEntry e = new SlotLayout.SlotEntry();
            e.index = i;
            e.label = "Photo " + (i + 1);
            e.tlX = s.getTlX(); e.tlY = s.getTlY();
            e.trX = s.getTrX(); e.trY = s.getTrY();
            e.brX = s.getBrX(); e.brY = s.getBrY();
            e.blX = s.getBlX(); e.blY = s.getBlY();
            layout.slots.add(e);
        }

        File sidecar = new File(AppConfig.TEMPLATES_DIR, t.getId() + AppConfig.LAYOUT_SUFFIX);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(sidecar), StandardCharsets.UTF_8)) {
            gson.toJson(layout, w);
        }
        log.info("Saved slot layout: {}", sidecar.getAbsolutePath());
    }

    /** Load sidecar JSON if it exists and apply corners to the template's slots. */
    private void loadSidecarIfExists(PhotoTemplate t) {
        File sidecar = new File(AppConfig.TEMPLATES_DIR, t.getId() + AppConfig.LAYOUT_SUFFIX);
        if (!sidecar.exists()) return;

        try (Reader r = new InputStreamReader(new FileInputStream(sidecar), StandardCharsets.UTF_8)) {
            SlotLayout layout = gson.fromJson(r, SlotLayout.class);
            if (layout == null || layout.slots == null || layout.slots.isEmpty()) return;

            List<SlotRegion> newSlots = new ArrayList<>();
            for (SlotLayout.SlotEntry e : layout.slots) {
                SlotRegion sr = new SlotRegion();
                sr.setTlX(e.tlX); sr.setTlY(e.tlY);
                sr.setTrX(e.trX); sr.setTrY(e.trY);
                sr.setBrX(e.brX); sr.setBrY(e.brY);
                sr.setBlX(e.blX); sr.setBlY(e.blY);
                // Derive bounding rect from corners for backward compatibility
                int minX = (int) Math.min(e.tlX, e.blX);
                int minY = (int) Math.min(e.tlY, e.trY);
                int maxX = (int) Math.max(e.trX, e.brX);
                int maxY = (int) Math.max(e.blY, e.brY);
                sr.setX(minX); sr.setY(minY);
                sr.setWidth(maxX - minX); sr.setHeight(maxY - minY);
                newSlots.add(sr);
            }
            t.setSlots(newSlots);
            log.info("Loaded sidecar layout for '{}': {} slots", t.getId(), newSlots.size());
        } catch (Exception ex) {
            log.error("Failed to load sidecar for template {}", t.getId(), ex);
        }
    }

    /** Remove a template by ID (deletes the PNG and its sidecar from disk) */
    public boolean removeTemplate(String templateId) {
        PhotoTemplate toRemove = templates.stream()
                .filter(t -> t.getId().equals(templateId))
                .findFirst().orElse(null);
        if (toRemove != null) {
            new File(toRemove.getImagePath()).delete();
            new File(AppConfig.TEMPLATES_DIR, templateId + AppConfig.LAYOUT_SUFFIX).delete();
            templates.remove(toRemove);
            log.info("Removed template: {}", templateId);
            return true;
        }
        return false;
    }

    /** Get all loaded templates */
    public List<PhotoTemplate> getTemplates() {
        return templates;
    }

    // ==================== SLOT DETECTION ====================

    private List<SlotRegion> detectSlots(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        boolean[][] cutoutMask = new boolean[h][w];
        int[] rowCutoutCount = new int[h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                boolean isTransparent = a < ALPHA_THRESHOLD;
                boolean isWhite = (r > WHITE_THRESHOLD && g > WHITE_THRESHOLD && b > WHITE_THRESHOLD && a > 200);

                if (isTransparent || isWhite) {
                    cutoutMask[y][x] = true;
                    rowCutoutCount[y]++;
                }
            }
        }

        int widthThreshold = (int)(w * 0.4);
        List<int[]> rowRanges = new ArrayList<>();
        int rangeStart = -1;

        for (int y = 0; y < h; y++) {
            if (rowCutoutCount[y] > widthThreshold) {
                if (rangeStart == -1) rangeStart = y;
            } else {
                if (rangeStart != -1) {
                    if (y - rangeStart > MIN_SLOT_SIZE) rowRanges.add(new int[]{rangeStart, y - 1});
                    rangeStart = -1;
                }
            }
        }
        if (rangeStart != -1 && h - rangeStart > MIN_SLOT_SIZE) {
            rowRanges.add(new int[]{rangeStart, h - 1});
        }

        List<SlotRegion> slots = new ArrayList<>();
        for (int[] range : rowRanges) {
            int minX = w, maxX = 0;
            for (int y = range[0]; y <= range[1]; y++) {
                for (int x = 0; x < w; x++) { if (cutoutMask[y][x]) { minX = Math.min(minX, x); break; } }
                for (int x = w-1; x >= 0; x--) { if (cutoutMask[y][x]) { maxX = Math.max(maxX, x); break; } }
            }
            int slotW = maxX - minX + 1;
            int slotH = range[1] - range[0] + 1;
            if (slotW > MIN_SLOT_SIZE && slotH > MIN_SLOT_SIZE) {
                slots.add(new SlotRegion(minX, range[0], slotW, slotH));
            }
        }
        return slots;
    }

    private BufferedImage createTransparentOverlay(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage transparent = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = original.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                boolean isTransparent = a < ALPHA_THRESHOLD;
                boolean isWhite = (r > WHITE_THRESHOLD && g > WHITE_THRESHOLD && b > WHITE_THRESHOLD && a > 200);

                transparent.setRGB(x, y, (isTransparent || isWhite) ? 0x00000000 : argb);
            }
        }
        return transparent;
    }
}
