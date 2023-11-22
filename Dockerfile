FROM openjdk:21

# permanent files
COPY storage /storage

# Define the working directory in the container
WORKDIR /app

# Copy your application's JAR file into the container
COPY /bin/labbook_connect.jar /app/labbook_connect.jar

# Command to launch application on container startup
CMD ["java", "-jar", "labbook_connect.jar"]

# CMD ["supervisord", \
#     "-c", "/home/supervisor/etc/supervisor.conf", \
#     "--pidfile", "/home/supervisor/tmp/supervisor.pid", \
#     "--user", "root"]
