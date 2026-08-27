FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system adp && adduser --system --ingroup adp adp
COPY --from=build /workspace/target/adp-be-0.0.1-SNAPSHOT.jar app.jar
USER adp

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
