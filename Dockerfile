FROM eclipse-temurin:21

# Define the working directory in the container
WORKDIR /app

# Create the logs directory before running the application
RUN mkdir -p /app/logs

# Copy your application's JAR file into the container
COPY /bin/labbook_connect.jar /app/labbook_connect.jar

# Command to launch application on container startup
# Java needs the timezone name, which podman does not provide, so it is looked up
CMD ["sh", "-c", "TZ=${TZ:-$(find /usr/share/zoneinfo -type f -not -path '*/posix/*' -not -path '*/right/*' -not -path '*/Etc/*' -exec cmp -s /etc/localtime {} \\; -print | head -n1 | sed 's|/usr/share/zoneinfo/||')}; export TZ; java -jar labbook_connect.jar"]