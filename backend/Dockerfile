# ---- Stage 1: Build ----
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

COPY src src

RUN ./mvnw clean package -DskipTests -B

# Extract application (Spring Boot 4 format)
RUN java -Djarmode=tools -jar target/*.jar extract --destination extracted

# ---- Stage 2: Production runner ----
FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app

RUN addgroup --system --gid 1001 spring && \
    adduser --system --uid 1001 spring

COPY --from=builder --chown=spring:spring /build/extracted/ ./

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "b2b-strawman-backend-0.0.1-SNAPSHOT.jar"]
