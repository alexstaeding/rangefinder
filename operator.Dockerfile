FROM docker.io/sbtscala/scala-sbt:eclipse-temurin-alpine-21.0.2_13_1.10.2_3.5.1 AS builder
WORKDIR /src
COPY . /src

RUN sbt update
RUN sbt operator/assembly

FROM docker.io/openjdk:21-bookworm
COPY --from=builder /src/app/operator/target/scala-3.5.1/app-operator-assembly-0.1.0-SNAPSHOT.jar /src/operator.jar
ENTRYPOINT ["java", "-jar", "/src/operator.jar"]
