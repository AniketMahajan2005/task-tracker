# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /build/target/task-tracker-1.0.0.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
