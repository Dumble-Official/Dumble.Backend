# ---- Stage 1: Build ----
# Maven is baked into the base image. We deliberately do NOT use the ./mvnw
# wrapper here: the wrapper's first step is a busybox `wget` of the Maven
# distribution zip from repo.maven.apache.org, whose TLS handshake is fragile
# behind some AV/proxy setups and was the failing step on contributor machines.
# The maven: base sidesteps that bootstrap entirely (same pattern as
# payment/wallet/subscription).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom first so the dependency layer is cached unless pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src src
RUN mvn package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
