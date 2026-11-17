package com.example.converter.controller;

import java.io.IOException;
import java.util.List;

import com.example.converter.service.ConversionService;
import com.example.converter.service.ImageFormat;
import com.example.converter.service.UnsupportedConversionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ConversionController {

    private final ConversionService service;

    public ConversionController(ConversionService service) {
        this.service = service;
    }

    public record FormatInfo(String name, String extension, boolean supportsAlpha) {
        static FormatInfo of(ImageFormat f) {
            return new FormatInfo(f.name(), f.extension(), f.supportsAlpha());
        }
    }

    @GetMapping("/formats")
    public List<FormatInfo> formats() {
        return List.of(ImageFormat.values()).stream().map(FormatInfo::of).toList();
    }

    @PostMapping("/convert")
    public ResponseEntity<byte[]> convert(@RequestParam("file") MultipartFile file,
            @RequestParam("target") String target)
            throws IOException, UnsupportedConversionException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"no file uploaded\"}".getBytes());
        }

        ConversionService.Conversion result = service.convert(file.getBytes(), target);

        String downloadName = rename(file.getOriginalFilename(), result.targetFormat());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/" + result.targetFormat().extension()))
                .header("Content-Disposition", "attachment; filename=\"" + downloadName + "\"")
                .body(result.data());
    }

    private String rename(String original, ImageFormat target) {
        String base = original != null ? original : "converted";
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        // strip anything that could break out of the filename
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return base + "." + target.extension();
    }

    @ExceptionHandler(UnsupportedConversionException.class)
    public ResponseEntity<String> unsupported(UnsupportedConversionException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"file exceeds the size limit\"}");
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> ioFailure(IOException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"could not decode the file\"}");
    }
}
