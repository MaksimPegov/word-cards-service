# Use an official OpenJDK base image
FROM openjdk:17-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the application JAR file to the container
COPY target/api-gateway-1.0-SNAPSHOT.jar app.jar

# Expose the port that your microservice listens on
EXPOSE 9001

# Specify the command to run when the container starts
CMD ["java", "-jar", "app.jar"]
