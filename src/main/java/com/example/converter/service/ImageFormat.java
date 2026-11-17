package com.example.converter.service;

import java.util.Locale;

/**
 * Supported input/output formats, with the ImageIO format name used for
 * encoding and whether the encoder preserves alpha transparency.
 */
public enum ImageFormat {

    PNG("png", true),
    JPG("jpg", false),
    GIF("gif", true),
    BMP("bmp", true);

    private final String extension;
    private final boolean supportsAlpha;

    ImageFormat(String extension, boolean supportsAlpha) {
        this.extension = extension;
        this.supportsAlpha = supportsAlpha;
    }

    public String extension() {
        return extension;
    }

    public boolean supportsAlpha() {
        return supportsAlpha;
    }

    public static boolean isValidTarget(String name) {
        return fromName(name) != null;
    }

    public static ImageFormat fromName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ImageFormat.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Detects the format from magic bytes — never trust filenames or
     * content-type headers, which are trivially spoofed.
     */
    public static ImageFormat detect(byte[] header) {
        if (startsWith(header, 0x89, 'P', 'N', 'G')) {
            return PNG;
        }
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
            return JPG;
        }
        if (startsWith(header, 'G', 'I', 'F', '8')) {
            return GIF;
        }
        if (startsWith(header, 'B', 'M')) {
            return BMP;
        }
        return null;
    }

    private static boolean startsWith(byte[] h, int... bytes) {
        if (h.length < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if ((h[i] & 0xFF) != bytes[i]) {
                return false;
            }
        }
        return true;
    }
}
