package com.boothyeah.controller;

import com.boothyeah.App;
import com.boothyeah.AppConfig;
import com.boothyeah.model.AppSettings;
import com.boothyeah.service.*;
import com.boothyeah.util.ImageUtils;
import com.boothyeah.util.ThreadPool;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the Result screen.
 *
 * Shows the collage preview FIRST (user sees their photos immediately).
 * Upload to Google Drive + QR generation happens ON-DEMAND when user
 * presses the "Upload & Get QR Code" button.
 *
 * Auto-resets to Idle after configured timeout.
 */
public class ResultController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ResultController.class);

    @FXML private ImageView collageView;
    @FXML private ImageView qrView;
    @FXML private VBox qrBox;
    @FXML private Label linkLabel;
    @FXML private Button printBtn;
    @FXML private Button uploadBtn;
    @FXML private VBox uploadStatusBox;
    @FXML private ProgressIndicator uploadProgress;
    @FXML private Label uploadStatusLabel;

    private Timeline autoResetTimer;
    private File collageFile;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        NavigationService nav = App.getService(NavigationService.class);
        AppSettings settings = App.getService(SettingsService.class).getSettings();

        // Load collage from context
        collageFile = nav.getContext("collageFile", File.class);

        // Display collage preview immediately
        if (collageFile != null && collageFile.exists()) {
            try {
                BufferedImage collage = ImageIO.read(collageFile);
                collageView.setImage(ImageUtils.toFXImage(collage));
            } catch (Exception e) {
                log.error("Failed to load collage for display", e);
            }
        }

        // Show/hide buttons based on settings
        printBtn.setVisible(settings.isPrintEnabled());
        printBtn.setManaged(settings.isPrintEnabled());

        // Hide upload button if Drive is not configured
        boolean driveReady = settings.isGoogleDriveEnabled()
                && settings.getGoogleDriveFolderId() != null
                && !settings.getGoogleDriveFolderId().isEmpty();
        uploadBtn.setVisible(driveReady);
        uploadBtn.setManaged(driveReady);

        // Auto-reset timer
        int resetSeconds = settings.getAutoResetSeconds();
        if (resetSeconds > 0) {
            autoResetTimer = new Timeline(new KeyFrame(
                    Duration.seconds(resetSeconds),
                    e -> onDone()
            ));
            autoResetTimer.play();
        }
    }

    // ==================== UPLOAD & QR (ON-DEMAND) ====================

    @FXML
    private void onUpload() {
        // Show upload progress
        uploadBtn.setVisible(false);
        uploadBtn.setManaged(false);
        uploadStatusBox.setVisible(true);
        uploadStatusBox.setManaged(true);
        uploadProgress.setProgress(-1);
        uploadStatusLabel.setText("Uploading to Google Drive...");

        // Reset auto-reset timer (user is interacting)
        if (autoResetTimer != null) autoResetTimer.stop();

        AppSettings settings = App.getService(SettingsService.class).getSettings();

        CompletableFuture
                // Step 1: Upload to Google Drive
                .supplyAsync(() -> {
                    GoogleDriveService driveService = App.getService(GoogleDriveService.class);
                    return driveService.uploadImage(collageFile, settings.getGoogleDriveFolderId());
                }, ThreadPool.getExecutor())

                // Step 2: Generate QR code
                .thenApplyAsync(driveLink -> {
                    if (driveLink != null) {
                        Platform.runLater(() -> uploadStatusLabel.setText("Generating QR code..."));
                        QRCodeService qrService = App.getService(QRCodeService.class);
                        BufferedImage qrImage = qrService.generateQR(driveLink, AppConfig.DEFAULT_QR_SIZE);
                        return new Object[]{driveLink, qrImage};
                    }
                    return new Object[]{null, null};
                }, ThreadPool.getExecutor())

                // Step 3: Show QR code
                .thenAccept(result -> {
                    String driveLink = (String) result[0];
                    BufferedImage qrImage = (BufferedImage) result[1];

                    Platform.runLater(() -> {
                        uploadStatusBox.setVisible(false);
                        uploadStatusBox.setManaged(false);

                        if (driveLink != null && qrImage != null) {
                            // Show QR code
                            qrView.setImage(ImageUtils.toFXImage(qrImage));
                            linkLabel.setText(driveLink);
                            qrBox.setVisible(true);
                            qrBox.setManaged(true);
                            log.info("Upload complete, QR code displayed. Link: {}", driveLink);
                        } else {
                            // Upload failed — show error in upload area
                            uploadStatusBox.setVisible(true);
                            uploadStatusBox.setManaged(true);
                            uploadProgress.setVisible(false);
                            GoogleDriveService driveService = App.getService(GoogleDriveService.class);
                            String error = driveService.getLastError();
                            uploadStatusLabel.setText("⚠ Upload failed: " + (error != null ? error : "Unknown error"));
                            uploadStatusLabel.setStyle("-fx-text-fill: #ff4444;");
                        }

                        // Restart auto-reset timer
                        resetAutoTimer();
                    });
                })

                .exceptionally(ex -> {
                    log.error("Upload pipeline failed", ex);
                    Platform.runLater(() -> {
                        uploadStatusBox.setVisible(true);
                        uploadStatusBox.setManaged(true);
                        uploadProgress.setVisible(false);
                        uploadStatusLabel.setText("⚠ Upload error: " + ex.getMessage());
                        uploadStatusLabel.setStyle("-fx-text-fill: #ff4444;");
                    });
                    return null;
                });
    }

    // ==================== PRINT ====================

    @FXML
    private void onPrint() {
        if (collageFile == null || !collageFile.exists()) {
            log.warn("No collage file to print");
            return;
        }

        try {
            BufferedImage image = ImageIO.read(collageFile);
            PhotoPrintService printService = App.getService(PhotoPrintService.class);
            String printerName = App.getService(SettingsService.class).getSettings().getPrinterName();
            boolean success = printService.printImage(image, printerName);

            if (success) {
                printBtn.setText("✓ Sent to Printer");
                printBtn.setDisable(true);
            } else {
                printBtn.setText("⚠ Print Failed");
            }
        } catch (Exception e) {
            log.error("Print error", e);
            printBtn.setText("⚠ Print Error");
        }
    }

    // ==================== DONE ====================

    @FXML
    private void onDone() {
        if (autoResetTimer != null) autoResetTimer.stop();

        NavigationService nav = App.getService(NavigationService.class);
        nav.clearAllContext();
        nav.navigateTo(AppConfig.FXML_IDLE);
    }

    private void resetAutoTimer() {
        int resetSeconds = App.getService(SettingsService.class).getSettings().getAutoResetSeconds();
        if (resetSeconds > 0) {
            if (autoResetTimer != null) autoResetTimer.stop();
            autoResetTimer = new Timeline(new KeyFrame(Duration.seconds(resetSeconds), e -> onDone()));
            autoResetTimer.play();
        }
    }
}
