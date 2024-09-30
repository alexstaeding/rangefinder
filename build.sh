#!/bin/bash
docker build -t images.sourcegrade.org/rangefinder/"$1":latest -f "$1".Dockerfile .
