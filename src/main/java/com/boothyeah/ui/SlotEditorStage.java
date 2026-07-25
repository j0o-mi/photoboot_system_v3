package com.boothyeah.ui;

import com.boothyeah.model.PhotoTemplate;
import com.boothyeah.model.SlotRegion;
import com.boothyeah.util.ImageUtils;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modal Stage for editing slot positions in a photo template.
 *
 * Features:
 * - Up to 4 draggable quadrilateral slots (Photo 1/2/3/4)
 * - 4 draggable corner handles per slot
 * - Scroll wheel to zoom in/out (pivot-stable: zooms toward cursor)
 * - Zoom buttons + reset; zoom range 25%–1600%
 * - Magnifier loupe while dragging a corner
 */
public class SlotEditorStage extends Stage {

    private static final int    MAX_SLOTS      = 4;
    private static final double HANDLE_RADIUS  = 9.0;
    private static final double BASE_WIDTH     = 420.0; // display width at zoom=1

    // Zoom
    private static final double ZOOM_MIN  = 0.25;
    private static final double ZOOM_MAX  = 16.0;
    private static final double ZOOM_STEP = 1.30; // per scroll tick

    // Magnifier
    private static final double MAG_RADIUS   = 70.0;
    private static final double MAG_ZOOM     = 4.0;
    private static final double MAG_OFFSET_X = 90.0;
    private static final double MAG_OFFSET_Y = -90.0;

    private static final Color[] SLOT_COLORS = {
            Color.web("#FF6B6B"), Color.web("#4ECDC4"), Color.web("#FFE66D"), Color.web("#9B59B6")
    };
    private static final String[] SLOT_NAMES = {"Photo 1", "Photo 2", "Photo 3", "Photo 4"};

    // ---- State ----
    private final PhotoTemplate    template;
    private final Image             templateFXImage;
    private final List<SlotRegion>  editableSlots = new ArrayList<>();
    private boolean saved = false;

    /** Base display height at zoom=1, computed once from template aspect ratio */
    private final double baseDisplayHeight;

    /** Current zoom level (1.0 = fits BASE_WIDTH). scaleX/Y are derived from this. */
    private double zoomLevel = 1.0;

    /**
     * Convert display pixels → template pixels.
     * scaleX = template.width / (BASE_WIDTH * zoom)
     */
    private double scaleX;
    private double scaleY;

    // Drag state
    private int    dragSlot   = -1;
    private int    dragCorner = -1;
    private double dragMouseX = 0;
    private double dragMouseY = 0;
    private int    activeSlot = 0;

    // UI fields needed for zoom logic
    private Canvas     canvas;
    private ImageView  imageView;
    private ScrollPane scrollPane;
    private Label      zoomLabel;
    private Label      statusLabel;

    // ==================== CONSTRUCTOR ====================

    public SlotEditorStage(PhotoTemplate template, Window owner) {
        this.template        = template;
        this.templateFXImage = ImageUtils.toFXImage(template.getOriginalImage());
        this.baseDisplayHeight = BASE_WIDTH * template.getHeight() / template.getWidth();

        for (SlotRegion orig : template.getSlots()) {
            SlotRegion copy = copySlot(orig);
            if (!copy.hasCustomCorners()) copy.initCornersFromRect();
            editableSlots.add(copy);
        }
        if (editableSlots.isEmpty()) {
            editableSlots.add(defaultSlot(0, template.getWidth(), template.getHeight()));
        }

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Edit Slot Layout — " + template.getName());
        setResizable(true);

        computeScale();
        buildUI();
    }

    // ==================== UI BUILD ====================

    private void buildUI() {
        imageView = new ImageView(templateFXImage);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        canvas = new Canvas();

        applyZoomSize(); // set initial canvas/imageView sizes

        StackPane imageStack = new StackPane(imageView, canvas);
        imageStack.setAlignment(Pos.TOP_LEFT);
        // Let StackPane shrink-wrap the canvas
        imageStack.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        imageStack.setPrefSize(canvas.getWidth(), canvas.getHeight());
        imageStack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        scrollPane = new ScrollPane(imageStack);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setPrefViewportWidth(460);
        scrollPane.setPrefViewportHeight(660);
        scrollPane.setStyle("-fx-background-color: #111; -fx-background: #111;");

        // Slot selector + zoom bar at top
        VBox topBox = new VBox(6, buildSlotSelector(), buildZoomBar(), buildInstructionsLabel());
        topBox.setPadding(new Insets(10, 14, 6, 14));
        topBox.setStyle("-fx-background-color: #1a1a2e;");

        // Bottom bar
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #333; -fx-text-fill: white; " +
                "-fx-font-size: 14px; -fx-padding: 8 20; -fx-background-radius: 4;");
        cancelBtn.setOnAction(e -> close());

