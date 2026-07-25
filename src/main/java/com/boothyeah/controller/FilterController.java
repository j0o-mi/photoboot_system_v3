package com.boothyeah.controller;

import com.boothyeah.App;
import com.boothyeah.AppConfig;
import com.boothyeah.model.CaptureSession;
import com.boothyeah.service.*;
import com.boothyeah.util.ImageUtils;
import com.boothyeah.util.ThreadPool;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the Filter Selection screen.
 * Previews filters purely in memory, writing a single output file ONLY when "Apply" is pressed.
 */
public class FilterController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(FilterController.class);

    @FXML private VBox rootPane;
    @FXML private Label titleLabel;
    @FXML private ImageView previewImage;
    @FXML private VBox filterButtonBox;
    @FXML private Button applyBtn;
    @FXML private Button backBtn;
    @FXML private Label selectedFilterLabel;
    @FXML private ProgressIndicator applyProgress;
    @FXML private Label applyStatusLabel;

    private File originalCollageFile;
    private BufferedImage originalCollageImage;
    private BufferedImage currentFilteredImage;
    private FilterService.FilterType selectedFilter = FilterService.FilterType.NONE;
    private CaptureSession session;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        NavigationService nav = App.getService(NavigationService.class);

        session = nav.getContext("session", CaptureSession.class);
        originalCollageFile = nav.getContext("collageFile", File.class);

        if (session == null) {
            log.error("Missing session in context");
            nav.navigateTo(AppConfig.FXML_IDLE);
            return;
        }

        applyBtn.setDisable(true);
        backBtn.setDisable(true);
        if (titleLabel != null) titleLabel.setText("Loading your photos...");

        // Render initial preview from session or fallback to file reading
        CompletableFuture
                .supplyAsync(() -> {
                    CollageService collageService = App.getService(CollageService.class);
                    if (collageService != null) {
                        BufferedImage rendered = collageService.renderCollageToMemory(session, FilterService.FilterType.NONE);
                        if (rendered != null) return rendered;
                    }
                    if (originalCollageFile != null) {
                        try {
                            return ImageIO.read(originalCollageFile);
                        } catch (Exception e) {
                            log.error("Failed to load original collage file", e);
                        }
                    }
                    return null;
                }, ThreadPool.getExecutor())
                .thenAccept(bufferedImage -> Platform.runLater(() -> {
                    if (bufferedImage != null) {
                        originalCollageImage = bufferedImage;
                        currentFilteredImage = bufferedImage;
                        if (previewImage != null) {
                            previewImage.setImage(ImageUtils.toFXImage(bufferedImage));
                        }
                    } else {
                        log.warn("Initial preview rendering produced null image");
                    }

                    createFilterButtons();
                    updateSelectedFilterLabel();
                    updateActiveButtonStyles(FilterService.FilterType.NONE);

                    applyBtn.setDisable(false);
                    backBtn.setDisable(false);
                    if (titleLabel != null) titleLabel.setText("Choose a Filter for Your Photos");

                    log.info("Filter screen ready");
                }))
                .exceptionally(ex -> {
                    log.error("Failed to initialize filter screen", ex);
                    Platform.runLater(() -> {
                        if (titleLabel != null) titleLabel.setText("Error loading photos");
                        applyBtn.setDisable(false);
                        backBtn.setDisable(false);
                    });
                    return null;
                });
    }

    private void createFilterButtons() {
        if (filterButtonBox == null) return;
        filterButtonBox.getChildren().clear();

        for (FilterService.FilterType filter : FilterService.FilterType.values()) {
            Button btn = new Button(filter.getDisplayName());
            btn.setUserData(filter);
            btn.setOnAction(e -> selectFilter(filter));
            filterButtonBox.getChildren().add(btn);
        }
    }

    /**
     * Renders filter preview IN-MEMORY. No output files are written to disk here!
     */
    private void selectFilter(FilterService.FilterType filter) {
        selectedFilter = filter;
        updateSelectedFilterLabel();
        updateActiveButtonStyles(filter);

        CompletableFuture
                .supplyAsync(() -> {
                    CollageService collageService = App.getService(CollageService.class);
                    if (collageService != null && session != null) {
                        return collageService.renderCollageToMemory(session, filter);
                    }
                    return null;
                }, ThreadPool.getExecutor())
                .thenAccept(previewImg -> Platform.runLater(() -> {
                    if (previewImg != null) {
                        currentFilteredImage = previewImg;
                        if (previewImage != null) {
                            previewImage.setImage(ImageUtils.toFXImage(previewImg));
                        }
                        log.info("In-memory preview updated for filter: {}", filter.getDisplayName());
                    } else {
                        log.error("Failed to generate preview image for filter: {}", filter.getDisplayName());
                    }
                }))
                .exceptionally(ex -> {
                    log.error("Error creating filter preview", ex);
                    return null;
                });
    }

    private void updateActiveButtonStyles(FilterService.FilterType activeFilter) {
        if (filterButtonBox == null) return;
        for (Node node : filterButtonBox.getChildren()) {
            if (node instanceof Button btn) {
                if (btn.getUserData() == activeFilter) {
                    if (!btn.getStyleClass().contains("filter-chip-selected")) {
                        btn.getStyleClass().add("filter-chip-selected");
                    }
                } else {
                    btn.getStyleClass().remove("filter-chip-selected");
                }
            }
        }
    }

    private void updateSelectedFilterLabel() {
        if (selectedFilterLabel != null) {
            selectedFilterLabel.setText("Selected: " + selectedFilter.getDisplayName());
        }
    }

    /**
     * Writes a SINGLE final output file ONLY when the user clicks Apply.
     */
    @FXML
    private void onApplyFilter() {
        setApplying(true);

        if (selectedFilter == FilterService.FilterType.NONE && originalCollageFile != null) {
            proceedToResult(originalCollageFile);
            return;
        }

        CompletableFuture
                .supplyAsync(() -> {
                    CollageService collageService = App.getService(CollageService.class);
                    if (collageService != null && session != null) {
                        return collageService.createCollage(session, selectedFilter);
                    }
                    return null;
                }, ThreadPool.getExecutor())
                .thenAccept(finalFile -> {
                    if (finalFile != null) {
                        proceedToResult(finalFile);
                    } else {
                        throw new RuntimeException("Failed to save final filtered collage");
                    }
                })
                .exceptionally(ex -> {
                    log.error("Filter application failed", ex);
                    Platform.runLater(() -> {
                        setApplying(false);
                        applyBtn.setText("✗ Filter Failed");
                    });
                    return null;
                });
    }

    private void proceedToResult(File filteredFile) {
        Platform.runLater(() -> {
            NavigationService nav = App.getService(NavigationService.class);
            nav.setContext("collageFile", filteredFile);
            nav.navigateTo(AppConfig.FXML_RESULT);
        });
    }

    private void setApplying(boolean applying) {
        applyBtn.setDisable(applying);
        backBtn.setDisable(applying);
        if (applyProgress != null) {
            applyProgress.setVisible(applying);
            applyProgress.setManaged(applying);
        }
        if (applyStatusLabel != null) {
            applyStatusLabel.setVisible(applying);
            applyStatusLabel.setManaged(applying);
            applyStatusLabel.setText(applying ? "Saving your photo strip..." : "");
        }
    }

    @FXML
    private void onBack() {
        NavigationService nav = App.getService(NavigationService.class);
        nav.navigateTo(AppConfig.FXML_CAPTURE);
    }
}