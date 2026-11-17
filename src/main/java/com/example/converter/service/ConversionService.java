package com.example.converter.service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.springframework.stereotype.Service;

/**
 * Converts images between the formats the JDK's ImageIO supports out of the
 * box: PNG, JPG, GIF and BMP (any of them in, any of them out).
 *
 * Two gotchas handled here:
 * 1. JPG has no alpha channel — converting a transparent PNG/GIF to JPG
 *    without compositing onto a background makes transparent pixels go black.
 *    We composite onto white, which is what users expect.
 * 2. Everything runs against plain ImageIO, so when HEIC/AVIF/WebP arrive via
 *    an ImageIO plugin or ImageMagick later, this class barely changes.
 */
@Service
public class ConversionService {

    public record Conversion(byte[] data, ImageFormat sourceFormat, ImageFormat targetFormat) {
    }

    public Conversion convert(byte[] input, String targetName) throws IOException, UnsupportedConversionException {
        ImageFormat target = ImageFormat.fromName(targetName);
        if (target == null) {
            throw new UnsupportedConversionException("unknown target format: " + targetName);
        }

        ImageFormat source = ImageFormat.detect(take(input, 16));
        if (source == null) {
            throw new UnsupportedConversionException("unrecognized input format — magic bytes don't match any supported type");
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
        if (image == null) {
            throw new IOException("ImageIO could not decode the file (corrupt or truncated?)");
        }

        BufferedImage encoded = target.supportsAlpha() ? image : compositeOnWhite(image);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(encoded, target.extension(), out)) {
            throw new IOException("no ImageIO writer registered for " + target.extension());
        }

        return new Conversion(out.toByteArray(), source, target);
    }

    /**
     * JPG (and any other alpha-less format) needs transparent pixels filled
     * with something; black is the ugly default, white is what users expect.
     */
    private BufferedImage compositeOnWhite(BufferedImage source) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return result;
    }

    private static byte[] take(byte[] data, int n) {
        return Arrays.copyOf(data, Math.min(n, data.length));
    }
}
