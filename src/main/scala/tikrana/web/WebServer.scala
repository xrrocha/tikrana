package tikrana.web

import tikrana.util.Fault
import tikrana.util.Resources.*
import tikrana.util.Utils.*

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.logging.Level.*
import java.util.logging.{Level, Logger}

import scala.util.Try

protected val logger: Logger = Logger.getLogger("tikrana.web.WebServer")

enum Protocol:
  case HTTP, HTTPS
  def scheme: String = toString.toLowerCase
end Protocol

object WebServer:
end WebServer

case class WebServer(config: WebServerConfig):
  private val rootHandler = RootHandler(config)
  private var webServer = createServer(rootHandler)

  def start(): Try[WebServer] =
    webServer
      .peek(_.start())
      .map(_ => this)

  def stop(): Try[WebServer] =
    webServer.peek(_.stop(config.stopDelay))
    webServer = createServer(rootHandler)
    webServer.map(_ => this)

  private def createServer(
      rootHandler: RootHandler
  ): Try[HttpServer] =
    Try:
      HttpServer.create(InetSocketAddress(config.address, config.port), 0)
    .peek: server =>
      // TODO Use config-provided executor
      server.setExecutor(null)
      server.createContext("/", rootHandler)
      logger.fine(s"Listening on ${config.uri}")
    .mapFailure: t =>
      Fault(s"Error creating web webServer", t)
        .logAsWarning(logger)
end WebServer

import java.io.File

// TODO Add smart constructor to config companion object
// TODO Configure executor for WebServer (w/virtual threads)
case class WebServerConfig(
    protocol: Protocol = Protocol.HTTP,
    address: NetAddress,
    port: NetPort,
    stopDelay: Int = 0,
    baseDirectory: Directory = File(System.getProperty("user.dir")),
    basePackage: Path = "",
    mimeTypes: Map[Extension, MimeType] = Map.empty
):

  lazy val uri = s"${protocol.scheme}://$address:$port"
end WebServerConfig
