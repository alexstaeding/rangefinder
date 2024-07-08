FROM sbtscala/scala-sbt:eclipse-temurin-alpine-21.0.2_13_1.10.0_3.4.2 as builder
WORKDIR /app
COPY . /app

RUN sbt update
RUN sbt headless/assembly

FROM openjdk:21-bookworm
COPY --from=builder /app/headless/target/scala-3.4.2/headless-assembly-0.1.0-SNAPSHOT.jar /app/headless.jar
ENTRYPOINT ["java", "-jar", "/app/headless.jar"]
CMD ["--help"]
