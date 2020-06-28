FROM openjdk:8-jre-alpine
ARG VER
ADD build/distributions/wsl-socks-1.4-SNAPSHOT.tar /usr/local/
VOLUME ["/etc/wsl-socks"]
ENTRYPOINT ["/usr/local/wsl-socks-1.4-SNAPSHOT/bin/wsl-socks","-c","/etc/wsl-socks/config.properties"]
