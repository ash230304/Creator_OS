# ══════════════════════════════════════════════════════
# CreatorOS Backend — Dockerfile
# Multi-stage build: keeps the final image small
# ══════════════════════════════════════════════════════

# ── Stage 1: Build ────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Cache Maven dependencies first (layer caching)
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Install FFmpeg — required for audio extraction and clip generation
RUN apk add --no-cache ffmpeg

# Create non-root user
RUN addgroup -S creatoros && adduser -S creatoros -G creatoros

# Copy built jar
COPY --from=builder /build/target/*.jar app.jar

# Create uploads directory with correct ownership
RUN mkdir -p /app/uploads/videos /app/uploads/clips /app/uploads/captioned \
    && chown -R creatoros:creatoros /app

USER creatoros

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
