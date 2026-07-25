package com.boothyeah.controller;

import com.boothyeah.App;
import com.boothyeah.AppConfig;
import com.boothyeah.service.NavigationService;
import com.boothyeah.service.SettingsService;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the Idle/Start screen.
 * Shows the app logo and a pulsing "Tap to Start" button.
 * Secret shortcut Ctrl+Shift+A opens admin dashboard.
 *
 * PIN entry uses an inline overlay instead of a modal dialog
 * because JavaFX modal dialogs freeze in macOS fullscreen mode.
 */
public class IdleController implements Initializable {
    @FXML private StackPane rootPane;
    @FXML private Label titleLabel;
    @FXML private Button startButton;
    @FXML private Label adminHint;

    // Inline PIN overlay (replaces frozen TextInputDialog)
    @FXML private StackPane pinOverlay;
    @FXML private PasswordField pinField;
    @FXML private Label pinErrorLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Pulse animation on the Start button
        ScaleTransition pulse = new ScaleTransition(Duration.millis(1200), startButton);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.06);
        pulse.setToY(1.06);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        pulse.play();

        // Fade in the title
        FadeTransition fadeTitle = new FadeTransition(Duration.millis(1500), titleLabel);
        fadeTitle.setFromValue(0);
        fadeTitle.setToValue(1);
        fadeTitle.play();

        // Register Ctrl+Shift+A shortcut for admin
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(event -> {
                    if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.A) {
                        showPinOverlay();
                    }
                    // Also allow Enter key to submit PIN
                    if (pinOverlay.isVisible() && event.getCode() == KeyCode.ENTER) {
                        onPinSubmit();
                    }
                    // Escape to close PIN overlay
                    if (pinOverlay.isVisible() && event.getCode() == KeyCode.ESCAPE) {
                        onPinCancel();
                    }
                });
            }
        });
    }

    @FXML
    private void onStartTapped() {
        NavigationService nav = App.getService(NavigationService.class);
        nav.navigateTo(AppConfig.FXML_TEMPLATE);
    }

    /**
     * Show inline PIN overlay instead of a modal dialog.
     * Modal dialogs freeze on macOS fullscreen.
     */
    private void showPinOverlay() {
        pinField.clear();
        pinErrorLabel.setText("");
        pinOverlay.setVisible(true);
        pinOverlay.setManaged(true);

        // Fade in the overlay
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), pinOverlay);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // Focus the PIN field after animation
        fadeIn.setOnFinished(e -> pinField.requestFocus());
    }

    @FXML
    private void onPinSubmit() {
        String pin = pinField.getText();
        String correctPin = App.getService(SettingsService.class).getSettings().getAdminPin();

        if (pin.equals(correctPin)) {
            pinOverlay.setVisible(false);
            pinOverlay.setManaged(false);
            NavigationService nav = App.getService(NavigationService.class);
            nav.navigateTo(AppConfig.FXML_ADMIN);
        } else {
            pinErrorLabel.setText("Incorrect PIN");
            pinField.clear();

            // Shake animation on wrong PIN
            TranslateTransition shake = new TranslateTransition(Duration.millis(50), pinField);
            shake.setByX(10);
            shake.setCycleCount(6);
            shake.setAutoReverse(true);
            shake.setOnFinished(e -> pinField.setTranslateX(0));
            shake.play();
        }
    }

    @FXML
    private void onPinCancel() {
        // Fade out and hide
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), pinOverlay);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            pinOverlay.setVisible(false);
            pinOverlay.setManaged(false);
        });
        fadeOut.play();
    }
}
