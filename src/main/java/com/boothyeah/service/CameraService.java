package com.boothyeah.service;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-platform camera service using JavaCV (OpenCV wrapper).
 *
 * Native backends per OS:
 * - macOS:   AVFoundation → proper camera permission dialog
 * - Windows: DirectShow   → works out of the box
 * - Linux:   V4L2
 *
 * Threading model:
 * - A background daemon thread continuously grabs frames at ~30fps
 * - Latest frame is stored in a volatile field (thread-safe reference swap)
 * - FX AnimationTimer reads the latest frame for live preview
 * - captureFrame() returns a deep copy safe for permanent storage
 */
public class CameraService {
    private static final Logger log = LoggerFactory.getLogger(CameraService.class);
    private static final int CAMERA_SCAN_LIMIT = 20;

    private OpenCVFrameGrabber grabber;
    private volatile BufferedImage latestFrame;
    private Thread captureThread;
    private volatile boolean running = false;
    private String lastError = "";
    private int cameraIndex = 0;

    /**
     * Open the default webcam at the requested resolution.
     * Starts a background thread that continuously grabs frames.
     */
    public void start(int requestedWidth, int requestedHeight) {
        start(0, requestedWidth, requestedHeight);
    }

    /**
     * Open any OS video-capture device at the given index and resolution.
     * This includes built-in/external webcams, UVC-compatible DSLRs, and HDMI
     * capture cards. A DSLR in file-transfer-only mode must be switched to its
     * webcam/UVC mode or connected through a capture card.
     * @param camIndex 0 = default, 1 = second camera, etc.
     */
    public void start(int camIndex, int requestedWidth, int requestedHeight) {
        if (running) return;

        try {
            this.cameraIndex = camIndex;
            grabber = new OpenCVFrameGrabber(camIndex);
            grabber.setImageWidth(requestedWidth);
            grabber.setImageHeight(requestedHeight);

            log.info("Opening camera (OpenCV)...");
            grabber.start();

            running = true;
            lastError = "";

            log.info("Camera opened successfully: {}x{} (requested {}x{})",
                    grabber.getImageWidth(), grabber.getImageHeight(),
                    requestedWidth, requestedHeight);

            // Start background frame-grabbing thread
            captureThread = new Thread(this::captureLoop, "camera-capture");
            captureThread.setDaemon(true);
            captureThread.start();

        } catch (FrameGrabber.Exception e) {
            lastError = buildErrorMessage(e);
            log.error("Failed to start camera: {}", lastError);
            running = false;
        }
    }

