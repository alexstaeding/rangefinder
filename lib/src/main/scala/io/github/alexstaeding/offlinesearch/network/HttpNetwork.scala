package io.github.alexstaeding.offlinesearch.network

import com.sun.net.httpserver.HttpServer

import java.net.http.{HttpClient, HttpRequest}
import java.net.{InetAddress, InetSocketAddress, URI}
import scala.concurrent.Future

class HttpNetwork(bindAddress: InetSocketAddress) extends Network {

  val client = HttpClient.newHttpClient()
  val server = HttpServer.create()

  {
    server.bind(bindAddress, 0)
  }

  override def send(to: InetAddress, data: String): Future[Option[String]] = {
  }
}
