package io.github.alexstaeding

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.log4s.Logger

import scala.collection.mutable

object Discovery extends IOApp {

  private val logger: Logger = org.log4s.getLogger(Discovery.getClass)
  private val clients = mutable.Set[String]()

  private val service = HttpRoutes
    .of[IO] {
      case req @ POST -> Root / "hello" =>
        val clientInfo = req.params.get("client-info")
        if (clientInfo.isEmpty) {
          BadRequest("Missing client-info\n")
        } else {
          logger.info(s"Added client ${clientInfo.get}")
          clients.add(clientInfo.get)
          Ok(s"Hello, ${clientInfo.get}\n")
        }
      case req @ GET -> Root / "get" =>
        val clientInfo = req.params.get("client-info")
        if (clientInfo.isEmpty) {
          BadRequest("Missing client-info\n")
        } else {
          Ok(clients.filter(_ != clientInfo.get).mkString(", "))
        }
    }
    .orNotFound

  def run(args: List[String]): IO[ExitCode] = {
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(service)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
