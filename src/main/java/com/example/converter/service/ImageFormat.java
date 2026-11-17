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
    BMP("bmp", true),
    HEIC("heic", true),
    AVIF("avif", true),
    MP4("mp4", false); // input-only: audio-extraction source, never an image target

    /** Formats only decodable by the native toolchain (ImageMagick). */
    public boolean isNativeOnly() {
        return this == HEIC || this == AVIF;
    }

    /** Formats the UI/API accept as a conversion target. MP4 is video (mp3-only
     *  pipeline); HEIC/AVIF are decode-only — nothing here can encode them. */
    public boolean isImageTarget() {
        return this == PNG || this == JPG || this == GIF || this == BMP;
    }

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
        if (name == null || !name.equals(name.toLowerCase(Locale.ROOT))) {
            return null; // API contract is lowercase extensions; "PNG"/"MP3" are not valid targets
        }
        try {
            return ImageFormat.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** MP3 is a separate pipeline (audio extraction), not an image target. */
    public static boolean isMp3Target(String name) {
        return "mp3".equals(name);
    }

    /**
     * Detects the format from magic bytes — never trust filenames or
     * content-type headers, which are trivially spoofed.
     *
     * HEIC/AVIF and MP4-family files are ISO-BMFF containers: they share the
     * "ftyp" box at offset 4, so the brand string at offset 8 decides.
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
        if (header.length >= 12 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            String brand = new String(header, 8, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (brand.startsWith("heic") || brand.startsWith("mif1") || brand.startsWith("msf1")) {
                return HEIC;
            }
            if (brand.startsWith("avif") || brand.startsWith("avis")) {
                return AVIF;
            }
            if (brand.startsWith("isom") || brand.startsWith("iso2") || brand.startsWith("mp4")
                    || brand.startsWith("M4V") || brand.startsWith("avc1")) {
                return MP4;
            }
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
