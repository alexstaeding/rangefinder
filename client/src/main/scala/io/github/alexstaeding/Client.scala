package io.github.alexstaeding

import cats.effect.*
import com.comcast.ip4s.*
import io.github.alexstaeding.Client.discoveryUri
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.log4s.Logger

import scala.util.Random

object Client extends IOApp {

  val logger: Logger = org.log4s.getLogger(Client.getClass)

  private val selfPort: Int = 9000 + Random.nextInt(1000)

  val discoveryUri: Uri = uri"http://localhost:8080/hello"
    .withQueryParam("client-info", s"http://localhost:$selfPort")

  private def createService(client: Client[IO]) = HttpRoutes.of[IO] {
    case POST -> Root / "hello" =>
      Ok("h")
  }.orNotFound

  override def run(args: List[String]): IO[ExitCode] = {
    EmberClientBuilder.default[IO].build.use { client =>
      client
        .expect[String](Request[IO](
          method = Method.POST,
          uri = discoveryUri,
        ))
        .flatMap(IO.println)
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(Port.fromInt(selfPort).get)
        .withHttpApp(createService(client))
        .build
        .use(_ => IO.never)
        .as(ExitCode.Success)
    }.as(ExitCode.Success)
  }
}
