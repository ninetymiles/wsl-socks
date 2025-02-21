#!/bin/sh
# Build from java and update docker image wsl-socks:latest

PWD=$(PWD)
VER=$(gradle properties | grep version: | awk '{print $2}')
echo "Build wsl-socks ${VER}..."

gradle distTar

docker build \
	--build-arg VER="${VER}" \
	-t wsl-socks \
	.
