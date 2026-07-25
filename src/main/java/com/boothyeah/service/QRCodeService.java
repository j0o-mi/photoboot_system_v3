package com.boothyeah.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates QR code images from URLs using ZXing (Zebra Crossing).
 * The QR code encodes the Google Drive sharing link so event attendees
 * can scan and download their photos on their phones.
 */
public class QRCodeService {
    private static final Logger log = LoggerFactory.getLogger(QRCodeService.class);

    /**
     * Generate a QR code image from the given URL/text.
     *
     * @param content the URL or text to encode
     * @param size    width and height in pixels (QR codes are square)
     * @return BufferedImage of the QR code, or null on failure
     */
    public BufferedImage generateQR(String content, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();

            // Encoding hints for better quality
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 2); // Quiet zone margin

            // Encode the content into a BitMatrix
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            // Convert BitMatrix to BufferedImage
            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(matrix);
            log.info("QR code generated for: {}... ({}x{}px)",
                    content.substring(0, Math.min(50, content.length())), size, size);
            return qrImage;

        } catch (WriterException e) {
            log.error("Failed to generate QR code", e);
            return null;
        }
    }
}
