# Use an OpenJDK 17 slim image
FROM openjdk:17-jdk-slim

# Copy the built JAR file from the target directory into the container.
# Make sure your application is built (e.g. via Maven or Gradle) so that target/Gait-0.0.1-SNAPSHOT.jar exists.
COPY target/Gait-0.0.1-SNAPSHOT.jar app.jar

# Set the entrypoint to run the JAR file.
ENTRYPOINT ["java", "-jar", "/app.jar"]
