FROM openjdk:21

# permanent files
COPY storage /storage

# Define the working directory in the container
WORKDIR /app

# Create the logs directory before running the application
RUN mkdir -p /app/logs

# Copy your application's JAR file into the container
COPY /bin/labbook_connect.jar /app/labbook_connect.jar

# Command to launch application on container startup
CMD ["sh", "-c", "java -jar labbook_connect.jar > logs/labbook_connect.log 2>&1"]