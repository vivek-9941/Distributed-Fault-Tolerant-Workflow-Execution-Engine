FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/orchestrator-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
