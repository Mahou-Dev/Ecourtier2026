# Dev & Hot-Reload Spring Boot Environment
FROM maven:3.9.9-eclipse-temurin-21-alpine

WORKDIR /app

# Pre-fetch Maven dependencies to optimize Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

EXPOSE 8080

# Start Spring Boot application with live reloading (DevTools enabled)
CMD ["mvn", "spring-boot:run", "-Dspring-boot.run.profiles=dev"]
