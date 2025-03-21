FROM openjdk:8-jre-slim

ARG VER
LABEL version="${VER}"

ENV JAVA_OPTS="-Xms256m -Xmx1024m"

ADD build/distributions/wsl-socks-${VER}.tar /usr/local/
RUN ln -nfs /usr/local/wsl-socks-${VER} /usr/local/wsl-socks
ENTRYPOINT ["/usr/local/wsl-socks/bin/wsl-socks"]
