package tikrana.web

import tikrana.util.Utils.*
import com.sun.net.httpserver.*
import java.net.InetSocketAddress

case class WebServer(address: NetAddress, port: Port) extends HttpHandler:

  private var server = createServer()

  def start() =
    server.start()

  def stop() =
    server.stop(0)
    server = createServer()

  override def handle(exchange: HttpExchange): Unit =
    val responseBody =
      "<h1>In the works...</h1>".getBytes()
    exchange.sendResponseHeaders(200, responseBody.length)
    exchange.getResponseBody().use: out =>
      out.write(responseBody)
      out.flush()

  // TODO Catch i/o and network-binding exceptions
  private def createServer() =
    HttpServer
      .create(InetSocketAddress(address, port), 0)
      .also: s =>
        s.setExecutor(null)
        s.createContext("/", this)
