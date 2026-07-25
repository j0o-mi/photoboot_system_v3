package com.boothyeah.model;

/**
 * Application settings persisted to data/settings.json.
 * Managed through the Admin Dashboard.
 * All fields have sensible defaults for first-run experience.
 */
public class AppSettings {
    /** PIN required to access admin dashboard */
    private String adminPin = "1234";

    /** Countdown duration in seconds before each photo capture */
    private int countdownSeconds = 3;

    /** Camera capture resolution */
    private int cameraWidth = 1280;
    private int cameraHeight = 720;

    /** Camera device index (0 = default, 1 = second camera, etc.) */
    private int cameraIndex = 0;

    /** Google Drive target folder ID */
    private String googleDriveFolderId = "";

    /** Whether to upload photos to Google Drive */
    private boolean googleDriveEnabled = false;

    /** Path to the service account credentials JSON (relative to app root) */
    private String serviceAccountPath = "data/credentials/service-account.json";

    /** Whether the Print button is available to users */
    private boolean printEnabled = true;

    /** Name of the selected printer (empty = use system default) */
    private String printerName = "";

    /** Seconds of inactivity on Result screen before auto-resetting to Idle */
    private int autoResetSeconds = 30;

    // --- Getters & Setters ---
    public String getAdminPin() { return adminPin; }
    public void setAdminPin(String adminPin) { this.adminPin = adminPin; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public void setCountdownSeconds(int s) { this.countdownSeconds = s; }
    public int getCameraWidth() { return cameraWidth; }
    public void setCameraWidth(int w) { this.cameraWidth = w; }
    public int getCameraHeight() { return cameraHeight; }
    public void setCameraHeight(int h) { this.cameraHeight = h; }
    public String getGoogleDriveFolderId() { return googleDriveFolderId; }
    public void setGoogleDriveFolderId(String id) { this.googleDriveFolderId = id; }
    public boolean isGoogleDriveEnabled() { return googleDriveEnabled; }
    public void setGoogleDriveEnabled(boolean e) { this.googleDriveEnabled = e; }
    public String getServiceAccountPath() { return serviceAccountPath; }
    public void setServiceAccountPath(String p) { this.serviceAccountPath = p; }
    public boolean isPrintEnabled() { return printEnabled; }
    public void setPrintEnabled(boolean e) { this.printEnabled = e; }
    public String getPrinterName() { return printerName; }
    public void setPrinterName(String n) { this.printerName = n; }
    public int getAutoResetSeconds() { return autoResetSeconds; }
    public void setAutoResetSeconds(int s) { this.autoResetSeconds = s; }
    public int getCameraIndex() { return cameraIndex; }
    public void setCameraIndex(int i) { this.cameraIndex = i; }
}
