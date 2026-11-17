# Format·Forge — Image Converter

[![CI](https://github.com/piccaj/file-converter/actions/workflows/ci.yml/badge.svg)](https://github.com/piccaj/file-converter/actions/workflows/ci.yml)

A server-side converter web app built with Spring Boot. **JDK ImageIO does images, libheif and ffmpeg do the formats the JDK can't touch** — no oversized all-in-one libraries, no storage, files are deleted immediately after conversion.

**Live demo:** https://file-converter-xvz4.onrender.com

## Features

- **Web UI** at `/` — drag-and-drop (or click) upload, format picker, preview of the converted image, and a download button
- **Magic-byte format detection** — input format is determined by the file's actual bytes, never the filename or content-type header, which are trivially spoofed (a `.png` containing an EXE is rejected)
- **HEIC & AVIF decode** via native libheif — the formats the JDK can't read; decoded to PNG, then converted by the standard pipeline, so HEIC→JPG still composites transparency onto white
- **MP4/MOV → MP3** audio extraction via ffmpeg — video in, audio track out
- **Transparency handled correctly** — JPG has no alpha channel, so transparent areas are composited onto white instead of turning black
- **REST API** for scripting: `POST /api/convert` (multipart `file` + `target`), `GET /api/formats`
- **Temp files strictly scoped** — each conversion gets its own private temp dir, deleted in a `finally` block; args are passed as arrays (no shell string, no injection surface)
- **Hard limits** — 25MB upload cap, returning `413`; malformed input returns `415` with a readable message
- **CI** — builds and runs the full test suite on every push, with the native tools installed

## Supported formats

| | read | write |
|---|---|---|
| PNG | ✓ | ✓ |
| JPG | ✓ | ✓ |
| GIF | ✓ | ✓ |
| BMP | ✓ | ✓ |
| WEBP | ✓ (ffmpeg) | — |
| HEIC / HEIF | ✓ (libheif) | — |
| AVIF | ✓ (libheif) | — |
| MP4 / MOV | ✓ (ffmpeg) | MP3 only |

HEIC/AVIF/WEBP are decode-only: iPhone photos, AVIF images, and WebP images → PNG/JPG/GIF/BMP. MP4/MOV → MP3 (audio track extraction). Encoding HEIC/AVIF would need an encoder build of libheif; encoding video is a different project.

## Quick start

```bash
# run
mvn spring-boot:run

# then open http://localhost:8080
```

Or convert from the terminal:

```bash
curl -F "file=@photo.png" -F "target=jpg" http://localhost:8080/api/convert -o photo.jpg
```

## API

### `POST /api/convert`

Multipart form: `file` (the image or video) and `target` — one of `png` / `jpg` / `gif` / `bmp` / `mp3`.

Response: the converted file's bytes with `Content-Type: image/<target>` (or `audio/mpeg` for mp3) and `Content-Disposition: attachment` with a renamed filename.

| Status | Meaning |
|---|---|
| `200` | converted |
| `400` | no file / bad request |
| `413` | file exceeds 25MB |
| `415` | unrecognized input (magic-byte check failed), unknown target, or a required native tool is missing on the server |
| `422` | input recognized, but the decoder failed (corrupt/truncated file, no audio track, ...) |

### `GET /api/formats`

Returns the formats the server can read/write:

```json
[{"name":"PNG","extension":"png","supportsAlpha":true}, ...]
```

## How it works

```
upload ──► magic-byte sniff ──► ImageIO or native decode ──► flatten alpha ──► encode ──► stream back
 (multipart)   (bytes 4-12,      (ImageIO: png/jpg/gif/bmp    (JPG target:      (target
                no filenames     libheif: heic/avif→png       white backdrop)   format)
                trusted)         ffmpeg: video→mp3 audio)
```

Images are processed in memory where possible. The native paths (HEIC/AVIF/video) use a private temp directory per request — created, used, and deleted within a single method call, with subprocess arguments passed as arrays so filenames can never inject shell commands.

## Roadmap

- [ ] WEBP write support
- [ ] Batch conversion (multiple files at once)
- [ ] EXIF stripping option
- [ ] Optional background color for JPG flattening
- [ ] Animated GIF frame handling

## Deploying (Docker)

```bash
docker build -t formatforge .
docker run -p 8080:8080 formatforge
```

The Dockerfile is multi-stage: Maven build with cached dependencies, then a slim JRE-alpine runtime running as a non-root user. It listens on `${PORT:8080}`, so container hosts (Render, Fly.io, Railway) that inject `PORT` work out of the box.

## License

MIT
