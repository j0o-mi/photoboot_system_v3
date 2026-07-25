package com.boothyeah.service;

import com.boothyeah.AppConfig;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages scene transitions between FXML screens.
 * Maintains a context map for passing data between controllers.
 *
 * KEY: Uses Scene.setRoot() instead of Stage.setScene() to avoid
 * fullscreen flicker when navigating between screens.
 */
public class NavigationService {
    private static final Logger log = LoggerFactory.getLogger(NavigationService.class);

    private final Stage primaryStage;
    /** Shared context for passing data between screens */
    private final Map<String, Object> context = new HashMap<>();
    /** The single Scene instance — we swap the root, never create a new Scene */
    private Scene scene;

    public NavigationService(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    /**
     * Navigate to a new screen by loading its FXML.
     * Swaps the root of the existing Scene (no fullscreen flicker).
     *
     * @param fxmlPath classpath resource path (e.g., "/com/boothyeah/fxml/idle.fxml")
     */
    public void navigateTo(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            if (scene == null) {
                // First navigation — create the Scene once
                scene = new Scene(root);
                String cssUrl = getClass().getResource(AppConfig.CSS_PATH).toExternalForm();
                scene.getStylesheets().add(cssUrl);
                primaryStage.setScene(scene);
            } else {
                // Subsequent navigations — just swap the root (no fullscreen exit)
                scene.setRoot(root);
            }

            // Ensure fullscreen is maintained (only needed for the first setScene)
            if (!primaryStage.isFullScreen()) {
                primaryStage.setFullScreen(true);
            }

            log.info("Navigated to: {}", fxmlPath);

        } catch (IOException e) {
            log.error("Failed to navigate to: {}", fxmlPath, e);
        }
    }

    /** Store a value in the shared context (for the next screen to read) */
    public void setContext(String key, Object value) {
        context.put(key, value);
    }

    /** Read a value from the shared context */
    public <T> T getContext(String key, Class<T> type) {
        Object val = context.get(key);
        return val != null ? type.cast(val) : null;
    }

    /** Remove a key from context */
    public void clearContext(String key) {
        context.remove(key);
    }

    /** Clear all context (used when resetting to Idle) */
    public void clearAllContext() {
        context.clear();
    }

    /** Get the primary stage (for file dialogs, etc.) */
    public Stage getStage() {
        return primaryStage;
    }
}
