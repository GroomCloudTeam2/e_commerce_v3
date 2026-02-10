FROM eclipse-temurin:17-jre

WORKDIR /app
COPY build/libs/*.jar app.jar

ARG OTEL_AGENT_VER=2.20.1
RUN mkdir -p /otel && \
    curl -L -o /otel/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VER}/opentelemetry-javaagent.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
