# Builder image with jdk
FROM maven:3.8.3-eclipse-temurin-11 AS build

WORKDIR /build

COPY . .

RUN mvn -B --quiet package -DskipTests=true \
    && mkdir /artifact \
    && mv /build/target/jack-of-all-trades-*.jar /artifact/joal.jar

# Actual joal image with jre only
FROM eclipse-temurin:11.0.13_8-jre

LABEL name="Adnan Ahmad"
LABEL maintainer="viperadnan@gmail.com"
LABEL url="https://github.com/viperadnan-git/joal"
LABEL vcs-url="https://github.com/viperadnan-git/joal"

WORKDIR /joal/

COPY --from=build /artifact/joal.jar /joal/joal.jar
COPY ./resources /data

# Use a shell form entrypoint to allow environment variable expansion at runtime
ENTRYPOINT ["/bin/sh", "-c"]

CMD ["java -jar /joal/joal.jar --joal-conf=/data --spring.main.web-environment=true --server.port=${PORT} --joal.ui.path.prefix=${JOAL_UI_PATH_PREFIX} --joal.ui.secret-token=${JOAL_UI_SECRET_TOKEN}"]
