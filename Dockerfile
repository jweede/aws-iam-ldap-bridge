FROM ubuntu:bionic
RUN apt update && apt install -y \
    openjdk-11-jdk-headless \
    ca-certificates-java \
    ant \
    unzip \
    && echo "OK"

WORKDIR /src/apacheds
COPY ["build/", "./build/"]
COPY ["dist/", "./dist/"]
COPY ["src/", "./src/"]
COPY ["*.xml", "version", "LICENSE", "INSTALL.md", "./"]
RUN ant dist

RUN mv /src/apacheds/target/apacheds  /opt/apacheds
WORKDIR /opt/apacheds

COPY ["scripts/docker/entrypoint.sh", "/root/"]
ENTRYPOINT ["bash", "/root/entrypoint.sh"]

ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
VOLUME /opt/apacheds/instances
EXPOSE 10389
EXPOSE 10636


