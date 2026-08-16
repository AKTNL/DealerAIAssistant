# syntax=docker/dockerfile:1.7

FROM node:24.6-alpine AS frontend-build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN --mount=type=cache,target=/root/.npm \
    cd frontend && npm ci --no-audit --no-fund
COPY frontend ./frontend
COPY backend/src/main/resources/static ./backend/src/main/resources/static
RUN cd frontend && npm run build

FROM maven:3.9.11-eclipse-temurin-21-alpine AS backend-build
WORKDIR /workspace
COPY backend/pom.xml ./backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f backend/pom.xml "-Dfrontend.skip=true" dependency:go-offline
COPY backend ./backend
COPY --from=frontend-build /workspace/backend/src/main/resources/static ./backend/src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f backend/pom.xml "-Dfrontend.skip=true" "-DskipTests" package

FROM eclipse-temurin:21.0.8_9-jre-alpine AS runtime
ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="Dealer AI Analysis Assistant" \
      org.opencontainers.image.version="${APP_VERSION}"
RUN addgroup -S -g 10001 agentpoc \
    && adduser -S -D -H -u 10001 -G agentpoc agentpoc
WORKDIR /app
COPY --from=backend-build --chown=10001:10001 \
    /workspace/backend/target/agent-poc-backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.io.tmpdir=/tmp", "-jar", "/app/app.jar"]
