package io.github.alexstaeding

import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.LogManager

import scala.collection.mutable

object Discovery {

  private val logger: Logger = LogManager.getLogger
  private val clients = mutable.Set[String]()

//  private val service = HttpRoutes
//    .of[IO] {
//      case req @ POST -> Root / "hello" =>
//        val clientInfo = req.params.get("client-info")
//        if (clientInfo.isEmpty) {
//          BadRequest("Missing client-info\n")
//        } else {
//          logger.info(s"Added client ${clientInfo.get}")
//          clients.add(clientInfo.get)
//          Ok(s"Hello, ${clientInfo.get}\n")
//        }
//      case req @ GET -> Root / "get" =>
//        val clientInfo = req.params.get("client-info")
//        if (clientInfo.isEmpty) {
//          BadRequest("Missing client-info\n")
//        } else {
//          Ok(clients.filter(_ != clientInfo.get).mkString(", "))
//        }
//    }
//    .orNotFound
//
//  def run(args: List[String]): IO[ExitCode] = {
//    EmberServerBuilder
//      .default[IO]
//      .withHost(ipv4"0.0.0.0")
//      .withPort(port"8080")
//      .withHttpApp(service)
//      .build
//      .use(_ => IO.never)
//      .as(ExitCode.Success)
//  }
}
