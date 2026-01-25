FROM eclipse-temurin:17-jre

WORKDIR /app
COPY service/payment/build/libs/*.jar app.jar


EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
