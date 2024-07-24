#!/bin/bash
docker build -t offline-search-"$1":latest -f "$1".Dockerfile .
