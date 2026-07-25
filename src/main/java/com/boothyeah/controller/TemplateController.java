package com.boothyeah.controller;

import com.boothyeah.App;
import com.boothyeah.AppConfig;
import com.boothyeah.model.CaptureSession;
import com.boothyeah.model.PhotoTemplate;
import com.boothyeah.service.NavigationService;
import com.boothyeah.service.TemplateService;
import com.boothyeah.util.ImageUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for template selection screen.
 * Dynamically creates clickable cards for each available template.
 */
public class TemplateController implements Initializable {
    @FXML private FlowPane templateGrid;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        TemplateService templateService = App.getService(TemplateService.class);
        List<PhotoTemplate> templates = templateService.getTemplates();

        if (templates.isEmpty()) {
            Label noTemplates = new Label("No templates found.\nAdd templates via Admin Dashboard.");
            noTemplates.getStyleClass().add("body-text");
            noTemplates.setStyle("-fx-text-fill: #ff4444;");
            templateGrid.getChildren().add(noTemplates);
            return;
        }

        // Create a card for each template
        for (PhotoTemplate template : templates) {
            VBox card = createTemplateCard(template);
            templateGrid.getChildren().add(card);
        }
    }

    private VBox createTemplateCard(PhotoTemplate template) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("template-card");
        card.setPrefWidth(200);

        // Template preview thumbnail
        ImageView preview = new ImageView(ImageUtils.toFXImage(template.getOriginalImage()));
        preview.setFitWidth(180);
        preview.setPreserveRatio(true);

        // Template name
        Label name = new Label(template.getName());
        name.getStyleClass().add("heading-xs");
        name.setStyle("-fx-font-size: 16px;");

        // Slot count info
        Label slots = new Label(template.getSlotCount() + " photos");
        slots.getStyleClass().add("text-muted");

        card.getChildren().addAll(preview, name, slots);

        // Click to select this template
        card.setOnMouseClicked(e -> selectTemplate(template));

        return card;
    }

    private void selectTemplate(PhotoTemplate template) {
        // Create a new capture session with this template
        CaptureSession session = new CaptureSession(template);

        NavigationService nav = App.getService(NavigationService.class);
        nav.setContext("session", session);
        nav.navigateTo(AppConfig.FXML_CAPTURE);
    }

    @FXML
    private void onBack() {
        App.getService(NavigationService.class).navigateTo(AppConfig.FXML_IDLE);
    }
}
