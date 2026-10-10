# ---- Stage 1: build the jar ----
# Docker Official Images mirror avoids Docker Hub 429 failures on shared CI runners.
FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# copy pom first so the dependency layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
# tests live outside code/ (repo-level test/ folder), so they are not part of the image build;
# run them with `mvn test` locally / in CI before pushing
RUN mvn -q package -Dmaven.test.skip=true

# ---- Stage 2: small runtime image (JRE only) ----
FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --no-create-home laundry
COPY --from=build /app/target/*.jar app.jar
USER laundry

EXPOSE 8080
# MaxRAMPercentage keeps the JVM inside small container limits (e.g. free-tier hosts)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
