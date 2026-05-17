# ---- Stage 1: Build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy maven files for dependency caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (chmod first — the +x bit on mvnw is not preserved
# when the build context comes from a Windows working tree).
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build jar
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar from the build stage (glob — version-agnostic)
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8181

ENTRYPOINT ["java", "-jar", "app.jar"]
