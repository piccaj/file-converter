package com.example.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConversionApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void convertsUploadedPngToJpg() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", pngResource("photo.png", 12, 9));
        body.add("target", "jpg");

        ResponseEntity<byte[]> response = restTemplate.postForEntity("/api/convert",
                new HttpEntity<>(body, headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("photo.jpg");
        // response body is a real JPG
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()[0]).isEqualTo((byte) 0xFF);
        assertThat(response.getBody()[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void rejectsTextFileWith415() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("not an image at all".getBytes()) {
            @Override
            public String getFilename() {
                return "evil.png";
            }
        });
        body.add("target", "jpg");

        ResponseEntity<String> response = restTemplate.postForEntity("/api/convert",
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).contains("magic bytes");
    }

    @Test
    void formatsEndpointListsAllFour() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/formats", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("PNG", "JPG", "GIF", "BMP", "HEIC", "MP4", "mp3");
    }

    @Test
    void convertsMp4ToMp3WhenFfmpegPresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new com.example.converter.service.NativeConverterService().ffmpegAvailable(),
                "ffmpeg not available — skipping mp3 extraction test");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fixtureResource("sample.mp4", "clip.mp4"));
        body.add("target", "mp3");

        ResponseEntity<byte[]> response = restTemplate.postForEntity("/api/convert",
                new HttpEntity<>(body, headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("clip.mp3");
        byte[] out = response.getBody();
        assertThat(out).isNotNull();
        assertThat(out.length).isGreaterThan(1000);
        // ID3 tag ("ID3") or a raw MPEG frame sync (0xFF Ex) both prove audio
        boolean id3 = out[0] == 'I' && out[1] == 'D' && out[2] == '3';
        boolean frame = (out[0] & 0xFF) == 0xFF && (out[1] & 0xE0) == 0xE0;
        assertThat(id3 || frame).isTrue();
    }

    @Test
    void convertsWebpToPngWhenFfmpegPresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new com.example.converter.service.NativeConverterService().ffmpegAvailable(),
                "ffmpeg not available — skipping webp test");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fixtureResource("sample.webp", "photo.webp"));
        body.add("target", "png");

        ResponseEntity<byte[]> response = restTemplate.postForEntity("/api/convert",
                new HttpEntity<>(body, headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("photo.png");
        byte[] out = response.getBody();
        assertThat(out).isNotNull();
        assertThat(out[0]).isEqualTo((byte) 0x89); // PNG magic
    }

    @Test
    void convertsHeicToPngWhenHeifToolsPresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new com.example.converter.service.NativeConverterService().heifAvailable(),
                "heif-convert not available — skipping heic test");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fixtureResource("sample.heic", "photo.heic"));
        body.add("target", "png");

        ResponseEntity<byte[]> response = restTemplate.postForEntity("/api/convert",
                new HttpEntity<>(body, headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("photo.png");
        byte[] out = response.getBody();
        assertThat(out).isNotNull();
        assertThat(out[0]).isEqualTo((byte) 0x89); // PNG magic
    }

    @Test
    void convertsMp4ToGifWhenFfmpegPresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new com.example.converter.service.NativeConverterService().ffmpegAvailable(),
                "ffmpeg not available — skipping video→gif test");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fixtureResource("sample.mp4", "clip.mp4"));
        body.add("target", "gif");

        ResponseEntity<byte[]> response = restTemplate.postForEntity("/api/convert",
                new HttpEntity<>(body, headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("clip.gif");
        byte[] out = response.getBody();
        assertThat(out).isNotNull();
        assertThat(new String(out, 0, 6, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("GIF89a");
    }

    @Test
    void rejectsMp4TargetedAtImageFormatWith415() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fixtureResource("sample.mp4", "clip.mp4"));
        body.add("target", "png");

        ResponseEntity<String> response = restTemplate.postForEntity("/api/convert",
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).contains("gif or mp3");
    }

    @Test
    void homepageServesUi() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("drop");
    }

    private org.springframework.core.io.ByteArrayResource fixtureResource(String fixture, String filename) {
        try (var in = getClass().getResourceAsStream("/fixtures/" + fixture)) {
            byte[] data = in.readAllBytes();
            return new org.springframework.core.io.ByteArrayResource(data) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ByteArrayResource pngResource(String filename, int w, int h) {
        try {
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = image.createGraphics();
            g.setColor(java.awt.Color.BLUE);
            g.fillRect(0, 0, w, h);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return new ByteArrayResource(out.toByteArray()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
