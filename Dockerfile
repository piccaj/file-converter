# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# cache dependencies separately so code changes rebuild fast
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# ---- run stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# ffmpeg: MP4→MP3 audio extraction. libheif-tools: heif-convert for HEIC/AVIF
# decode (the plain "libheif" apk is just the library, no CLI).
RUN apk add --no-cache ffmpeg libheif-tools

RUN addgroup -S app && adduser -S app -G app
USER app

# no temp files are ever written; conversion happens fully in memory
COPY --from=build /app/target/file-converter-0.1.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
