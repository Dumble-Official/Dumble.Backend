# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom first so dependency download is cached when source changes only
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build jar
COPY src src
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/DumbleSubscription-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8182

ENTRYPOINT ["java", "-jar", "app.jar"]
