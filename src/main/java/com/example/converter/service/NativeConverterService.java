package com.example.converter.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Shells out to native binaries for formats the JDK can't touch:
 * ffmpeg (video -> mp3) and ImageMagick (HEIC/AVIF -> png/jpg).
 *
 * Rules: args are passed as an array (never a shell string, so filenames
 * can't inject commands), every request gets its own temp directory which is
 * deleted in a finally block, and processes are hard-killed after a timeout.
 */
@Service
public class NativeConverterService {

    private static final Logger log = LoggerFactory.getLogger(NativeConverterService.class);
    private static final long PROCESS_TIMEOUT_SECONDS = 60;

    private final String ffmpeg;
    private final String heifConvert;

    public NativeConverterService() {
        this(System.getenv());
    }

    /** Test constructor: forces specific tool locations (e.g. absent tools). */
    NativeConverterService(java.util.Map<String, String> env) {
        this.ffmpeg = resolveBinary("ffmpeg", "FFMPEG_BIN", env);
        this.heifConvert = resolveBinary("heif-convert", "HEIF_CONVERT_BIN", env);
        log.info("native tools: ffmpeg={}, heif-convert={}", ffmpeg, heifConvert);
    }

    public boolean ffmpegAvailable() {
        return ffmpeg != null;
    }

    public boolean heifAvailable() {
        return heifConvert != null;
    }

    /** Extracts the audio track as MP3: ffmpeg -i in -vn -c:a libmp3lame. */
    public byte[] convertToMp3(byte[] input, String inputExtension) throws IOException, UnsupportedConversionException {
        if (ffmpeg == null) {
            throw new UnsupportedConversionException("ffmpeg is not installed on this server");
        }
        return withTempDir(input, inputExtension, (in) -> {
            // output goes beside the input: ffmpeg treats "X/output.mp3" with an
            // existing X dir as a target dir and refuses to pick a muxer
            Path mp3 = in.getParent().resolve("output.mp3");
            run(List.of(ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
                    "-i", in.toString(), "-vn", "-c:a", "libmp3lame", "-q:a", "4",
                    mp3.toString()), "ffmpeg");
            return Files.readAllBytes(mp3);
        });
    }

    /** Decodes HEIC/AVIF to PNG via libheif's heif-convert. The caller feeds
     *  the PNG back through the standard ImageIO pipeline, so every image
     *  target (JPG flatten, GIF, BMP) keeps working with no extra code.
     *  ImageMagick's Debian build lacks HEIC delegates; libheif handles both
     *  HEIC and AVIF natively. */
    public byte[] decodeToPng(byte[] input, String inputExtension)
            throws IOException, UnsupportedConversionException {
        if (heifConvert == null) {
            throw new UnsupportedConversionException("heif-convert (libheif) is not installed on this server");
        }
        return withTempDir(input, inputExtension, (in) -> {
            // write beside the input (see convertToMp3 for the ffmpeg path-trap note)
            Path png = in.getParent().resolve("decoded.png");
            run(List.of(heifConvert, in.toString(), png.toString()), "heif-convert");
            return Files.readAllBytes(png);
        });
    }

    /* ── plumbing ─────────────────────────────────────────── */

    private interface TempJob {
        byte[] run(Path inDir) throws IOException, UnsupportedConversionException;
    }

    private byte[] withTempDir(byte[] input, String inputExtension, TempJob job)
            throws IOException, UnsupportedConversionException {
        Path work = Files.createTempDirectory("forge-");
        try {
            Path in = work.resolve("input." + inputExtension);
            Files.write(in, input);
            return job.run(in);
        } finally {
            deleteRecursively(work);
        }
    }

    private void run(List<String> command, String tool) throws IOException, UnsupportedConversionException {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new UnsupportedConversionException(tool + " failed to start: " + e.getMessage());
        }
        // read output before waiting: ffmpeg/heif-convert write little, but
        // an unread full pipe buffer would deadlock the process
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        boolean finished;
        try {
            finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException(tool + " was interrupted");
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException(tool + " timed out after " + PROCESS_TIMEOUT_SECONDS + "s");
        }
        if (process.exitValue() != 0) {
            log.warn("{} exited with {}: {}", tool, process.exitValue(), output);
            throw new UnsupportedConversionException(
                    tool + " could not process this file (exit " + process.exitValue() + ")");
        }
    }

    private static void deleteRecursively(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best effort; temp dir lives under /tmp either way
                }
            });
        } catch (IOException ignored) {
        }
    }

    /** Env override (FFMPEG_BIN/HEIF_CONVERT_BIN) wins; tests use it to point
     *  at a pip-installed ffmpeg or to force a tool-absent state. */
    private static String resolveBinary(String name, String envOverride, java.util.Map<String, String> env) {
        String override = env.get(envOverride);
        if (override != null && !override.isBlank()) {
            return override;
        }
        return onPath(name) ? name : null;
    }

    private static boolean onPath(String binary) {
        for (String dir : System.getenv("PATH").split(":")) {
            if (Files.isExecutable(Path.of(dir, binary))) {
                return true;
            }
        }
        return false;
    }
}
