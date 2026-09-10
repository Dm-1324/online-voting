# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Cache Maven dependencies before copying application source.
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q clean package

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Run as a non-root user.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/target/voting-app.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser

# The application uses DATABASE_URL, DATABASE_USERNAME and DATABASE_PASSWORD
# at runtime, so the same image works with Neon locally and on EC2.
ENV PORT=8092
EXPOSE 8092

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8092/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
