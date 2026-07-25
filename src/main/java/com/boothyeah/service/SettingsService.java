package com.boothyeah.service;

import com.boothyeah.AppConfig;
import com.boothyeah.model.AppSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Reads and writes application settings to/from data/settings.json.
 * Creates default settings on first run if the file doesn't exist.
 * Thread-safe: synchronizes on read/write operations.
 */
public class SettingsService {
    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private AppSettings settings;

    public SettingsService() {
        load();
    }

    /** Load settings from disk, or create defaults if file missing */
    public synchronized void load() {
        File file = new File(AppConfig.SETTINGS_FILE);
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                settings = gson.fromJson(reader, AppSettings.class);
                log.info("Settings loaded from {}", file.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to load settings, using defaults", e);
                settings = new AppSettings();
            }
        } else {
            log.info("No settings file found, creating defaults");
            settings = new AppSettings();
            save(); // Persist the defaults
        }
    }

    /** Persist current settings to disk */
    public synchronized void save() {
        File file = new File(AppConfig.SETTINGS_FILE);
        file.getParentFile().mkdirs();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(settings, writer);
            log.info("Settings saved to {}", file.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to save settings", e);
        }
    }

    /** Get current settings (in-memory). Modify the returned object then call save(). */
    public AppSettings getSettings() {
        return settings;
    }
}
