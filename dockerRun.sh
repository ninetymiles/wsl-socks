#!/bin/sh
# Tag docker image wsl-socks:latest as version in labels

VER=$(docker inspect  --format '{{.Config.Labels.version}}' wsl-socks)

docker tag wsl-socks wsl-socks:${VER}
docker run -d \
	-p 1080:1080 \
	--restart always \
	--name wsl-socks \
	--env JAVA_OPTS="-Xms64m -Xmx1024m" \
	wsl-socks
