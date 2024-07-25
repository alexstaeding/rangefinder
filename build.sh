#!/bin/bash
docker build -t images.sourcegrade.org/offline-search/"$1":latest -f "$1".Dockerfile .
