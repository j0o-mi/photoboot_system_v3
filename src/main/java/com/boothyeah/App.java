package com.boothyeah;

import com.boothyeah.service.*;
import com.boothyeah.util.ThreadPool;
import javafx.application.Application;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Main entry point for BoothYeah Photobooth application.
 *
 * Initializes all services as singletons, loads fonts, and navigates to the Idle screen.
 * Services are accessible globally via App.getService(ServiceClass.class).
 *
 * Launch: mvn javafx:run
 */
public class App extends Application {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    /** Service locator — simple DI for non-Spring applications */
    private static final Map<Class<?>, Object> services = new HashMap<>();

    @Override
    public void start(Stage primaryStage) {
        log.info("Starting {} v{}", AppConfig.APP_NAME, AppConfig.APP_VERSION);

        // 1. Ensure data directories exist
        AppConfig.ensureDirectories();

        // 2. Load custom fonts
        loadFonts();

        // 3. Initialize all services
        initializeServices(primaryStage);

        // 4. Configure the stage for kiosk mode
        primaryStage.setTitle(AppConfig.APP_NAME);
        primaryStage.setFullScreen(true);
        primaryStage.setFullScreenExitHint(""); // No "press ESC" hint

        // 5. Navigate to the idle screen
        NavigationService nav = getService(NavigationService.class);
        nav.navigateTo(AppConfig.FXML_IDLE);

        primaryStage.show();
        log.info("Application started successfully");
    }

    @Override
    public void stop() {
        log.info("Shutting down application...");

        // Stop camera if running
        CameraService cam = getService(CameraService.class);
        if (cam != null) cam.stop();

        // Shutdown thread pool
        ThreadPool.shutdown();

        log.info("Application stopped");
    }

    /**
     * Initialize all application services.
     * Services are stored in a static map for global access.
     */
    private void initializeServices(Stage primaryStage) {
        // Settings must be first — other services may depend on it
        SettingsService settingsService = new SettingsService();
        services.put(SettingsService.class, settingsService);

        // Template service — loads templates and detects slots
        TemplateService templateService = new TemplateService();
        templateService.loadAll();
        services.put(TemplateService.class, templateService);

        // Camera service — opened when entering capture screen, not here
        services.put(CameraService.class, new CameraService());

        // Collage service — stateless, creates composites
        services.put(CollageService.class, new CollageService());

        // Filter service — stateless, applies filters to images
        services.put(FilterService.class, new FilterService());

        // Google Drive service — initialized lazily when credentials are configured
        GoogleDriveService driveService = new GoogleDriveService();
        String credPath = settingsService.getSettings().getServiceAccountPath();
        if (credPath != null && !credPath.isEmpty() && new java.io.File(credPath).exists()) {
            driveService.initialize(credPath);
        }
        services.put(GoogleDriveService.class, driveService);

        // QR code service — stateless
        services.put(QRCodeService.class, new QRCodeService());

        // Print service — stateless wrapper around Java Print API
        services.put(PhotoPrintService.class, new PhotoPrintService());

        // Navigation service — needs stage reference
        services.put(NavigationService.class, new NavigationService(primaryStage));

        log.info("All services initialized ({} templates loaded)", templateService.getTemplates().size());
    }

    /**
     * Load custom fonts from resources.
     * Fonts must be loaded BEFORE any FXML/CSS that references them.
     */
    private void loadFonts() {
        // Space Grotesk — used for headings (free from Google Fonts)
        loadFont("/com/boothyeah/fonts/SpaceGrotesk-Regular.ttf");
        loadFont("/com/boothyeah/fonts/SpaceGrotesk-Bold.ttf");
        // Inter — used for body text
        loadFont("/com/boothyeah/fonts/Inter-Regular.ttf");
        loadFont("/com/boothyeah/fonts/Inter-SemiBold.ttf");
        // Custom
        loadFont("/com/boothyeah/fonts/LEMON.otf");
        loadFont("/com/boothyeah/fonts/Boring Time.otf");
        loadFont("/com/boothyeah/fonts/Purple Smile.otf");
    }

    private void loadFont(String path) {
        try {
            InputStream is = getClass().getResourceAsStream(path);
            if (is != null) {
                Font font = Font.loadFont(is, 14);
                if (font != null) {
                    log.debug("Loaded font: {}", font.getName());
                }
                is.close();
            } else {
                log.warn("Font not found (will use system fallback): {}", path);
            }
        } catch (Exception e) {
            log.warn("Failed to load font: {}", path, e);
        }
    }

    /**
     * Global service locator.
     * Controllers use this to access shared services.
     *
     * @param type the service class
     * @return the singleton service instance
     */
    @SuppressWarnings("unchecked")
    public static <T> T getService(Class<T> type) {
        return (T) services.get(type);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
