FROM openjdk:8-jre-alpine
ARG WSL_VER
ADD build/distributions/wsl-socks-${WSL_VER}.tar /usr/local/
RUN ln -nfs /usr/local/wsl-socks-${WSL_VER} /usr/local/wsl-socks
ENTRYPOINT ["/usr/local/wsl-socks/bin/wsl-socks"]
