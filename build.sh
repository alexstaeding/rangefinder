#!/bin/bash
#docker build -t images.sourcegrade.org/rangefinder/"$1":latest -f "$1".Dockerfile .

# run 'docker buildx create --use' first
docker buildx build --platform linux/amd64,linux/arm64 --push -t images.sourcegrade.org/rangefinder/"$1":latest -f "$1".Dockerfile .