        Button saveBtn = new Button("Save Layout");
        saveBtn.setStyle("-fx-background-color: #FFD700; -fx-text-fill: #000; " +
                "-fx-font-size: 14px; -fx-padding: 8 20; -fx-background-radius: 4; " +
                "-fx-font-weight: bold;");
        saveBtn.setOnAction(e -> { saved = true; close(); });

        HBox bottomBar = new HBox(12, statusLabel, spacer, cancelBtn, saveBtn);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 14, 10, 14));
        bottomBar.setStyle("-fx-background-color: #1a1a2e;");

        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(scrollPane);
        root.setBottom(bottomBar);
        root.setStyle("-fx-background-color: #0a0a0a;");

        setScene(new Scene(root, 500, 840));
        setupMouseHandlers();
        redraw();
    }

    private HBox buildSlotSelector() {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);

        for (int i = 0; i < MAX_SLOTS; i++) {
            final int idx = i;
            Button btn = new Button(SLOT_NAMES[i]);
            btn.setPrefWidth(90);
            btn.setStyle(slotButtonStyle(i, i == activeSlot));
            btn.setOnAction(e -> { activeSlot = idx; refreshSlotButtons(row); redraw(); });
            row.getChildren().add(btn);
        }

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        Button addBtn = new Button("+ Add");
        addBtn.setStyle("-fx-background-color: #2a4a2a; -fx-text-fill: #44ff88; " +
                "-fx-background-radius: 4; -fx-font-size: 12px; -fx-padding: 4 10;");
        addBtn.setOnAction(e -> {
            if (editableSlots.size() < MAX_SLOTS) {
                editableSlots.add(defaultSlot(editableSlots.size(), template.getWidth(), template.getHeight()));
                activeSlot = editableSlots.size() - 1;
                refreshSlotButtons(row);
                redraw();
            }
        });

        Button removeBtn = new Button("− Remove");
        removeBtn.setStyle("-fx-background-color: #4a1a1a; -fx-text-fill: #ff4444; " +
                "-fx-background-radius: 4; -fx-font-size: 12px; -fx-padding: 4 10;");
        removeBtn.setOnAction(e -> {
            if (editableSlots.size() > 1 && activeSlot < editableSlots.size()) {
                editableSlots.remove(activeSlot);
                activeSlot = Math.min(activeSlot, editableSlots.size() - 1);
                refreshSlotButtons(row);
                redraw();
            }
        });

        row.getChildren().addAll(gap, addBtn, removeBtn);
        return row;
    }

    private HBox buildZoomBar() {
        zoomLabel = new Label("100%");
        zoomLabel.setStyle("-fx-text-fill: #ddd; -fx-font-size: 12px; -fx-min-width: 46px; " +
                "-fx-alignment: center;");

        Button zoomOut = zoomBtn("−", () -> zoomCenter(zoomLevel / ZOOM_STEP));
        Button zoomIn  = zoomBtn("+", () -> zoomCenter(zoomLevel * ZOOM_STEP));
        Button reset   = zoomBtn("⌂", () -> zoomCenter(1.0));

        Label tip = new Label("Ctrl+Scroll to zoom");
        tip.setStyle("-fx-text-fill: #555; -fx-font-size: 11px;");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        HBox row = new HBox(6, zoomOut, zoomLabel, zoomIn, reset, gap, tip);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button zoomBtn(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #333; -fx-text-fill: white; " +
                "-fx-font-size: 14px; -fx-padding: 2 10; -fx-background-radius: 4;");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private Label buildInstructionsLabel() {
        Label lbl = new Label("Drag corners to reposition.  1=TL  2=TR  3=BR  4=BL  |  Ctrl+Scroll to zoom");
        lbl.setStyle("-fx-text-fill: #555; -fx-font-size: 11px;");
        return lbl;
    }

    // ==================== ZOOM ====================

    /**
     * Zoom centered on the middle of the current viewport (for button clicks).
     */
    private void zoomCenter(double newZoom) {
        Bounds vp = scrollPane.getViewportBounds();
        zoomAt(vp.getWidth() / 2.0 + scrollOffsetX(),
               vp.getHeight() / 2.0 + scrollOffsetY(),
               newZoom);
    }

    /**
     * Zoom toward a specific canvas point (pivot), keeping that point
     * visually stationary in the viewport.
     *
     * @param canvasPivotX  X in canvas pixels (includes scroll offset)
     * @param canvasPivotY  Y in canvas pixels
     * @param newZoom       desired zoom level
     */
    private void zoomAt(double canvasPivotX, double canvasPivotY, double newZoom) {
        newZoom = clamp(newZoom, ZOOM_MIN, ZOOM_MAX);
        if (newZoom == zoomLevel) return;

        // Template pixel under the pivot (invariant through zoom)
        double tpX = canvasPivotX * scaleX;
        double tpY = canvasPivotY * scaleY;

        // Viewport position of the pivot
        double vpX = canvasPivotX - scrollOffsetX();
        double vpY = canvasPivotY - scrollOffsetY();

        // Apply new zoom
        zoomLevel = newZoom;
        computeScale();
        applyZoomSize();
        redraw();
        updateZoomLabel();

        // After layout settles, shift scroll so the same template pixel
        // stays under the same viewport position
        Platform.runLater(() -> {
            double newCanvasX = tpX / scaleX;
            double newCanvasY = tpY / scaleY;

            double maxH = Math.max(1, canvas.getWidth()  - scrollPane.getViewportBounds().getWidth());
            double maxV = Math.max(1, canvas.getHeight() - scrollPane.getViewportBounds().getHeight());

            scrollPane.setHvalue(clamp((newCanvasX - vpX) / maxH, 0, 1));
            scrollPane.setVvalue(clamp((newCanvasY - vpY) / maxV, 0, 1));
        });
    }

    /** Current horizontal scroll offset in canvas pixels */
    private double scrollOffsetX() {
        double max = Math.max(0, canvas.getWidth() - scrollPane.getViewportBounds().getWidth());
        return scrollPane.getHvalue() * max;
    }

    /** Current vertical scroll offset in canvas pixels */
    private double scrollOffsetY() {
        double max = Math.max(0, canvas.getHeight() - scrollPane.getViewportBounds().getHeight());
        return scrollPane.getVvalue() * max;
    }

    /** Recompute scaleX/Y from current zoomLevel */
    private void computeScale() {
        double w = BASE_WIDTH  * zoomLevel;
        double h = baseDisplayHeight * zoomLevel;
        scaleX = template.getWidth()  / w;
        scaleY = template.getHeight() / h;
    }

    /** Resize the ImageView and Canvas to match current zoomLevel */
    private void applyZoomSize() {
        double w = BASE_WIDTH  * zoomLevel;
        double h = baseDisplayHeight * zoomLevel;
        imageView.setFitWidth(w);
        canvas.setWidth(w);
        canvas.setHeight(h);
        // Keep the StackPane wrapper in sync
        if (canvas.getParent() instanceof StackPane sp) {
            sp.setPrefSize(w, h);
            sp.setMinSize(w, h);
            sp.setMaxSize(w, h);
        }
    }

    private void updateZoomLabel() {
        if (zoomLabel != null)
            zoomLabel.setText(String.format("%.0f%%", zoomLevel * 100));
    }

    // ==================== MOUSE HANDLING ====================

    private void setupMouseHandlers() {
        // Drag corners
        canvas.setOnMousePressed(e -> {
            int[] hit = findHandle(e.getX(), e.getY());
            if (hit != null) {
                dragSlot   = hit[0];
                dragCorner = hit[1];
                dragMouseX = e.getX();
                dragMouseY = e.getY();
                activeSlot = dragSlot;
                redraw();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (dragSlot >= 0 && dragSlot < editableSlots.size()) {
                double tx = clamp(e.getX() * scaleX, 0, template.getWidth());
                double ty = clamp(e.getY() * scaleY, 0, template.getHeight());
                setCorner(editableSlots.get(dragSlot), dragCorner, tx, ty);
                dragMouseX = e.getX();
                dragMouseY = e.getY();
                statusLabel.setText(String.format("Slot %d  %s → (%.0f, %.0f)",
                        dragSlot + 1, cornerName(dragCorner), tx, ty));
                redraw();
            }
        });

        canvas.setOnMouseReleased(e -> {
            dragSlot   = -1;
            dragCorner = -1;
            statusLabel.setText("");
            redraw();
        });

        canvas.setOnMouseMoved(e -> {
            int[] hit = findHandle(e.getX(), e.getY());
            canvas.setCursor(hit != null ? Cursor.HAND : Cursor.DEFAULT);
        });

        // Ctrl+Scroll → zoom toward cursor; plain scroll passes through to ScrollPane
        canvas.setOnScroll(e -> {
            if (e.isControlDown() || e.isMetaDown()) {
                if (e.getDeltaY() == 0) return;
                double factor = e.getDeltaY() > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP;
                zoomAt(e.getX(), e.getY(), zoomLevel * factor);
                e.consume(); // prevent ScrollPane from also reacting
            }
            // no consume → ScrollPane handles normal scroll for panning
        });
    }

    // ==================== DRAWING ====================

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (int i = 0; i < editableSlots.size(); i++) {
            drawSlot(gc, editableSlots.get(i), i, i == activeSlot);
        }

        if (dragSlot >= 0 && dragSlot < editableSlots.size()) {
            drawMagnifier(gc, dragMouseX, dragMouseY);
        }
    }

    private void drawSlot(GraphicsContext gc, SlotRegion slot, int idx, boolean active) {
        Color color = SLOT_COLORS[idx % SLOT_COLORS.length];
        double[][] pts = cornersInDisplay(slot);
        double[] xs = {pts[0][0], pts[1][0], pts[2][0], pts[3][0]};
        double[] ys = {pts[0][1], pts[1][1], pts[2][1], pts[3][1]};

        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                active ? 0.20 : 0.08));
        gc.fillPolygon(xs, ys, 4);

        gc.setStroke(color);
        gc.setLineWidth(active ? 2.5 : 1.5);
        gc.strokePolygon(xs, ys, 4);

        // Center label
        double cx = (xs[0] + xs[1] + xs[2] + xs[3]) / 4.0;
        double cy = (ys[0] + ys[1] + ys[2] + ys[3]) / 4.0;
        gc.setFont(Font.font("System", FontWeight.BOLD, active ? 13 : 10));
        gc.setFill(color);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(SLOT_NAMES[idx % SLOT_NAMES.length], cx, cy + 5);

        // Handles — scale handle radius with zoom so they stay easy to click
        double hr = Math.max(5, Math.min(HANDLE_RADIUS, HANDLE_RADIUS * zoomLevel));
        for (int c = 0; c < 4; c++) {
            boolean isDragging = (dragSlot == idx && dragCorner == c);
            double hx = pts[c][0];
            double hy = pts[c][1];

            gc.setFill(isDragging ? Color.WHITE : color);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            gc.fillOval(hx - hr, hy - hr, hr * 2, hr * 2);
            gc.strokeOval(hx - hr, hy - hr, hr * 2, hr * 2);

            gc.setFont(Font.font("System", FontWeight.BOLD, Math.max(7, 9 * zoomLevel)));
            gc.setFill(Color.BLACK);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.valueOf(c + 1), hx, hy + hr * 0.4);
        }
    }

    /**
     * Magnifier loupe centered on the cursor.
     * Samples the TEMPLATE image in image-pixel space (independent of zoom level).
     */
    private void drawMagnifier(GraphicsContext gc, double mouseX, double mouseY) {
        // Position loupe offset from cursor
        double magCX = mouseX + MAG_OFFSET_X;
        double magCY = mouseY + MAG_OFFSET_Y;
        magCX = clamp(magCX, MAG_RADIUS + 4, canvas.getWidth()  - MAG_RADIUS - 4);
        magCY = clamp(magCY, MAG_RADIUS + 4, canvas.getHeight() - MAG_RADIUS - 4);

        // Convert cursor → template image pixels (the coordinate space of templateFXImage)
        double imgCX = mouseX * scaleX;
        double imgCY = mouseY * scaleY;

        // Sample area in IMAGE pixels: (MAG_RADIUS*2 / MAG_ZOOM) display pixels → image pixels
        double imgSrcSize = (MAG_RADIUS * 2.0 / MAG_ZOOM) * scaleX;
        double imgSrcX = imgCX - imgSrcSize / 2.0;
        double imgSrcY = imgCY - imgSrcSize / 2.0;

        // Dark backing
        gc.save();
        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillOval(magCX - MAG_RADIUS - 3, magCY - MAG_RADIUS - 3,
                (MAG_RADIUS + 3) * 2, (MAG_RADIUS + 3) * 2);

        // Circular clip + zoomed image
        gc.beginPath();
        gc.arc(magCX, magCY, MAG_RADIUS, MAG_RADIUS, 0, 360);
        gc.closePath();
        gc.clip();

        gc.drawImage(templateFXImage,
                imgSrcX, imgSrcY, imgSrcSize, imgSrcSize,
                magCX - MAG_RADIUS, magCY - MAG_RADIUS, MAG_RADIUS * 2, MAG_RADIUS * 2);

        gc.restore();

        // White border ring
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2.0);
        gc.strokeOval(magCX - MAG_RADIUS, magCY - MAG_RADIUS, MAG_RADIUS * 2, MAG_RADIUS * 2);

        // Red crosshair
        gc.setStroke(Color.color(1, 0.2, 0.2, 0.9));
        gc.setLineWidth(1.0);
        gc.strokeLine(magCX - 10, magCY, magCX + 10, magCY);
        gc.strokeLine(magCX, magCY - 10, magCX, magCY + 10);

        // Coordinate label
        double tx = mouseX * scaleX;
        double ty = mouseY * scaleY;
        gc.setFont(Font.font("System", FontWeight.BOLD, 10));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.color(0, 0, 0, 0.75));
        gc.fillRoundRect(magCX - 38, magCY + MAG_RADIUS + 3, 76, 17, 4, 4);
        gc.setFill(Color.WHITE);
        gc.fillText(String.format("(%.0f, %.0f)", tx, ty), magCX, magCY + MAG_RADIUS + 14);
    }

    // ==================== HELPERS ====================

    private double[][] cornersInDisplay(SlotRegion s) {
        return new double[][]{
            {s.getTlX() / scaleX, s.getTlY() / scaleY},
            {s.getTrX() / scaleX, s.getTrY() / scaleY},
            {s.getBrX() / scaleX, s.getBrY() / scaleY},
            {s.getBlX() / scaleX, s.getBlY() / scaleY}
        };
    }

    private int[] findHandle(double mouseX, double mouseY) {
        double hr = Math.max(5, Math.min(HANDLE_RADIUS, HANDLE_RADIUS * zoomLevel)) + 4;
        for (int i = editableSlots.size() - 1; i >= 0; i--) {
            double[][] pts = cornersInDisplay(editableSlots.get(i));
            for (int c = 0; c < 4; c++) {
                double dx = pts[c][0] - mouseX;
                double dy = pts[c][1] - mouseY;
                if (dx * dx + dy * dy <= hr * hr) return new int[]{i, c};
            }
        }
        return null;
    }

    private void setCorner(SlotRegion slot, int corner, double tx, double ty) {
        switch (corner) {
            case 0 -> { slot.setTlX(tx); slot.setTlY(ty); }
            case 1 -> { slot.setTrX(tx); slot.setTrY(ty); }
            case 2 -> { slot.setBrX(tx); slot.setBrY(ty); }
            case 3 -> { slot.setBlX(tx); slot.setBlY(ty); }
        }
    }

    private String cornerName(int c) {
        return switch (c) { case 0 -> "TL"; case 1 -> "TR"; case 2 -> "BR"; default -> "BL"; };
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private SlotRegion defaultSlot(int idx, int templateW, int templateH) {
        int margin = (int)(templateW * 0.05);
        int bandH  = templateH / MAX_SLOTS;
        int top    = idx * bandH + (int)(bandH * 0.05);
        int bottom = top + (int)(bandH * 0.90);
        SlotRegion s = new SlotRegion(margin, top, templateW - margin * 2, bottom - top);
        s.initCornersFromRect();
        return s;
    }

    private SlotRegion copySlot(SlotRegion orig) {
        SlotRegion c = new SlotRegion(orig.getX(), orig.getY(), orig.getWidth(), orig.getHeight());
        c.setTlX(orig.getTlX()); c.setTlY(orig.getTlY());
        c.setTrX(orig.getTrX()); c.setTrY(orig.getTrY());
        c.setBrX(orig.getBrX()); c.setBrY(orig.getBrY());
        c.setBlX(orig.getBlX()); c.setBlY(orig.getBlY());
        return c;
    }

    private void refreshSlotButtons(HBox row) {
        for (int i = 0; i < MAX_SLOTS && i < row.getChildren().size(); i++) {
            if (row.getChildren().get(i) instanceof Button btn) {
                btn.setStyle(slotButtonStyle(i, i == activeSlot));
            }
        }
        redraw();
    }

    private String slotButtonStyle(int idx, boolean active) {
        Color c = SLOT_COLORS[idx % SLOT_COLORS.length];
        String hex = String.format("#%02x%02x%02x",
                (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
        return active
            ? "-fx-background-color:" + hex + "; -fx-text-fill:black; " +
              "-fx-font-weight:bold; -fx-background-radius:4; -fx-font-size:12px;"
            : "-fx-background-color:#2a2a2a; -fx-text-fill:" + hex + "; " +
              "-fx-background-radius:4; -fx-font-size:12px;";
    }

    // ==================== PUBLIC API ====================

    public boolean wasSaved() { return saved; }

    public List<SlotRegion> getEditedSlots() {
        return Collections.unmodifiableList(editableSlots);
    }
}
