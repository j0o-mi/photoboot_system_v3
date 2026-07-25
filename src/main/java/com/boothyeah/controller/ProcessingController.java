package com.boothyeah.controller;

import com.boothyeah.App;
import com.boothyeah.AppConfig;
import com.boothyeah.model.CaptureSession;
import com.boothyeah.service.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the Processing screen.
 * Creates the collage, then navigates to the Filter selection screen.
 * User can apply filters before viewing the final result.
 */
public class ProcessingController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ProcessingController.class);

    @FXML private Label titleLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;
    @FXML private Label subStatusLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        NavigationService nav = App.getService(NavigationService.class);
        CaptureSession session = nav.getContext("session", CaptureSession.class);

        if (session == null) {
            log.error("No session in context for processing!");
            nav.navigateTo(AppConfig.FXML_IDLE);
            return;
        }

        progressIndicator.setProgress(-1); // Indeterminate

        // The filter screen renders previews in memory. The only output file is
        // created after the user confirms their selected filter.
        updateStatus("Preparing your photo strip...", "Choose a filter before saving");
        Platform.runLater(() -> {
            NavigationService navService = App.getService(NavigationService.class);
            navService.clearContext("collageFile");
            navService.setContext("session", session);
            navService.navigateTo(AppConfig.FXML_FILTER);
        });
    }

    private void updateStatus(String main, String sub) {
        Platform.runLater(() -> {
            statusLabel.setText(main);
            subStatusLabel.setText(sub);
        });
    }
}
