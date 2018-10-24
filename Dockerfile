FROM ubuntu:bionic
RUN apt update && apt install -y \
    openjdk-11-jdk-headless \
    ca-certificates-java \
    ant \
    unzip \
    python3-jinja2 \
    python3-click \
    python3-pip \
    && echo "OK"

WORKDIR /src/apacheds
COPY ["build/", "./build/"]
COPY ["dist/", "./dist/"]
COPY ["src/", "./src/"]
COPY ["*.xml", "version", "LICENSE", "INSTALL.md", "./"]
RUN ant dist

RUN mv /src/apacheds/target/apacheds  /opt/apacheds
WORKDIR /opt/apacheds

COPY ["scripts/docker/*.py", "/root/"]
COPY ["scripts/docker/templates", "/root/templates"]
COPY ["scripts/docker/entrypoint.sh", "/root/"]
ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8
ENTRYPOINT ["bash", "/root/entrypoint.sh"]

ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
VOLUME /opt/apacheds/instances
EXPOSE 10389
EXPOSE 10636


