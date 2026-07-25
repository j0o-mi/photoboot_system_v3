package com.boothyeah;

import java.io.File;

/**
 * Centralized application constants and path configuration.
 * All paths are relative to the application root directory.
 */
public final class AppConfig {

    private AppConfig() {}

    // --- Application Info ---
    public static final String APP_NAME = "BoothYeah";
    public static final String APP_VERSION = "1.0.0";

    // --- File Paths (relative to app root) ---
    public static final String DATA_DIR = "data";
    public static final String TEMPLATES_DIR = DATA_DIR + File.separator + "templates";
    public static final String OUTPUT_DIR = DATA_DIR + File.separator + "output";
    public static final String SETTINGS_FILE = DATA_DIR + File.separator + "settings.json";
    public static final String CREDENTIALS_DIR = DATA_DIR + File.separator + "credentials";
    public static final String LAYOUT_SUFFIX = ".layout.json";

    // --- FXML Paths (classpath resources) ---
    public static final String FXML_IDLE = "/com/boothyeah/fxml/idle.fxml";
    public static final String FXML_TEMPLATE = "/com/boothyeah/fxml/template_selection.fxml";
    public static final String FXML_CAPTURE = "/com/boothyeah/fxml/capture.fxml";
    public static final String FXML_PROCESSING = "/com/boothyeah/fxml/processing.fxml";
    public static final String FXML_FILTER = "/com/boothyeah/fxml/filter.fxml";
    public static final String FXML_RESULT = "/com/boothyeah/fxml/result.fxml";
    public static final String FXML_ADMIN = "/com/boothyeah/fxml/admin.fxml";

    // --- CSS ---
    public static final String CSS_PATH = "/com/boothyeah/css/styles.css";

    // --- Defaults ---
    public static final int DEFAULT_COUNTDOWN = 3;
    public static final int DEFAULT_CAMERA_WIDTH = 1280;
    public static final int DEFAULT_CAMERA_HEIGHT = 720;
    public static final int DEFAULT_QR_SIZE = 300;
    public static final int DEFAULT_AUTO_RESET = 30;

    /**
     * Ensure all required data directories exist.
     * Called once at application startup.
     */
    public static void ensureDirectories() {
        new File(TEMPLATES_DIR).mkdirs();
        new File(OUTPUT_DIR).mkdirs();
        new File(CREDENTIALS_DIR).mkdirs();
    }
}
