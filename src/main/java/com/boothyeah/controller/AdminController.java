package com.boothyeah.controller;

import com.boothyeah.App;
import com.boothyeah.AppConfig;
import com.boothyeah.model.AppSettings;
import com.boothyeah.model.PhotoTemplate;
import com.boothyeah.model.SlotRegion;
import com.boothyeah.service.*;
import com.boothyeah.ui.SlotEditorStage;
import com.boothyeah.util.ImageUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Admin Dashboard.
 * Manages templates, Google Drive settings, printer settings,
 * general config, and session history.
 */
public class AdminController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    // --- FXML Injected ---
    @FXML private TabPane tabPane;
    @FXML private FlowPane adminTemplateGrid;
    @FXML private TextField driveFolderField;
    @FXML private TextField serviceAccountField;
    @FXML private CheckBox driveEnabledCheck;
    @FXML private Label driveStatusLabel;
    @FXML private CheckBox printEnabledCheck;
    @FXML private ComboBox<String> printerCombo;
    @FXML private Spinner<Integer> countdownSpinner;
    @FXML private Spinner<Integer> autoResetSpinner;
    @FXML private ComboBox<String> cameraCombo;
    @FXML private PasswordField pinField;
    @FXML private FlowPane driveGalleryPane;
    @FXML private Label driveStatusText;
    @FXML private Label outputSizeLabel;
    @FXML private Label saveStatusLabel;

    private SettingsService settingsService;
    private TemplateService templateService;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        settingsService = App.getService(SettingsService.class);
        templateService = App.getService(TemplateService.class);
        AppSettings s = settingsService.getSettings();

        // --- Templates Tab ---
        refreshTemplateGrid();

        // --- Drive Tab ---
        driveFolderField.setText(s.getGoogleDriveFolderId());
        serviceAccountField.setText(s.getServiceAccountPath());
        driveEnabledCheck.setSelected(s.isGoogleDriveEnabled());

        // --- Printer Tab ---
        printEnabledCheck.setSelected(s.isPrintEnabled());
        PhotoPrintService printService = App.getService(PhotoPrintService.class);
        String[] printers = printService.getAvailablePrinters();
        printerCombo.getItems().addAll(printers);
        if (printers.length > 0) {
            if (s.getPrinterName() != null && !s.getPrinterName().isEmpty()) {
                printerCombo.setValue(s.getPrinterName());
            } else {
                printerCombo.setValue(printers[0]);
            }
        }

        // --- Settings Tab ---
        countdownSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, s.getCountdownSeconds()));
        autoResetSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 120, s.getAutoResetSeconds()));

        // Camera selection — show current setting
        cameraCombo.getItems().add("Camera " + s.getCameraIndex() + " (current)");
        cameraCombo.setValue("Camera " + s.getCameraIndex() + " (current)");

        // --- History Tab ---
        refreshHistory();

        // Clear status label when switching tabs
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            saveStatusLabel.setText("");
            if (newTab != null && "History".equals(newTab.getText())) {
                onRefreshDrive();
            }
        });
    }

    // ==================== TEMPLATES ====================

    private void refreshTemplateGrid() {
        adminTemplateGrid.getChildren().clear();
        for (PhotoTemplate t : templateService.getTemplates()) {
            VBox card = new VBox(8);
            card.setAlignment(Pos.CENTER);
            card.getStyleClass().add("template-card");
            card.setPrefWidth(190);

            ImageView preview = new ImageView(ImageUtils.toFXImage(t.getOriginalImage()));
            preview.setFitWidth(150);
            preview.setPreserveRatio(true);

            Label name = new Label(t.getName());
            name.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");

            Label info = new Label(t.getSlotCount() + " slots | " + t.getWidth() + "x" + t.getHeight());
            info.getStyleClass().add("text-muted");
            info.setStyle("-fx-font-size: 11px;");

            Button editBtn = new Button("Edit Layout");
            editBtn.getStyleClass().add("btn-secondary");
            editBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4 12;");
            editBtn.setOnAction(e -> {
                // Ensure corners are initialized before opening editor
                for (SlotRegion slot : t.getSlots()) {
                    if (!slot.hasCustomCorners()) slot.initCornersFromRect();
                }
                SlotEditorStage editor = new SlotEditorStage(t,
                        App.getService(NavigationService.class).getStage());
                editor.showAndWait();
                if (editor.wasSaved()) {
                    t.setSlots(new ArrayList<>(editor.getEditedSlots()));
                    try {
                        templateService.saveSidecar(t);
                        saveStatusLabel.setText("Layout saved: " + t.getName());
                        saveStatusLabel.setStyle("-fx-text-fill: #44ff88;");
                    } catch (IOException ex) {
                        log.error("Failed to save layout", ex);
                        saveStatusLabel.setText("Error saving layout: " + ex.getMessage());
                        saveStatusLabel.setStyle("-fx-text-fill: #ff4444;");
                    }
                    refreshTemplateGrid();
                }
            });

            Button deleteBtn = new Button("Delete");
            deleteBtn.getStyleClass().add("btn-danger");
            deleteBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4 12;");
            deleteBtn.setOnAction(e -> {
                templateService.removeTemplate(t.getId());
                refreshTemplateGrid();
            });

            card.getChildren().addAll(preview, name, info, editBtn, deleteBtn);
            adminTemplateGrid.getChildren().add(card);
        }
    }

    @FXML
    private void onAddTemplate() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Template Image");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        javafx.stage.Stage ownerStage = App.getService(NavigationService.class).getStage();
        File file = fc.showOpenDialog(ownerStage);
        if (file == null) return;

        try {
            // Step 1: copy file and preload image with auto-detected initial slots
            PhotoTemplate template = templateService.copyAndPreload(file);
            if (template == null) {
                saveStatusLabel.setText("Failed to read image.");
                saveStatusLabel.setStyle("-fx-text-fill: #ff4444;");
                return;
            }

            // Initialize corners from auto-detected rects so editor has a starting point
            for (SlotRegion slot : template.getSlots()) {
                if (!slot.hasCustomCorners()) slot.initCornersFromRect();
            }

            // Step 2: open visual slot editor
            SlotEditorStage editor = new SlotEditorStage(template, ownerStage);
            editor.showAndWait();

            if (editor.wasSaved()) {
                // Apply edited slots and persist
                template.setSlots(new ArrayList<>(editor.getEditedSlots()));
                templateService.finalizeTemplate(template);
                refreshTemplateGrid();
                saveStatusLabel.setText("Template added: " + template.getName());
                saveStatusLabel.setStyle("-fx-text-fill: #44ff88;");
            } else {
                // Cancelled — delete the copied PNG
                new File(template.getImagePath()).delete();
                saveStatusLabel.setText("Template add cancelled.");
                saveStatusLabel.setStyle("");
            }
        } catch (Exception e) {
            log.error("Failed to add template", e);
            saveStatusLabel.setText("Error: " + e.getMessage());
            saveStatusLabel.setStyle("-fx-text-fill: #ff4444;");
        }
    }

    // ==================== GOOGLE DRIVE ====================

    @FXML
    private void onBrowseCredentials() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Service Account JSON");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = fc.showOpenDialog(App.getService(NavigationService.class).getStage());
        if (file != null) {
            serviceAccountField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void onTestDrive() {
        String path = serviceAccountField.getText();
        String folderId = driveFolderField.getText();

        if (path.isEmpty() || folderId.isEmpty()) {
            driveStatusLabel.setText("⚠ Fill in both fields first");
            driveStatusLabel.setStyle("-fx-text-fill: #ff4444;");
            return;
        }

        GoogleDriveService driveService = App.getService(GoogleDriveService.class);
        driveService.initialize(path);
        boolean ok = driveService.testConnection(folderId);
        if (ok) {
            driveStatusLabel.setText("✓ Connected successfully!");
            driveStatusLabel.setStyle("-fx-text-fill: #44ff88;");
        } else {
            // Show specific error message from the service
            String error = driveService.getLastError();
            driveStatusLabel.setText("✕ " + (error != null ? error : "Connection failed"));
            driveStatusLabel.setStyle("-fx-text-fill: #ff4444;");
            driveStatusLabel.setWrapText(true);
            driveStatusLabel.setMaxWidth(500);
        }
    }

    // ==================== PRINTER ====================

    @FXML
    private void onTestPrint() {
        // Print a small test image
        java.awt.image.BufferedImage testImg =
                new java.awt.image.BufferedImage(200, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = testImg.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.setColor(java.awt.Color.BLACK);
        g.drawString("BoothYeah Test Print", 30, 55);
        g.dispose();

        PhotoPrintService ps = App.getService(PhotoPrintService.class);
        String printer = printerCombo.getValue();
        ps.printImage(testImg, printer);
    }

    // ==================== CAMERA ====================

    @FXML
    private void onDetectCameras() {
        saveStatusLabel.setText("Scanning for cameras...");
        saveStatusLabel.setStyle("-fx-text-fill: #FFD700;");

        // Run camera detection on a background thread (can take a few seconds)
        new Thread(() -> {
            List<String> cameras = CameraService.listAvailableCameras();
            javafx.application.Platform.runLater(() -> {
                cameraCombo.getItems().clear();
                cameraCombo.getItems().addAll(cameras);
                if (!cameras.isEmpty()) {
                    cameraCombo.setValue(cameras.get(0));
                }
                boolean foundCamera = !cameras.contains("No camera detected");
                saveStatusLabel.setText(foundCamera
                        ? "Found " + cameras.size() + " camera(s). Select one, then save."
                        : "No video camera detected. Connect a webcam, UVC DSLR, or capture card.");
                saveStatusLabel.setStyle(foundCamera ? "-fx-text-fill: #44ff88;" : "-fx-text-fill: #ff4444;");
            });
        }, "camera-detect").start();
    }

    // ==================== SETTINGS ====================

    @FXML
    private void onUpdatePin() {
        String newPin = pinField.getText();
        if (newPin != null && newPin.length() >= 4) {
            settingsService.getSettings().setAdminPin(newPin);
            pinField.clear();
            saveStatusLabel.setText("PIN updated (save to persist)");
        }
    }

    @FXML
    private void onSaveSettings() {
        AppSettings s = settingsService.getSettings();

        // Gather all values from UI
        s.setGoogleDriveFolderId(driveFolderField.getText());
        s.setServiceAccountPath(serviceAccountField.getText());
        s.setGoogleDriveEnabled(driveEnabledCheck.isSelected());
        s.setPrintEnabled(printEnabledCheck.isSelected());
        if (printerCombo.getValue() != null) {
            s.setPrinterName(printerCombo.getValue());
        }
        s.setCountdownSeconds(countdownSpinner.getValue());
        s.setAutoResetSeconds(autoResetSpinner.getValue());

        // Save camera selection — extract index from "Camera N ..." string
        String camSelection = cameraCombo.getValue();
        if (camSelection != null && camSelection.startsWith("Camera ")) {
            try {
                int idx = Integer.parseInt(camSelection.split(" ")[1]);
                s.setCameraIndex(idx);
            } catch (NumberFormatException ignored) {}
        }

        // Initialize Drive service if enabled
        if (s.isGoogleDriveEnabled() && s.getServiceAccountPath() != null
                && !s.getServiceAccountPath().isEmpty()) {
            App.getService(GoogleDriveService.class).initialize(s.getServiceAccountPath());
        }

        settingsService.save();
        saveStatusLabel.setText("✓ Settings saved!");
        saveStatusLabel.setStyle("-fx-text-fill: #44ff88;");

        // Auto-clear the status label after 3 seconds
        Timeline clearTimer = new Timeline(new KeyFrame(Duration.seconds(3), ev -> {
            saveStatusLabel.setText("");
        }));
        clearTimer.play();
    }

    // ==================== HISTORY ====================

    private void refreshHistory() {
        File outputDir = new File(AppConfig.OUTPUT_DIR);
        File[] files = outputDir.listFiles();
        long totalBytes = 0;
        if (files != null) {
            for (File f : files) totalBytes += f.length();
        }
        outputSizeLabel.setText(String.format("%.1f MB", totalBytes / (1024.0 * 1024.0)));
        onRefreshDrive();
    }

    @FXML
    private void onRefreshDrive() {
        GoogleDriveService driveService = App.getService(GoogleDriveService.class);
        AppSettings s = settingsService.getSettings();

        if (!s.isGoogleDriveEnabled() || !driveService.isInitialized()) {
            driveStatusText.setText("Google Drive disabled or not initialized.");
            driveStatusText.setStyle("-fx-text-fill: #aa4444;");
            if (driveGalleryPane != null) driveGalleryPane.getChildren().clear();
            return;
        }

        driveStatusText.setText("Fetching thumbnails...");
        driveStatusText.setStyle("-fx-text-fill: #FFD700;");
        driveGalleryPane.getChildren().clear();

        new Thread(() -> {
            List<com.google.api.services.drive.model.File> files = driveService.listFiles(s.getGoogleDriveFolderId(), 30);
            javafx.application.Platform.runLater(() -> {
                if (files.isEmpty()) {
                    driveStatusText.setText("No photos found in Drive.");
                } else {
                    driveStatusText.setText(files.size() + " photos found.");
                    driveStatusText.setStyle("-fx-text-fill: #44ff88;");
                    for (com.google.api.services.drive.model.File file : files) {
                        addDriveThumbnail(file, driveService);
                    }
                }
            });
        }, "drive-fetch").start();
    }

    private void addDriveThumbnail(com.google.api.services.drive.model.File file, GoogleDriveService driveService) {
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: #222; -fx-padding: 5; -fx-background-radius: 5;");
        
        ImageView imageView = new ImageView();
        imageView.setFitWidth(140);
        imageView.setPreserveRatio(true);
        if (file.getThumbnailLink() != null) {
            imageView.setImage(new javafx.scene.image.Image(file.getThumbnailLink(), true)); // background loading
        }

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 10;");
        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this photo from Drive?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    driveStatusText.setText("Deleting...");
                    new Thread(() -> {
                        boolean ok = driveService.deleteFile(file.getId());
                        javafx.application.Platform.runLater(() -> {
                            if (ok) {
                                driveGalleryPane.getChildren().remove(card);
                                driveStatusText.setText("Deleted successfully.");
                                refreshHistory(); // update the list
                            } else {
                                driveStatusText.setText("Delete failed.");
                                driveStatusText.setStyle("-fx-text-fill: #ff4444;");
                            }
                        });
                    }).start();
                }
            });
        });

        card.getChildren().addAll(imageView, deleteBtn);
        driveGalleryPane.getChildren().add(card);
    }

    @FXML
    private void onClearOutput() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete all saved photos?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                File dir = new File(AppConfig.OUTPUT_DIR);
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
                refreshHistory();
                saveStatusLabel.setText("Output cleared");
            }
        });
    }

    // ==================== NAVIGATION ====================

    @FXML
    private void onClose() {
        App.getService(NavigationService.class).navigateTo(AppConfig.FXML_IDLE);
    }
}
