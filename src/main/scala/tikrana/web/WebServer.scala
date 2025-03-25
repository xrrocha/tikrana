package tikrana.web

import com.sun.net.httpserver.*
import java.net.InetSocketAddress
import java.util.logging.Logger
import tikrana.util.Fault
import tikrana.util.Utils.*

object WebServer:
  val logger = Logger.getLogger("tikrana.web.WebServer")

case class WebServer(address: NetAddress, port: Port) extends HttpHandler:
  import WebServer.*
  private var server = createServer()

  def start(): Either[Fault, WebServer] =
    server
      .peek(_.start())
      .map(_ => this)

  def stop(): Either[Fault, WebServer] =
    server.peek(_.stop(0))
    server = createServer()
    server.map(_ => this)

  override def handle(exchange: HttpExchange): Unit =
    val responseBody =
      "<h1>In the works...</h1>".getBytes()
    exchange.sendResponseHeaders(200, responseBody.length)
    exchange
      .getResponseBody()
      .use: out =>
        out.write(responseBody)
        out.flush()
      .mapLeft: t =>
        Fault(s"I/O error handling exchange ${exchange.getRequestURI()}", t)
          .logWarning(logger, WITH_STACK_TRACE)

  private def createServer(): Either[Fault, HttpServer] =
    catching:
      HttpServer.create(InetSocketAddress(address, port), 0)
    .peek: server =>
      // TODO Use sensible executor for WebServer (w/virtual threads)
      server.setExecutor(null)
      server.createContext("/", this)
    .mapLeft: t =>
      Fault(s"Error creating web server", t)
        .logFiner(logger, WITH_NO_STACK_TRACE)
