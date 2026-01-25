FROM eclipse-temurin:17-jre

WORKDIR /app
COPY service/product/build/libs/*.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
