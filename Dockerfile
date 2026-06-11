# Build stage: compile and package the application
FROM eclipse-temurin:8-jdk AS builder

ARG VER
WORKDIR /workspace

# Copy Gradle wrapper and build files first (for better caching)
COPY gradle/ gradle/
COPY gradlew gradlew.bat settings.gradle build.gradle ./

# Download dependencies (cached if build.gradle doesn't change)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src/ src/
COPY bin/ bin/

# Build distribution with specified version
RUN ./gradlew distTar -Pversion=${VER} --no-daemon && \
    ls -lh build/distributions/

# Runtime stage: minimal JRE image with application
FROM eclipse-temurin:8-jre

ARG VER
LABEL name="wsl-socks" version="${VER}"

# Copy the distribution from builder stage
COPY --from=builder /workspace/build/distributions/wsl-socks-${VER}.tar /usr/local/
RUN ln -nfs /usr/local/wsl-socks-${VER} /usr/local/wsl-socks

ENTRYPOINT ["/usr/local/wsl-socks/bin/wsl-socks"]
