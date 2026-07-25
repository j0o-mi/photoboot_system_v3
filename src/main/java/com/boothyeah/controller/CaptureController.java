package com.boothyeah.controller;

import com.boothyeah.App;
import com.boothyeah.AppConfig;
import com.boothyeah.model.CaptureSession;
import com.boothyeah.model.CaptureState;
import com.boothyeah.model.PhotoTemplate;
import com.boothyeah.model.SlotRegion;
import com.boothyeah.service.CameraService;
import com.boothyeah.service.CaptureSessionManager;
import com.boothyeah.service.NavigationService;
import com.boothyeah.service.SettingsService;
import com.boothyeah.util.ImageUtils;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the photo capture screen — the most complex controller.
 *
 * Responsibilities:
 * 1. Render live camera feed via AnimationTimer updating an ImageView
 * 2. Show countdown (3, 2, 1) with scale animation before each shot
 * 3. Display captured photo preview with Accept/Retake buttons
 * 4. Maintain photo thumbnails for already-captured photos
 * 5. Delegate all state logic to CaptureSessionManager
 *
 * Threading model:
 * - CameraService runs its own background thread for frame grabbing
 * - AnimationTimer runs on FX thread (~60fps), reads the latest frame
 * - CaptureSessionManager callbacks execute on FX thread via Platform.runLater
 */
