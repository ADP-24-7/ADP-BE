FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace

COPY settings.gradle build.gradle ./
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21.0.12_8-jre-ubi10-minimal@sha256:daa3502c4017ec00661fd902dde77738e7136626d61d4e243068368163ac971a
WORKDIR /app

COPY --from=build --chown=10001:0 /workspace/build/libs/adp-be.jar app.jar
USER 10001

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
