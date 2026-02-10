FROM eclipse-temurin:17-jre

WORKDIR /app
COPY service/cart/build/libs/*.jar app.jar

COPY opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
