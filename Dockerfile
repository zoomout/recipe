# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline

COPY config config
COPY src src
RUN ./mvnw -q package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app app
USER app
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