public class CaptureController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(CaptureController.class);
    private static final int MAX_CAPTURE_RETRIES = 5;

    // --- FXML Injected ---
    @FXML private StackPane rootPane;
    @FXML private StackPane cameraStack;
    @FXML private ImageView cameraView;
    @FXML private Canvas    templateGuideCanvas;
    @FXML private Label photoCounter;
    @FXML private HBox thumbnailBox;
    @FXML private StackPane countdownOverlay;
    @FXML private Label countdownLabel;
    @FXML private Button takePhotoBtn;
    @FXML private Button backBtn;
    @FXML private VBox previewOverlay;
    @FXML private ImageView previewImage;
    @FXML private Button acceptBtn;
    @FXML private Button retakeBtn;

    // --- Services ---
    private CameraService    cameraService;
    private SettingsService  settingsService;
    private CaptureSessionManager sessionManager;
    private int countdownSeconds;

    // --- Template overlay ---
    /** Cached FX image of the template's transparent overlay (frame without slot fill). */
    private Image cachedTemplateImage;

    // --- Animation ---
    private AnimationTimer cameraTimer;
    private Timeline countdownTimeline;
    /** Reusable WritableImage to avoid GC pressure from frame conversion */
    private WritableImage fxFrameBuffer;

    /** Retry counter for capture (prevents infinite retry loop) */
    private int captureRetryCount = 0;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // 1. Get services
        cameraService   = App.getService(CameraService.class);
        settingsService = App.getService(SettingsService.class);
        NavigationService nav = App.getService(NavigationService.class);

        countdownSeconds = settingsService.getSettings().getCountdownSeconds();

        // 2. Get session from navigation context
        CaptureSession session = nav.getContext("session", CaptureSession.class);
        if (session == null) {
            log.error("No capture session in context!");
            nav.navigateTo(AppConfig.FXML_IDLE);
            return;
        }

        // 3. Create session manager and wire up state change callback
        sessionManager = new CaptureSessionManager(session);
        sessionManager.setOnStateChange(this::onStateChange);

        // 3b. Size the overlay canvas to match the displayed camera dimensions
        //     and cache the template's transparent frame image
        double dispH = 520.0;
        double dispW = dispH * settingsService.getSettings().getCameraWidth()
                             / settingsService.getSettings().getCameraHeight();
        templateGuideCanvas.setWidth(dispW);
        templateGuideCanvas.setHeight(dispH);

        PhotoTemplate tmpl = session.getTemplate();
        if (tmpl != null && tmpl.getTransparentImage() != null) {
            cachedTemplateImage = ImageUtils.toFXImage(tmpl.getTransparentImage());
        }

        // 4. Start camera (use selected index from settings)
        cameraService.start(
                settingsService.getSettings().getCameraIndex(),
                settingsService.getSettings().getCameraWidth(),
                settingsService.getSettings().getCameraHeight()
        );

        // 5. Let camera view rely on FXML fitHeight
        // Removed rootPane bindings to avoid stretching and crushing layout

        // 6. Bind preview image size
        previewImage.fitWidthProperty().bind(rootPane.widthProperty().multiply(0.5));
        previewImage.fitHeightProperty().bind(rootPane.heightProperty().multiply(0.6));

        // 7. Start live camera feed
        startCameraFeed();

        // 8. Update UI for initial state
        updatePhotoCounter();
        updateThumbnails();

        // 9b. Draw the initial template guide after layout has settled
        Platform.runLater(() -> updateTemplateGuide(true));

        // 9. Camera warmup check — verify camera started and producing frames
        //    Give the camera up to 3 seconds to produce a first frame
        PauseTransition warmupCheck = new PauseTransition(Duration.seconds(3));
        warmupCheck.setOnFinished(e -> {
            if (!cameraService.isRunning()) {
                showCameraError("Camera failed to start.\n\n" + cameraService.getLastError());
            } else {
                log.info("Camera warmup OK — live feed active");
            }
        });
        warmupCheck.play();
    }

    // ==================== CAMERA FEED ====================

    /**
     * Start the AnimationTimer that renders the live camera feed.
     * The AnimationTimer fires once per JavaFX pulse (~60fps).
     * We read the latest frame from the CameraService (thread-safe).
     */
    private void startCameraFeed() {
        if (cameraTimer != null) cameraTimer.stop();

        cameraTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                BufferedImage frame = cameraService.getLatestFrame();
                if (frame != null) {
                    // Mirror the camera feed horizontally (selfie mode)
                    cameraView.setScaleX(-1);
                    // Reuse the WritableImage buffer for performance
                    fxFrameBuffer = ImageUtils.toFXImage(frame, fxFrameBuffer);
                    cameraView.setImage(fxFrameBuffer);
                }
            }
        };
        cameraTimer.start();
    }

    private void stopCameraFeed() {
        if (cameraTimer != null) cameraTimer.stop();
    }

    // ==================== STATE CHANGE HANDLER ====================

    /**
     * Called by CaptureSessionManager on every state transition.
     * Updates the UI to match the new state.
     */
    private void onStateChange(CaptureState newState) {
        Platform.runLater(() -> {
            switch (newState) {
                case COUNTDOWN -> startCountdown();
                case CAPTURING -> capturePhoto();
                case PREVIEWING -> showPreview();
                case ALL_CAPTURED -> finishCapture();
                case WAITING_TO_START -> showWaiting();
            }
        });
    }

    // ==================== COUNTDOWN ====================

    private void startCountdown() {
        takePhotoBtn.setVisible(false);
        backBtn.setVisible(false);
        previewOverlay.setVisible(false);
        countdownOverlay.setVisible(true);
        updateTemplateGuide(false); // hide guide during countdown
        updatePhotoCounter();

        // Reset countdown label style (may have been changed by error display)
        countdownLabel.setStyle("");
        countdownLabel.getStyleClass().setAll("countdown");

        // Cancel any existing countdown
        if (countdownTimeline != null) countdownTimeline.stop();

        countdownTimeline = new Timeline();
        for (int i = 0; i <= countdownSeconds; i++) {
            final int display = countdownSeconds - i;
            KeyFrame kf = new KeyFrame(Duration.seconds(i), e -> {
                if (display > 0) {
                    countdownLabel.setText(String.valueOf(display));
                    // Pop-in scale animation for each number
                    ScaleTransition pop = new ScaleTransition(Duration.millis(400), countdownLabel);
                    pop.setFromX(1.8);
                    pop.setFromY(1.8);
                    pop.setToX(1.0);
                    pop.setToY(1.0);
                    pop.setInterpolator(Interpolator.EASE_OUT);
                    pop.play();
                } else {
                    // Countdown finished — trigger capture
                    countdownOverlay.setVisible(false);
                    sessionManager.countdownFinished();
                }
            });
            countdownTimeline.getKeyFrames().add(kf);
        }
        countdownTimeline.play();
    }

    // ==================== CAPTURE ====================

    /**
     * Capture the current camera frame.
     * Uses captureFrame() which returns a deep copy safe for storage.
     *
     * If the camera returns null, retries up to MAX_CAPTURE_RETRIES times
     * with a 300ms delay between attempts. If all retries fail, shows an error.
     */
    private void capturePhoto() {
        BufferedImage photo = cameraService.captureFrame();
        if (photo != null) {
            captureRetryCount = 0;
            sessionManager.storePhoto(photo);
        } else if (captureRetryCount < MAX_CAPTURE_RETRIES) {
            captureRetryCount++;
            log.warn("Camera returned null frame during capture, retry {}/{}",
                    captureRetryCount, MAX_CAPTURE_RETRIES);

            // Wait 300ms and try again
            PauseTransition retryDelay = new PauseTransition(Duration.millis(300));
            retryDelay.setOnFinished(e -> capturePhoto());
            retryDelay.play();
        } else {
            // All retries exhausted — show error
            log.error("Camera failed to capture after {} retries", MAX_CAPTURE_RETRIES);
            captureRetryCount = 0;
            showCameraError("Camera couldn't capture a photo.\nPlease check your camera connection.");
        }
    }

    // ==================== PREVIEW ====================

    private void showPreview() {
        stopCameraFeed();
        takePhotoBtn.setVisible(false);
        countdownOverlay.setVisible(false);
        previewOverlay.setVisible(true);
        updateTemplateGuide(false); // hide guide during preview

        // Show the captured photo
        BufferedImage photo = sessionManager.getCurrentPhoto();
        if (photo != null) {
            previewImage.setScaleX(-1); // Mirror to match camera feed
            previewImage.setImage(ImageUtils.toFXImage(photo));
        }

        // Update thumbnails to show this photo
        updateThumbnails();
    }

    // ==================== ACCEPT / RETAKE ====================

    @FXML
    private void onAccept() {
        sessionManager.accept();
        if (sessionManager.getState() != CaptureState.ALL_CAPTURED) {
            previewOverlay.setVisible(false);
            startCameraFeed();
            updateTemplateGuide(true);
        }
    }

    @FXML
    private void onRetake() {
        previewOverlay.setVisible(false);
        startCameraFeed();
        sessionManager.retake();
        updateTemplateGuide(true);
    }

    @FXML
    private void onTakePhoto() {
        // Only start if camera is producing frames
        if (!cameraService.isRunning()) {
            showCameraError("Camera is not ready.\n\n" + cameraService.getLastError());
            return;
        }
        sessionManager.startCapture();
    }

    @FXML
    private void onBack() {
        stopCameraFeed();
        cameraService.stop();
        NavigationService nav = App.getService(NavigationService.class);
        nav.navigateTo(AppConfig.FXML_TEMPLATE);
    }

    // ==================== WAITING STATE ====================

    private void showWaiting() {
        takePhotoBtn.setVisible(true);
        backBtn.setVisible(true);
        countdownOverlay.setVisible(false);
        previewOverlay.setVisible(false);
        updatePhotoCounter();
        updateTemplateGuide(true); // show guide when user is positioning
    }

    // ==================== FINISH ====================

    private void finishCapture() {
        stopCameraFeed();
        cameraService.stop();

        // Pass session to processing screen
        NavigationService nav = App.getService(NavigationService.class);
        nav.setContext("session", sessionManager.getSession());
        nav.navigateTo(AppConfig.FXML_PROCESSING);
    }

    // ==================== CAMERA ERROR ====================

    /**
     * Show a camera error alert and navigate back to Idle screen.
     * Stops camera and cleans up resources.
     */
    private void showCameraError(String message) {
        stopCameraFeed();
        cameraService.stop();

        log.error("Camera error displayed to user: {}", message);

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            alert.setTitle("Camera Error");
            alert.setHeaderText("Camera Not Available");
            alert.getDialogPane().setMinWidth(500);
            alert.showAndWait();

            NavigationService nav = App.getService(NavigationService.class);
            nav.navigateTo(AppConfig.FXML_IDLE);
        });
    }

    // ==================== TEMPLATE GUIDE OVERLAY ====================

    /**
     * Draw (or clear) the template guide overlay on the camera canvas.
     *
     * When visible, shows:
     *  - The template frame image scaled so the current slot maps to
     *    the center-crop area of the camera (semi-transparent, 65% opacity)
     *  - A dark vignette outside the crop area
     *  - Gold corner brackets marking the exact crop boundaries
     *  - Slot label ("Photo N of M")
     */
    private void updateTemplateGuide(boolean visible) {
        GraphicsContext gc = templateGuideCanvas.getGraphicsContext2D();
        double dispW = templateGuideCanvas.getWidth();
        double dispH = templateGuideCanvas.getHeight();
        gc.clearRect(0, 0, dispW, dispH);

        if (!visible || sessionManager == null) return;

        CaptureSession session = sessionManager.getSession();
        PhotoTemplate template = session.getTemplate();
        int slotIdx = sessionManager.getCurrentIndex();

        log.debug("updateTemplateGuide: template={} slotIdx={} canvasSize={}x{} cachedImg={}",
                template != null ? template.getName() : "null", slotIdx,
                dispW, dispH, cachedTemplateImage != null ? "ok" : "null");

        if (template == null || template.getSlots() == null
                || slotIdx >= template.getSlots().size()) return;

        SlotRegion slot = template.getSlots().get(slotIdx);

        // --- Slot dimensions (for aspect ratio calculation) ---
        double slotW, slotH, slotX, slotY;
        if (slot.hasCustomCorners()) {
            double wTop = Math.hypot(slot.getTrX()-slot.getTlX(), slot.getTrY()-slot.getTlY());
            double wBot = Math.hypot(slot.getBrX()-slot.getBlX(), slot.getBrY()-slot.getBlY());
            double hL   = Math.hypot(slot.getBlX()-slot.getTlX(), slot.getBlY()-slot.getTlY());
            double hR   = Math.hypot(slot.getBrX()-slot.getTrX(), slot.getBrY()-slot.getTrY());
            slotW = (wTop + wBot) / 2.0;
            slotH = (hL  + hR)  / 2.0;
            slotX = Math.min(slot.getTlX(), slot.getBlX());
            slotY = Math.min(slot.getTlY(), slot.getTrY());
        } else {
            slotW = slot.getWidth();
            slotH = slot.getHeight();
            slotX = slot.getX();
            slotY = slot.getY();
        }
        if (slotW <= 0 || slotH <= 0) return;

        // --- Compute center-crop rectangle in display pixels ---
        double camW = settingsService.getSettings().getCameraWidth();
        double camH = settingsService.getSettings().getCameraHeight();
        double slotRatio = slotW / slotH;
        double camRatio  = camW / camH;

        double srcX, srcY, srcW, srcH;
        if (camRatio > slotRatio) {          // camera wider → crop left/right
            srcH = camH; srcW = srcH * slotRatio;
            srcX = (camW - srcW) / 2; srcY = 0;
        } else {                              // camera taller → crop top/bottom
            srcW = camW; srcH = srcW / slotRatio;
            srcX = 0; srcY = (camH - srcH) / 2;
        }
        double ds  = dispH / camH;           // display scale factor
        double gx  = srcX * ds;
        double gy  = srcY * ds;
        double gw  = srcW * ds;
        double gh  = srcH * ds;

        // --- Template frame overlay ---
        // Scale so the current slot's position maps onto the crop guide area
        double tSx = gw / slotW;
        double tSy = gh / slotH;
        double tOx = gx - slotX * tSx;
        double tOy = gy - slotY * tSy;

        if (cachedTemplateImage != null) {
            gc.setGlobalAlpha(0.60);
            gc.drawImage(cachedTemplateImage,
                    tOx, tOy,
                    template.getWidth()  * tSx,
                    template.getHeight() * tSy);
            gc.setGlobalAlpha(1.0);
        }

        // --- Dark vignette outside crop area ---
        gc.setFill(Color.color(0, 0, 0, 0.35));
        gc.fillRect(0,       0,       gx,          dispH);          // left
        gc.fillRect(gx + gw, 0,       dispW-gx-gw, dispH);          // right
        gc.fillRect(gx,      0,       gw,          gy);             // top
        gc.fillRect(gx,      gy + gh, gw,          dispH-gy-gh);    // bottom

        // --- Gold corner brackets ---
        double cl = Math.min(22, gw * 0.08);
        gc.setStroke(Color.web("#FFD700"));
        gc.setLineWidth(3.5);
        // TL
        gc.strokeLine(gx,    gy,    gx+cl, gy);
        gc.strokeLine(gx,    gy,    gx,    gy+cl);
        // TR
        gc.strokeLine(gx+gw, gy,    gx+gw-cl, gy);
        gc.strokeLine(gx+gw, gy,    gx+gw,    gy+cl);
        // BL
        gc.strokeLine(gx,    gy+gh, gx+cl,    gy+gh);
        gc.strokeLine(gx,    gy+gh, gx,        gy+gh-cl);
        // BR
        gc.strokeLine(gx+gw, gy+gh, gx+gw-cl, gy+gh);
        gc.strokeLine(gx+gw, gy+gh, gx+gw,    gy+gh-cl);

        // --- Slot label ---
        gc.setFont(Font.font("System", FontWeight.BOLD, 15));
        gc.setTextAlign(TextAlignment.CENTER);
        // Label background pill
        String label = "Photo " + (slotIdx + 1) + " of " + template.getSlotCount();
        gc.setFill(Color.color(0, 0, 0, 0.6));
        gc.fillRoundRect(gx + gw/2 - 75, gy + 10, 150, 26, 10, 10);
        gc.setFill(Color.web("#FFD700"));
        gc.fillText(label, gx + gw / 2, gy + 28);
    }

    // ==================== UI HELPERS ====================

    private void updatePhotoCounter() {
        int current = sessionManager != null ? sessionManager.getCurrentIndex() + 1 : 1;
        int total = sessionManager != null ? sessionManager.getSession().getTotalPhotos() : 3;
        photoCounter.setText("Photo " + current + " of " + total);
    }

    private void updateThumbnails() {
        thumbnailBox.getChildren().clear();
        CaptureSession session = sessionManager.getSession();
        BufferedImage[] photos = session.getPhotos();

        for (int i = 0; i < photos.length; i++) {
            StackPane thumbContainer = new StackPane();
            thumbContainer.setPrefSize(200, 150);
            thumbContainer.setMinSize(200, 150);
            thumbContainer.setMaxSize(200, 150);
            thumbContainer.setStyle("-fx-background-color: rgba(255, 255, 255, 0.1); -fx-background-radius: 8;");
            
            if (photos[i] != null) {
                ImageView thumb = new ImageView();
                thumb.setFitWidth(200);
                thumb.setFitHeight(150);
                thumb.setPreserveRatio(true);
                thumb.setImage(ImageUtils.toFXImage(photos[i]));
                thumb.setScaleX(-1); // Mirror
                thumbContainer.getChildren().add(thumb);
                thumbContainer.setStyle("-fx-border-color: #44ff88; -fx-border-width: 2; -fx-border-radius: 8;");
            }
            if (i == session.getCurrentIndex()) {
                thumbContainer.setStyle(thumbContainer.getStyle() + " -fx-border-color: #FFD700; -fx-border-width: 3; -fx-border-radius: 8;");
            }
            thumbnailBox.getChildren().add(thumbContainer);
        }
    }
}
