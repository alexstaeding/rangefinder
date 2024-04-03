package io.github.alexstaeding.offlinesearch.network

import com.sun.net.httpserver.HttpServer

import java.net.http.HttpClient

class HttpNetwork extends Network {

  val client = HttpClient.newHttpClient()
  val server = HttpServer.create()

  override def send(peer: Int): Unit = {
  }


}
