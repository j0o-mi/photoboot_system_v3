package com.boothyeah.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.PrintServiceLookup;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;

/**
 * Sends images to the system printer using the Java Print Service API.
 * Scales the image to fit the printable area while maintaining aspect ratio.
 *
 * The admin dashboard can toggle printing on/off and select a specific printer.
 */
public class PhotoPrintService {
    private static final Logger log = LoggerFactory.getLogger(PhotoPrintService.class);

    /**
     * Print a BufferedImage to the specified printer (or system default).
     *
     * @param image       the image to print
     * @param printerName specific printer name, or empty/null for system default
     * @return true if print job was submitted successfully
     */
    public boolean printImage(BufferedImage image, String printerName) {
        try {
            PrinterJob job = PrinterJob.getPrinterJob();

            // Select specific printer if requested
            if (printerName != null && !printerName.isEmpty()) {
                javax.print.PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
                for (javax.print.PrintService svc : services) {
                    if (svc.getName().equalsIgnoreCase(printerName)) {
                        job.setPrintService(svc);
                        break;
                    }
                }
            }

            // Set the Printable that renders our image
            job.setPrintable(new ImagePrintable(image));

            // Submit the print job (no dialog in kiosk mode)
            job.print();
            log.info("Print job submitted to: {}", job.getPrintService().getName());
            return true;

        } catch (PrinterException e) {
            log.error("Printing failed", e);
            return false;
        }
    }

    /** Get list of available printer names (for admin settings dropdown) */
    public String[] getAvailablePrinters() {
        javax.print.PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        String[] names = new String[services.length];
        for (int i = 0; i < services.length; i++) {
            names[i] = services[i].getName();
        }
        return names;
    }

    /**
     * Inner class implementing Printable to render a BufferedImage on a print page.
     * Scales the image to fill the printable area while maintaining aspect ratio.
     */
    private static class ImagePrintable implements Printable {
        private final BufferedImage image;

        ImagePrintable(BufferedImage image) {
            this.image = image;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex)
                throws PrinterException {
            if (pageIndex > 0) return NO_SUCH_PAGE; // Single page only

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Calculate scaling to fit printable area while maintaining aspect ratio
            double pageW = pageFormat.getImageableWidth();
            double pageH = pageFormat.getImageableHeight();
            double scaleX = pageW / image.getWidth();
            double scaleY = pageH / image.getHeight();
            double scale = Math.min(scaleX, scaleY); // Fit within bounds

            // Center the image on the page
            double scaledW = image.getWidth() * scale;
            double scaledH = image.getHeight() * scale;
            double offsetX = pageFormat.getImageableX() + (pageW - scaledW) / 2;
            double offsetY = pageFormat.getImageableY() + (pageH - scaledH) / 2;

            g2d.translate(offsetX, offsetY);
            g2d.scale(scale, scale);
            g2d.drawImage(image, 0, 0, null);

            return PAGE_EXISTS;
        }
    }
}
