package com.boothyeah.model;

import java.util.List;

/**
 * Gson-serializable POJO for persisting slot quadrilateral data to a sidecar JSON file.
 * Each template can have a corresponding "<templateId>.layout.json" file in data/templates/.
 */
public class SlotLayout {
    public String templateId;
    public List<SlotEntry> slots;

    public static class SlotEntry {
        public int index;
        public String label;
        public double tlX, tlY;
        public double trX, trY;
        public double brX, brY;
        public double blX, blY;
    }
}
