FROM openjdk:17-jdk-slim

WORKDIR /cloud-app

# Copy the jar file in the container
COPY ./build/libs/cloud-app-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]