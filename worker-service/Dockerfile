FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/worker-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083

# Install basic CI/CD tools used by the worker for stream gobblers
RUN apt-get update && apt-get install -y git maven curl && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["java", "-jar", "app.jar"]
