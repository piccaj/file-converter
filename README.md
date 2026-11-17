# Format·Forge — Image Converter

[![CI](https://github.com/piccaj/file-converter/actions/workflows/ci.yml/badge.svg)](https://github.com/piccaj/file-converter/actions/workflows/ci.yml)

A server-side image converter web app built with Spring Boot and the JDK's ImageIO — **no external image libraries, no temp files, no storage**. Drop a JPG/PNG/GIF/BMP, pick a target format, download the result.

<!-- demo link goes here once deployed: **Live demo:** https://...onrender.com -->

## Features

- **Web UI** at `/` — drag-and-drop (or click) upload, format picker, preview of the converted image, and a download button
- **Magic-byte format detection** — input format is determined by the file's actual bytes, never the filename or content-type header, which are trivially spoofed (a `.png` containing an EXE is rejected)
- **Transparency handled correctly** — JPG has no alpha channel, so transparent areas are composited onto white instead of turning black
- **REST API** for scripting: `POST /api/convert` (multipart `file` + `target`), `GET /api/formats`
- **In-memory only** — files are converted and streamed back; nothing is written to disk or stored
- **Hard limits** — 10MB upload cap, returning `413`; malformed input returns `415` with a readable message
- **CI** — builds and runs the full test suite on every push

## Supported formats

| | read | write |
|---|---|---|
| PNG | ✓ | ✓ |
| JPG | ✓ | ✓ |
| GIF | ✓ | ✓ |
| BMP | ✓ | ✓ |

HEIC and AVIF are **not supported yet** — the JDK's ImageIO can't decode them. The architecture is ready for them (see Roadmap).

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

Multipart form: `file` (the image) and `target` (`png` | `jpg` | `gif` | `bmp`).

Response: the converted image bytes, `Content-Type: image/<target>`, `Content-Disposition: attachment` with a renamed filename.

| Status | Meaning |
|---|---|
| `200` | converted |
| `400` | no file / bad request |
| `413` | file exceeds 10MB |
| `415` | unrecognized input format (magic-byte check failed) or unknown target |

### `GET /api/formats`

Returns the formats the server can read/write:

```json
[{"name":"PNG","extension":"png","supportsAlpha":true}, ...]
```

## How it works

```
upload ──► magic-byte sniff ──► ImageIO.read ──► alpha flatten ──► ImageIO.write ──► stream back
 (multipart)   (4-12 bytes,     (decode into      (only for JPG       (encode to target)
                no filenames     memory)           target)
                trusted)
```

Everything happens in memory — `byte[]` in, `byte[]` out — so there are no temp files to leak and no cleanup jobs to write.

## Roadmap

- [ ] HEIC / AVIF support via [libheif](https://github.com/strukturag/libheif) — the formats people actually want converted
- [ ] WebP read/write
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
