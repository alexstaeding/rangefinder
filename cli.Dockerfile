FROM docker.io/sbtscala/scala-sbt:eclipse-temurin-alpine-21.0.2_13_1.10.2_3.5.1 AS builder
WORKDIR /src
COPY . /src

RUN sbt update
RUN sbt cli/assembly

FROM docker.io/openjdk:21-bookworm

COPY --from=builder /src/app/cli/target/scala-3.5.1/app-cli-assembly-0.1.0-SNAPSHOT.jar /src/cli.jar

ENTRYPOINT ["/bin/bash", "-c", "while true; do echo 'Waiting for 15 seconds...'; sleep 15; done"]
