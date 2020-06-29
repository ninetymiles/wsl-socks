FROM openjdk:8-jre-alpine
ARG WSL_VER
ADD build/distributions/wsl-socks-${WSL_VER}.tar /usr/local/
VOLUME ["/etc/wsl-socks"]
ENTRYPOINT ["/usr/local/wsl-socks-${WSL_VER}/bin/wsl-socks","-c","/etc/wsl-socks/config.properties"]
