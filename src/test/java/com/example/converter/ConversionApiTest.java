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
        assertThat(response.getBody()).contains("PNG", "JPG", "GIF", "BMP");
    }

    @Test
    void homepageServesUi() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("drop");
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
