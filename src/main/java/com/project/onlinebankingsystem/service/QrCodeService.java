package com.project.onlinebankingsystem.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {

    /**
     * Generates a Base64 PNG Data URL string for a given text payload.
     */
    public String generateQrCodeBase64(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            byte[] pngData = outputStream.toByteArray();

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR Code image: " + e.getMessage(), e);
        }
    }

    /**
     * Reads and decodes a QR code image from Base64 or byte array.
     */
    public String decodeQrCodeBase64(String base64Image) {
        try {
            String cleanBase64 = base64Image;
            if (base64Image.contains(",")) {
                cleanBase64 = base64Image.split(",")[1];
            }
            byte[] imageBytes = Base64.getDecoder().decode(cleanBase64.trim());
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (bufferedImage == null) {
                throw new IllegalArgumentException("Invalid image data provided for QR scanning.");
            }

            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to decode QR Code image. Ensure image contains a valid UPI QR code.");
        }
    }

    /**
     * Parses a standard simulated UPI URI e.g. upi://pay?pa=rahul@smartbank&pn=Rahul%20Sharma&am=500.00
     */
    public Map<String, String> parseUpiUri(String uri) {
        Map<String, String> params = new HashMap<>();
        if (uri == null || uri.isBlank()) {
            return params;
        }

        String target = uri.trim();
        if (target.startsWith("upi://pay?")) {
            target = target.substring("upi://pay?".length());
        } else if (target.contains("?")) {
            target = target.substring(target.indexOf("?") + 1);
        }

        String[] pairs = target.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = pair.substring(0, idx).trim();
                String value = pair.substring(idx + 1).trim();
                try {
                    value = URLDecoder.decode(value, StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
                params.put(key, value);
            }
        }
        return params;
    }
}
