FROM sbtscala/scala-sbt:eclipse-temurin-alpine-21.0.2_13_1.10.0_3.4.2 AS builder
WORKDIR /src
COPY . /src

RUN sbt update
RUN sbt cli/assembly

FROM openjdk:21-bookworm

COPY --from=builder /src/app/cli/target/scala-3.4.2/app-cli-assembly-0.1.0-SNAPSHOT.jar /src/cli.jar

ENTRYPOINT ["/bin/bash", "-c", "while true; do echo 'Waiting for 15 seconds...'; sleep 15; done"]
