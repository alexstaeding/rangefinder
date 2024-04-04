package io.github.alexstaeding.offlinesearch.network

import com.sun.net.httpserver.HttpServer

import java.net.http.HttpClient
import java.net.InetSocketAddress

class HttpNetwork(bindAddress: InetSocketAddress) extends Network {

  val client = HttpClient.newHttpClient()
  val server = HttpServer.create()

  {
    server.bind(bindAddress, 0)
  }

  override def send(peer: Int): Unit = {
    
  }
}
