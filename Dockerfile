FROM openjdk:8-jre-slim

ARG VER
LABEL name="wsl-socks" version="${VER}"

ADD build/distributions/wsl-socks-${VER}.tar /usr/local/
RUN ln -nfs /usr/local/wsl-socks-${VER} /usr/local/wsl-socks
ENTRYPOINT ["/usr/local/wsl-socks/bin/wsl-socks"]
