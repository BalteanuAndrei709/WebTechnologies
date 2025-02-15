# Use an OpenJDK 8 runtime base image (Blazegraph is often run on Java 8)
FROM openjdk:8-jre-alpine

# Set the working directory inside the container
WORKDIR /opt

# Copy the downloaded Blazegraph jar from the build context into the container
COPY blazegraph.jar blazegraph.jar

# Expose the default Blazegraph port
EXPOSE 9999

# Run Blazegraph with a server mode and allocate memory (adjust if needed)
CMD ["java", "-server", "-Xmx4g", "-jar", "blazegraph.jar"]
