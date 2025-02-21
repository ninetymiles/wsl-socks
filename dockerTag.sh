#!/bin/sh
# Tag docker image wsl-socks:latest as version in labels

VER=$(docker inspect  --format '{{.Config.Labels.version}}' wsl-socks)

docker tag wsl-socks wsl-socks:${VER}
