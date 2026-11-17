package com.example.converter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ConversionServiceTest {

    // no ffmpeg/ImageMagick on the test PATH: exercises the clear-error paths
    private final ConversionService service =
            new ConversionService(new NativeConverterService());

    @Test
    void convertsPngToJpgAndFlattensTransparency() throws Exception {
        byte[] png = render(10, 10, true);

        ConversionService.Conversion result = service.convert(png, "jpg");

        assertThat(result.sourceFormat()).isEqualTo(ImageFormat.PNG);
        assertThat(result.targetFormat()).isEqualTo(ImageFormat.JPG);
        assertThat(ImageFormat.detect(result.data())).isEqualTo(ImageFormat.JPG);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.data()));
        // transparent corner must become ~white after compositing, not black.
        // JPG is lossy, so allow per-channel tolerance rather than exact 255.
        int rgb = decoded.getRGB(0, 0);
        int r = (rgb >> 16) & 0xFF, g2 = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        assertThat(r).isGreaterThan(240);
        assertThat(g2).isGreaterThan(240);
        assertThat(b).isGreaterThan(240);
    }

    @Test
    void convertsJpgToPng() throws Exception {
        byte[] jpg = render(8, 6, false);

        ConversionService.Conversion result = service.convert(jpg, "png");

        assertThat(result.sourceFormat()).isEqualTo(ImageFormat.JPG);
        assertThat(ImageFormat.detect(result.data())).isEqualTo(ImageFormat.PNG);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.data()));
        assertThat(decoded.getWidth()).isEqualTo(8);
        assertThat(decoded.getHeight()).isEqualTo(6);
    }

    @Test
    void convertsBmpToPngAndPngToBmp() throws Exception {
        byte[] bmp = renderBytes("bmp", new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB));

        ConversionService.Conversion toPng = service.convert(bmp, "png");
        assertThat(toPng.sourceFormat()).isEqualTo(ImageFormat.BMP);

        ConversionService.Conversion backToBmp = service.convert(toPng.data(), "bmp");
        assertThat(backToBmp.sourceFormat()).isEqualTo(ImageFormat.PNG);
        assertThat(backToBmp.targetFormat()).isEqualTo(ImageFormat.BMP);
    }

    @Test
    void convertsGifToPng() throws Exception {
        byte[] gif = renderBytes("gif", new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));

        ConversionService.Conversion result = service.convert(gif, "png");
        assertThat(result.sourceFormat()).isEqualTo(ImageFormat.GIF);
        assertThat(ImageFormat.detect(result.data())).isEqualTo(ImageFormat.PNG);
    }

    @Test
    void rejectsUnknownTarget() throws Exception {
        byte[] png = renderBytes("png", new BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB));
        assertThatThrownBy(() -> service.convert(png, "heic"))
                .isInstanceOf(UnsupportedConversionException.class); // heic is input-only now
    }

    @Test
    void rejectsMp4AsTarget() throws Exception {
        byte[] png = renderBytes("png", new BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB));
        assertThatThrownBy(() -> service.convert(png, "mp4"))
                .isInstanceOf(UnsupportedConversionException.class);
    }

    @Test
    void rejectsUnrecognizedInputByMagicBytes() {
        byte[] notAnImage = "definitely not an image".repeat(2).getBytes();
        assertThatThrownBy(() -> service.convert(notAnImage, "png"))
                .isInstanceOf(UnsupportedConversionException.class)
                .hasMessageContaining("magic bytes");
    }

    @Test
    void rejectsRarFileDisguisedAsPng() throws Exception {
        // RAR magic (52 61 72 21) prepended to a real PNG — filename lies,
        // magic bytes don't
        byte[] png = renderBytes("png", new BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB));
        byte[] fake = new byte[png.length + 4];
        fake[0] = 'R'; fake[1] = 'a'; fake[2] = 'r'; fake[3] = '!';
        System.arraycopy(png, 0, fake, 4, png.length);

        assertThatThrownBy(() -> service.convert(fake, "jpg"))
                .isInstanceOf(UnsupportedConversionException.class);
    }

    @Test
    void detectsHeicAndMp4ByMagicBytes() throws Exception {
        byte[] heic = fixture("sample.heic");
        byte[] mp4 = fixture("sample.mp4");
        byte[] webp = fixture("sample.webp");

        assertThat(ImageFormat.detect(heic)).isEqualTo(ImageFormat.HEIC);
        assertThat(ImageFormat.detect(mp4)).isEqualTo(ImageFormat.MP4);
        assertThat(ImageFormat.detect(webp)).isEqualTo(ImageFormat.WEBP);
    }

    @Test
    void rejectsMp4TargetedAtImageFormat() throws Exception {
        byte[] mp4 = fixture("sample.mp4");
        // mp4 input routed at an image target is rejected before any subprocess runs
        assertThatThrownBy(() -> service.convert(mp4, "png"))
                .isInstanceOf(UnsupportedConversionException.class)
                .hasMessageContaining("gif or mp3");
    }

    @Test
    void detectsQuickTimeMovBrandAsVideo() {
        // ISO-BMFF container with a "qt  " major brand is a .mov file
        byte[] mov = new byte[16];
        mov[3] = 0x14;
        System.arraycopy("ftypqt  ".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, mov, 4, 8);

        assertThat(ImageFormat.detect(mov)).isEqualTo(ImageFormat.MP4);
    }

    @Test
    void convertsMp4ToGifWhenFfmpegPresent() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new NativeConverterService().ffmpegAvailable(),
                "ffmpeg not available — skipping video→gif test");

        byte[] mp4 = fixture("sample.mp4");

        ConversionService.Conversion result = service.convert(mp4, "gif");

        assertThat(result.sourceFormat()).isEqualTo(ImageFormat.MP4);
        assertThat(result.targetFormat()).isEqualTo(ImageFormat.GIF);
        assertThat(ImageFormat.detect(result.data())).isEqualTo(ImageFormat.GIF);
        // the encoded bytes are a real, non-trivial animated GIF
        assertThat(new String(result.data(), 0, 6, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("GIF89a");
        assertThat(result.data().length).isGreaterThan(1000);
    }

    @Test
    void videoToGifWithoutFfmpegFailsWithClearError() throws Exception {
        byte[] mp4 = fixture("sample.mp4");
        NativeConverterService bare = new NativeConverterService(
                java.util.Map.of("FFMPEG_BIN", "/nonexistent/ffmpeg",
                        "HEIF_CONVERT_BIN", "/nonexistent/heif-convert"));
        ConversionService forcedAbsent = new ConversionService(bare);

        assertThatThrownBy(() -> forcedAbsent.convert(mp4, "gif"))
                .isInstanceOf(UnsupportedConversionException.class)
                .hasMessageContaining("ffmpeg");
    }

    @Test
    void heicInputWithoutHeifToolsFailsWithClearError() throws Exception {
        byte[] heic = fixture("sample.heic");
        // force the "tool missing" state: env override points at a nonexistent
        // binary, so this passes wherever heif-convert actually is installed
        NativeConverterService bare = new NativeConverterService(
                java.util.Map.of("HEIF_CONVERT_BIN", "/nonexistent/heif-convert",
                        "FFMPEG_BIN", "/nonexistent/ffmpeg"));
        ConversionService forcedAbsent = new ConversionService(bare);

        // detected as HEIC by magic bytes, then routed to the native tool,
        // which is absent — must surface a readable message, not a 500
        assertThatThrownBy(() -> forcedAbsent.convert(heic, "png"))
                .isInstanceOf(UnsupportedConversionException.class)
                .hasMessageContaining("heif-convert");
    }

    /* helpers: build real encoded images in memory, no binary fixtures */

    private byte[] fixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return in.readAllBytes();
        }
    }

    private byte[] renderBytes(String format, BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    /** Encodes a red w×h image; when alpha is on, the left half is transparent. */
    private byte[] render(int w, int h, boolean alpha) throws Exception {
        BufferedImage image = new BufferedImage(w, h,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            g.setColor(java.awt.Color.RED);
            g.fillRect(0, 0, w, h);
            if (alpha) {
                g.setComposite(java.awt.AlphaComposite.Clear);
                g.fillRect(0, 0, w / 2, h);
            }
        } finally {
            g.dispose();
        }
        return renderBytes(alpha ? "png" : "jpg", image);
    }
}
