# Build context is the repo root (docker-compose.yml: context ..), so later tasks can read ../contracts.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY backend/gradlew backend/settings.gradle backend/build.gradle backend/gradle.properties backend/
COPY backend/gradle backend/gradle
WORKDIR /src/backend
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY backend/src src
COPY contracts /src/contracts
RUN ./gradlew --no-daemon bootJar -x test && cp "$(ls build/libs/*.jar | grep -v -- -plain | head -1)" /app.jar

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app.jar /app/app.jar
# Like V1: an image that is run without configuration starts as local and cannot reach production
ENV SPRING_PROFILES_ACTIVE=local
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS localhost:8080/api/v2/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