    /**
     * Background loop: continuously grabs frames and stores the latest one.
     * Each frame is deep-copied so the volatile reference is always safe to read
     * from any thread without synchronization.
     */
    private void captureLoop() {
        // Each thread has its own converter (converter reuses internal buffers)
        Java2DFrameConverter converter = new Java2DFrameConverter();

        while (running) {
            try {
                Frame frame = grabber.grab();
                if (frame != null && frame.image != null) {
                    // converter.convert() may reuse an internal buffer, so deep-copy
                    BufferedImage raw = converter.convert(frame);
                    if (raw != null) {
                        int w = raw.getWidth(), h = raw.getHeight();
                        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
                        Graphics g = copy.getGraphics();
                        g.drawImage(raw, 0, 0, null);
                        g.dispose();
                        latestFrame = copy; // volatile write → atomic reference swap
                    }
                }
                Thread.sleep(33); // ~30fps — sufficient for live preview
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running) {
                    log.warn("Frame grab error: {}", e.getMessage());
                }
            }
        }
        log.info("Camera capture loop stopped");
    }

    /**
     * Get the latest camera frame for live preview display.
     * Safe to call from any thread. May return null if camera isn't ready yet.
     *
     * @return latest frame (independent copy), or null
     */
    public BufferedImage getLatestFrame() {
        return latestFrame;
    }

    /**
     * Capture a single frame intended for permanent storage (photo session).
     * Returns a deep copy that won't be overwritten by the continuous capture loop.
     *
     * @return deep copy of the current frame, or null if camera unavailable
     */
    public BufferedImage captureFrame() {
        BufferedImage frame = latestFrame;
        if (frame == null) return null;

        // Deep copy for permanent storage
        BufferedImage copy = new BufferedImage(
                frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics g = copy.getGraphics();
        g.drawImage(frame, 0, 0, null);
        g.dispose();
        return copy;
    }

    /**
     * Check if camera is running AND has produced at least one frame.
     * Useful for verifying camera actually works after start().
     */
    public boolean isRunning() {
        return running && latestFrame != null;
    }

    /** Close the webcam and release all resources */
    public void stop() {
        running = false;

        if (captureThread != null) {
            captureThread.interrupt();
            try {
                captureThread.join(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
                log.info("Camera stopped and released");
            } catch (Exception e) {
                log.error("Error stopping camera", e);
            }
        }

        latestFrame = null;
    }

    /** Get the camera name for display in admin dashboard */
    public String getCameraName() {
        if (grabber != null) {
            return "Camera " + cameraIndex + " (OpenCV " + grabber.getImageWidth() + "x" + grabber.getImageHeight() + ")";
        }
        return "No camera";
    }

    /** Get the last error message (for UI display) */
    public String getLastError() {
        return lastError;
    }

    /**
     * Enumerate available camera devices with real device names.
     * Uses OS-native commands to get device names, then verifies with OpenCV.
     *
     * @return list of camera entries as "Camera N — DeviceName (WxH)" strings
     */
    public static List<String> listAvailableCameras() {
        // 1. Get device names from OS
        List<String> deviceNames = getOSDeviceNames();

        // 2. Probe each index with OpenCV to confirm availability
        List<String> cameras = new ArrayList<>();
        // External cameras are often assigned higher indexes than built-in ones.
        int maxCheck = Math.max(deviceNames.size(), CAMERA_SCAN_LIMIT);

        for (int i = 0; i < maxCheck; i++) {
            try {
                OpenCVFrameGrabber test = new OpenCVFrameGrabber(i);
                test.start();
                int w = test.getImageWidth();
                int h = test.getImageHeight();
                test.stop();
                test.release();

                String deviceName = (i < deviceNames.size()) ? deviceNames.get(i) : "Unknown";
                cameras.add("Camera " + i + " — " + deviceName + " (" + w + "x" + h + ")");
            } catch (Exception e) {
                // Camera at this index not available, skip
            }
        }

        if (cameras.isEmpty()) {
            cameras.add("No camera detected");
        }
        return cameras;
    }

    /**
     * Get camera device names from the OS.
     * macOS: uses system_profiler SPCameraDataType
     * Windows: uses wmic or PowerShell
     */
    private static List<String> getOSDeviceNames() {
        List<String> names = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();

        try {
            ProcessBuilder pb;
            if (os.contains("mac")) {
                pb = new ProcessBuilder("system_profiler", "SPCameraDataType");
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("powershell", "-Command",
                        "Get-CimInstance Win32_PnPEntity | Where-Object { $_.PNPClass -eq 'Camera' -or $_.PNPClass -eq 'Image' } | Select-Object -ExpandProperty Name");
            } else {
                // Linux: try v4l2
                pb = new ProcessBuilder("bash", "-c",
                        "for d in /dev/video*; do v4l2-ctl -d $d --info 2>/dev/null | grep 'Card type' | cut -d: -f2; done");
            }

            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());
            proc.waitFor();

            if (os.contains("mac")) {
                // Parse macOS output: lines containing camera names are indented with "Model ID:" above
                // Camera names appear after indentation without a colon, or as value of specific keys
                for (String line : output.split("\n")) {
                    String trimmed = line.trim();
                    // macOS lists cameras with their name as a header line (ends with ':')
                    // followed by properties. The header is the device name.
                    if (trimmed.endsWith(":") && !trimmed.startsWith("Camera")
                            && !trimmed.contains("SPCamera") && !trimmed.isEmpty()
                            && !trimmed.equals("Cameras:")) {
                        names.add(trimmed.substring(0, trimmed.length() - 1).trim());
                    }
                }
            } else {
                // Windows/Linux: each line is a device name
                for (String line : output.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        names.add(trimmed);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not enumerate camera device names: {}", e.getMessage());
        }

        return names;
    }

    /**
     * Build a user-friendly error message with OS-specific troubleshooting tips.
     */
    private String buildErrorMessage(Exception e) {
        String os = System.getProperty("os.name", "").toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("Camera error: ").append(e.getMessage());

        if (os.contains("mac")) {
            sb.append("\n\n🍎 macOS: Grant camera permission:")
              .append("\nSystem Settings → Privacy & Security → Camera")
              .append("\n→ Enable for Terminal / your IDE");
        } else if (os.contains("win")) {
            sb.append("\n\n🪟 Windows: Check:")
              .append("\n• Settings → Privacy → Camera → Allow desktop apps")
              .append("\n• Camera is not used by another app");
        } else {
            sb.append("\n\n🐧 Linux: Check:")
              .append("\n• Camera device /dev/video0 exists")
              .append("\n• User has video group permissions");
        }

        return sb.toString();
    }
}
