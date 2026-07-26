# Stage 1 - Build (Debian-based, suporta linux/amd64 + linux/arm64)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY checkstyle.xml checkstyle-suppressions.xml spotbugs-exclude.xml ./
COPY src/ src/
RUN mvn clean package -DskipTests -B

# Stage 2 - Runtime (Debian-based, suporta linux/amd64 + linux/arm64)
FROM eclipse-temurin:17-jre-jammy
LABEL org.opencontainers.image.title="arremateai-recommendation"
LABEL org.opencontainers.image.description="Servico de recomendacao do ArremateAI (E30 / ADR-009)"
LABEL org.opencontainers.image.vendor="ArremateAI"
LABEL org.opencontainers.image.source="https://github.com/Quintanilha09/arremateai-recommendation"
LABEL org.opencontainers.image.licenses="MIT"
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd -r appgroup && useradd -r -g appgroup -u 1001 appuser
WORKDIR /app
COPY --from=build /app/target/arremateai-recommendation-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8089
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s CMD curl -f http://localhost:8089/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
