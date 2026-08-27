FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace

COPY settings.gradle build.gradle ./
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system adp && adduser --system --ingroup adp adp
COPY --from=build /workspace/build/libs/adp-be.jar app.jar
USER adp

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
