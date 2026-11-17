package com.example.converter.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.converter.service.ConversionService;
import com.example.converter.service.NativeConverterService;
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
    private final NativeConverterService nativeTools;

    public ConversionController(ConversionService service, NativeConverterService nativeTools) {
        this.service = service;
        this.nativeTools = nativeTools;
    }

    public record FormatInfo(String name, String extension, boolean supportsAlpha) {
        static FormatInfo of(ImageFormat f) {
            return new FormatInfo(f.name(), f.extension(), f.supportsAlpha());
        }
    }

    public record InputInfo(String name, String extension, boolean nativeTool) {
        static InputInfo of(ImageFormat f) {
            return new InputInfo(f.name(), f.extension(), f.isNativeOnly() || f == ImageFormat.MP4);
        }
    }

    public record TargetInfo(String name, String extension, String kind) {
    }

    @GetMapping("/formats")
    public Map<String, Object> formats() {
        List<InputInfo> inputs = Arrays.stream(ImageFormat.values())
                .map(InputInfo::of)
                .toList();
        List<TargetInfo> targets = Arrays.stream(ImageFormat.values())
                .filter(ImageFormat::isImageTarget)
                .map(f -> new TargetInfo(f.name(), f.extension(), "image"))
                .collect(Collectors.toList());
        targets.add(new TargetInfo("MP3", "mp3", "audio"));
        return Map.of("inputs", inputs, "targets", targets);
    }

    @PostMapping("/convert")
    public ResponseEntity<byte[]> convert(@RequestParam("file") MultipartFile file,
            @RequestParam("target") String target)
            throws IOException, UnsupportedConversionException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"no file uploaded\"}".getBytes());
        }

        // mp3 target: extract the audio track of any video/audio file ffmpeg can read
        if (ImageFormat.isMp3Target(target)) {
            byte[] mp3 = nativeTools.convertToMp3(file.getBytes(),
                    extensionOf(file.getOriginalFilename()));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .header("Content-Disposition",
                            "attachment; filename=\"" + rename(file.getOriginalFilename(), "mp3") + "\"")
                    .body(mp3);
        }

        ConversionService.Conversion result = service.convert(file.getBytes(), target);

        String downloadName = rename(file.getOriginalFilename(), result.targetFormat());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/" + result.targetFormat().extension()))
                .header("Content-Disposition", "attachment; filename=\"" + downloadName + "\"")
                .body(result.data());
    }

    private String rename(String original, ImageFormat target) {
        return rename(original, target.extension());
    }

    private String rename(String original, String extension) {
        String base = original != null ? original : "converted";
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        // strip anything that could break out of the filename
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return base + "." + extension;
    }

    /** Lowercased extension of the upload's filename, or "bin" if none. */
    private static String extensionOf(String filename) {
        if (filename == null) {
            return "bin";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "bin";
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
