# ---- Stage 1: Build ----
# Maven is baked into the base image (same pattern as auth/gym/subscription):
# we deliberately avoid the ./mvnw wrapper here so there is no busybox wget of
# the Maven distribution, which is the step that fails behind some AV/proxies.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src src
RUN mvn package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8186

ENTRYPOINT ["java", "-jar", "app.jar"]
