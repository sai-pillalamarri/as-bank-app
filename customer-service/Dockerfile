FROM maven:3.9-eclipse-temurin-21@sha256:8f6ac126f7810bb5549c4cd122d2bf0e9cda5bdeb0838aa928f09e779fd8bef8 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn --batch-mode -DskipTests dependency:go-offline

COPY src ./src

RUN mvn --batch-mode -DskipTests package \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools \
        -jar application.jar \
        extract \
        --layers \
        --destination extracted

FROM eclipse-temurin:21-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037

RUN groupadd --system asbank \
    && useradd --system --gid asbank --uid 10001 asbank

WORKDIR /app

COPY --from=build --chown=asbank:asbank /workspace/extracted/dependencies/ ./
COPY --from=build --chown=asbank:asbank /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=asbank:asbank /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=asbank:asbank /workspace/extracted/application/ ./

USER asbank

EXPOSE 8080 8081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]